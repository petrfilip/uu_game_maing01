package uu.game.main.abl;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import uu.app.datastore.domain.PageInfo;
import uu.app.datastore.domain.PagedResult;
import uu.app.datastore.exceptions.DatastoreConcurrencyRuntimeException;
import uu.app.datastore.exceptions.DatastoreRuntimeException;
import uu.app.validation.ValidationResult;
import uu.app.validation.Validator;
import uu.app.validation.utils.ValidationResultUtils;
import uu.app.workspace.api.dto.common.PageInfoDto;
import uu.game.main.abl.dto.Player;
import uu.game.main.abl.entity.Score;
import uu.game.main.api.dto.ScoreGetByUuIdentityDtoIn;
import uu.game.main.api.dto.ScoreGetDtoOut;
import uu.game.main.api.dto.ScoreListDtoIn;
import uu.game.main.api.dto.ScoreListDtoOut;
import uu.game.main.api.exceptions.ScoreAblRuntimeException;
import uu.game.main.api.exceptions.ScoreAblRuntimeException.Error;
import uu.game.main.dao.ScoreDao;
import uu.game.main.helper.EloUtils;

@Component
public final class ScoreAbl {

  /**
   * How many times the rating of a single player is stored before giving up. Bounded on purpose: this
   * runs at the end of a match inside the game loop, so it must fail rather than spin when a player is
   * genuinely hot (and every attempt costs a read plus a write).
   */
  public static final int MAX_PERSIST_ATTEMPTS = 3;

  @Inject
  private Validator validator;
  @Inject
  private ScoreDao scoreDao;
  @Inject
  private ModelMapper modelMapper;

  public ScoreListDtoOut list(String awid, ScoreListDtoIn dtoIn) {
    // HDS 1
    ValidationResult validationResult = validator.validate(dtoIn);
    if (!validationResult.isValid()) {
      // A1
      throw new ScoreAblRuntimeException(Error.INVALID_DTO_IN, ValidationResultUtils.validationResultToAppErrorMap(validationResult));
    }

    // The dtoIn constructor pre-fills the paging, but a client sending "pageInfo": null (or only one of
    // the two attributes) overwrites that again, and the schema makes neither of them required - so
    // read it defensively instead of dereferencing straight into the DAO call.
    PageInfoDto pageInfo = dtoIn.getPageInfo();
    Integer pageIndex = pageInfo == null ? null : pageInfo.getPageIndex();
    Integer pageSize = pageInfo == null ? null : pageInfo.getPageSize();
    PagedResult<Score> scoreList = scoreDao.list(awid, new PageInfo(
      pageIndex == null ? PageInfo.DEFAULT_PAGE_INDEX : pageIndex,
      pageSize == null ? PageInfo.DEFAULT_PAGE_SIZE : pageSize));

    return modelMapper.map(scoreList, ScoreListDtoOut.class);
  }

  /**
   * Returns the leaderboard entry of a single identity.
   *
   * <p>Takes the dtoIn rather than a bare String: the validator resolves the schema from the
   * {@link uu.app.validation.ValidationType} annotation on the value's class, so handing it a String
   * only ever threw {@code ValidatorRuntimeException} (validation type not defined) and this uuCmd
   * could not answer at all.</p>
   */
  public ScoreGetDtoOut getByUuIdentity(String awid, ScoreGetByUuIdentityDtoIn dtoIn) {
    // HDS 1
    ValidationResult validationResult = validator.validate(dtoIn);
    if (!validationResult.isValid()) {
      // A1
      throw new ScoreAblRuntimeException(Error.INVALID_DTO_IN, ValidationResultUtils.validationResultToAppErrorMap(validationResult));
    }

    Score score = scoreDao.getByUuIdentity(awid, dtoIn.getUuIdentity());

    return new ScoreGetDtoOut(score);
  }

  /**
   * Recalculates the Elo rating of every player of a finished match and stores it.
   *
   * <p>What this guarantees: every player's rating change is applied on top of whatever is in the
   * datastore at the moment of the write. Each write goes through the optimistic lock on
   * {@code sys.rev}, and when the lock reports that somebody else got there first, the player's record
   * is read again and the <em>change</em> - not the absolute rating computed from the stale read - is
   * re-applied. So a match finishing at the same time with a player in common can no longer have its
   * result silently overwritten, and a retry can never count the same change twice.</p>
   *
   * <p>What this does <em>not</em> guarantee: the match is not atomic. There is no transaction
   * spanning the players, so if one player's record stays contended until the retries run out, the
   * players written before it keep their new rating and the exception propagates to the caller. The
   * exchange is also computed from one snapshot of everybody's rating, so two overlapping matches are
   * only zero-sum within themselves, not against each other. Both would need a transaction the
   * datastore does not offer here.</p>
   */
  public void calculateEloAndPersist(String awid, Collection<Player> players){
    // The outcome no longer depends on this order - EloUtils compares the match scores itself - but
    // keeping it makes the order of the datastore calls deterministic.
    List<Player> ordered = players.stream().sorted(Comparator.comparing(Player::getScore).reversed()).collect(Collectors.toList());

    List<Score> elos = new ArrayList<>(ordered.size());
    List<Integer> eloScoreList = new ArrayList<>(ordered.size());
    List<Integer> matchScoreList = new ArrayList<>(ordered.size());

    for (Player p : ordered) {
      Score score = scoreDao.getByUuIdentity(awid, p.getPlayerId());
      if(score == null){
        score = newScore(awid, p);
      }
      elos.add(score);
      eloScoreList.add(score.getScore());
      matchScoreList.add(p.getScore());
    }

    List<Integer> updatedElos = EloUtils.calculateEloForResultList(eloScoreList, matchScoreList);

    for (int i = 0; i < updatedElos.size(); i++) {
      persistRatingChange(awid, ordered.get(i), elos.get(i), updatedElos.get(i) - eloScoreList.get(i));
    }

  }

  /**
   * Stores one player's rating change, retrying a bounded number of times when the optimistic lock
   * reports a conflict.
   *
   * @param known the record this player's change was computed from, used for the first attempt
   * @param change rating to add, which is what gets re-applied to the re-read record after a conflict
   */
  private void persistRatingChange(String awid, Player player, Score known, int change) {
    if (change == 0 && !Strings.isNullOrEmpty(known.getId())) {
      // A draw between equally rated players moves nothing. Writing anyway would only bump the
      // revision and turn this player into a conflict for every match running in parallel.
      return;
    }

    Score score = known;
    DatastoreRuntimeException lastFailure = null;
    for (int attempt = 0; attempt < MAX_PERSIST_ATTEMPTS; attempt++) {
      if (score == null) {
        score = scoreDao.getByUuIdentity(awid, player.getPlayerId());
        if (score == null) {
          score = newScore(awid, player);
        }
      }
      try {
        // Add the change to the rating that was just read. Storing the absolute value computed from
        // the first read is exactly how the concurrent update got lost before.
        score.setScore(score.getScore() + change);
        if (Strings.isNullOrEmpty(score.getId())) {
          scoreDao.create(score);
        } else {
          scoreDao.update(score);
        }
        return;
      } catch (DatastoreConcurrencyRuntimeException e) {
        // Somebody rated this player between our read and our write - read again and re-apply.
        lastFailure = e;
      } catch (DatastoreRuntimeException e) {
        if (!Strings.isNullOrEmpty(score.getId())) {
          // An update that failed for any other reason is not something a retry fixes.
          throw e;
        }
        // A failed create is the same race one step earlier: the first record for this player was
        // most likely created by the other match (the uuIdentity index rejects the duplicate), so the
        // retry will find it and update it instead.
        lastFailure = e;
      }
      // Force a fresh read on the next attempt; the object we just touched is stale by definition.
      score = null;
    }
    throw lastFailure;
  }

  private static Score newScore(String awid, Player player) {
    Score score = new Score();
    score.setAwid(awid);
    score.setUuIdentity(player.getPlayerId());
    score.setPlayerName(player.getPlayerName());
    return score;
  }

}
