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
import uu.app.validation.ValidationResult;
import uu.app.validation.Validator;
import uu.app.validation.utils.ValidationResultUtils;
import uu.game.main.abl.dto.Player;
import uu.game.main.abl.entity.Score;
import uu.game.main.api.dto.ScoreGetDtoOut;
import uu.game.main.api.dto.ScoreListDtoIn;
import uu.game.main.api.dto.ScoreListDtoOut;
import uu.game.main.api.exceptions.ScoreAblRuntimeException;
import uu.game.main.api.exceptions.ScoreAblRuntimeException.Error;
import uu.game.main.dao.ScoreDao;
import uu.game.main.helper.EloUtils;

@Component
public final class ScoreAbl {

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

    PagedResult<Score> scoreList = scoreDao.list(awid, new PageInfo(dtoIn.getPageInfo().getPageIndex(), dtoIn.getPageInfo().getPageSize()));

    return modelMapper.map(scoreList, ScoreListDtoOut.class);
  }

  public ScoreGetDtoOut getByUuIdentity(String awid, String uuIdentity) {
    // HDS 1
    ValidationResult validationResult = validator.validate(uuIdentity);
    if (!validationResult.isValid()) {
      // A1
      throw new ScoreAblRuntimeException(Error.INVALID_DTO_IN, ValidationResultUtils.validationResultToAppErrorMap(validationResult));
    }

    Score score = scoreDao.getByUuIdentity(awid, uuIdentity);

    return new ScoreGetDtoOut(score);
  }

  public void calculateEloAndPersist(String awid, Collection<Player> players){
    List<Player> ordered = players.stream().sorted(Comparator.comparing(Player::getScore).reversed()).collect(Collectors.toList());

    List<Score> elos = new ArrayList<>(ordered.size());
    List<Integer> eloScoreList = new ArrayList<>(ordered.size());


    for (Player p : ordered) {
      Score score = scoreDao.getByUuIdentity(awid, p.getPlayerId());
      if(score == null){
        score = new Score();
        score.setAwid(awid);
        score.setUuIdentity(p.getPlayerId());
        score.setPlayerName(p.getPlayerName());
      }
      elos.add(score);
      eloScoreList.add(score.getScore());
    }

    List<Integer> updatedElos = EloUtils.calculateEloForResultList(eloScoreList);

    for (int i = 0; i < updatedElos.size(); i++) {
      Score scoreToPersist = elos.get(i);
      scoreToPersist.setScore(updatedElos.get(i));
      if(Strings.isNullOrEmpty(scoreToPersist.getId())){
        scoreDao.create(scoreToPersist);
      }else{
        scoreDao.update(scoreToPersist);
      }
    }

  }

}
