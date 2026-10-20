package uu.game.main.abl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Query;
import uu.app.datastore.concurrency.Lock;
import uu.app.datastore.domain.PageInfo;
import uu.app.datastore.domain.PagedResult;
import uu.app.datastore.exceptions.DatastoreConcurrencyRuntimeException;
import uu.app.datastore.exceptions.DatastoreIdentityRuntimeException;
import uu.app.validation.ErrorSet;
import uu.app.validation.ValidationErrorType;
import uu.app.validation.ValidationResult;
import uu.app.validation.ValidationType;
import uu.app.validation.Validator;
import uu.app.validation.ValidatorRuntimeException;
import uu.app.workspace.api.dto.common.PageInfoDto;
import uu.game.main.abl.dto.Player;
import uu.game.main.abl.entity.Score;
import uu.game.main.api.dto.ScoreGetByUuIdentityDtoIn;
import uu.game.main.api.dto.ScoreGetDtoOut;
import uu.game.main.api.dto.ScoreListDtoIn;
import uu.game.main.api.dto.ScoreListDtoOut;
import uu.game.main.api.exceptions.ScoreAblRuntimeException;
import uu.game.main.dao.ScoreDao;

/**
 * Drives {@link ScoreAbl} over a hand written datastore that behaves like the real one: it hands out
 * copies, keeps a revision per record and rejects a write whose revision moved on.
 */
public class ScoreAblTest {

  private static final String AWID = "awid-1";

  private final FakeScoreDao dao = new FakeScoreDao();

  @Test
  public void twoEquallyRatedPlayersWhoTieKeepTheirRating() throws Exception {
    dao.seed("alice", 1000);
    dao.seed("bob", 1000);

    abl(dao).calculateEloAndPersist(AWID, Arrays.asList(player("alice", 7), player("bob", 7)));

    assertEquals("a tie must not hand rating to whoever the sort put first", 1000, dao.rating("alice"));
    assertEquals(1000, dao.rating("bob"));
  }

  @Test
  public void aRevisionConflictIsRetriedAndTheChangeLandsOnTheValueReadAgain() throws Exception {
    dao.seed("alice", 1000);
    dao.seed("bob", 1000);
    // Another match finishes with alice in it and stores 1050 between our read and our write.
    dao.interfereWith("alice", 1, 1050);

    abl(dao).calculateEloAndPersist(AWID, Arrays.asList(player("alice", 10), player("bob", 3)));

    assertEquals("the win must be added to the rating stored by the other match", 1066, dao.rating("alice"));
    assertEquals(984, dao.rating("bob"));
  }

  @Test(timeout = 10000)
  public void retriesAreBoundedSoAContendedPlayerCannotSpinTheGameLoop() throws Exception {
    dao.seed("alice", 1000);
    dao.seed("bob", 1000);
    // Somebody keeps rating alice, every single attempt of ours loses the race.
    dao.interfereWith("alice", Integer.MAX_VALUE, 1050);

    try {
      abl(dao).calculateEloAndPersist(AWID, Arrays.asList(player("alice", 10), player("bob", 3)));
      fail("a permanently contended record must surface as an error, not as an endless loop");
    } catch (DatastoreConcurrencyRuntimeException expected) {
      assertEquals(ScoreAbl.MAX_PERSIST_ATTEMPTS, dao.updateAttempts("alice"));
    }
  }

  @Test(timeout = 10000)
  public void aConflictOnOnePlayerDoesNotReapplyTheChangesAlreadyWritten() throws Exception {
    dao.seed("alice", 1000);
    dao.seed("bob", 1200);
    dao.seed("cyril", 800);
    dao.interfereWith("cyril", Integer.MAX_VALUE, 900);

    try {
      abl(dao).calculateEloAndPersist(AWID,
        Arrays.asList(player("alice", 10), player("bob", 5), player("cyril", 1)));
      fail("the unresolvable conflict on cyril has to reach the caller");
    } catch (DatastoreConcurrencyRuntimeException expected) {
      // Retrying the whole match instead of the single contended player would count these twice.
      assertEquals(1032, dao.rating("alice"));
      assertEquals(1179, dao.rating("bob"));
      assertEquals(1, dao.updateAttempts("alice"));
      assertEquals(1, dao.updateAttempts("bob"));
    }
  }

  @Test
  public void aPlayerWithoutARecordYetIsCreatedWithTheRatingChangeApplied() throws Exception {
    dao.seed("bob", 1000);

    abl(dao).calculateEloAndPersist(AWID, Arrays.asList(player("alice", 10), player("bob", 3)));

    assertEquals(1016, dao.rating("alice"));
    assertEquals("name of alice", dao.stored("alice").getPlayerName());
    assertEquals(984, dao.rating("bob"));
  }

  @Test
  public void aMissingPageInfoFallsBackToTheDefaultPageInsteadOfFailing() throws Exception {
    ScoreListDtoIn dtoIn = new ScoreListDtoIn();
    dtoIn.setPageInfo(null);

    ScoreListDtoOut dtoOut = abl(dao).list(AWID, dtoIn);

    assertNotNull(dtoOut);
    assertEquals(PageInfo.DEFAULT_PAGE_INDEX, (int) dao.lastPageInfo().getPageIndex());
    assertEquals(PageInfo.DEFAULT_PAGE_SIZE, (int) dao.lastPageInfo().getPageSize());
  }

  @Test
  public void theRequestedPageReachesTheDatastore() throws Exception {
    dao.seed("alice", 1000);
    PageInfoDto pageInfo = new PageInfoDto();
    pageInfo.setPageIndex(3);
    pageInfo.setPageSize(10);
    ScoreListDtoIn dtoIn = new ScoreListDtoIn();
    dtoIn.setPageInfo(pageInfo);

    ScoreListDtoOut dtoOut = abl(dao).list(AWID, dtoIn);

    assertEquals(3, (int) dao.lastPageInfo().getPageIndex());
    assertEquals(10, (int) dao.lastPageInfo().getPageSize());
    assertEquals(1, (int) dtoOut.getPageInfo().getTotal());
  }

  @Test
  public void getByUuIdentityReadsTheIdentityCarriedByTheDtoIn() throws Exception {
    dao.seed("21-2222-1", 1234);
    ScoreGetByUuIdentityDtoIn dtoIn = new ScoreGetByUuIdentityDtoIn();
    dtoIn.setUuIdentity("21-2222-1");

    // The validator here refuses anything the real one could not resolve a schema for, which is what a
    // bare String was: this call only gets as far as the DAO because an annotated dtoIn is what reaches it.
    ScoreGetDtoOut dtoOut = abl(dao).getByUuIdentity(AWID, dtoIn);

    assertEquals(1234, (int) dtoOut.getScore().getScore());
  }

  @Test
  public void anInvalidDtoInNeverReachesTheDatastore() throws Exception {
    ScoreAbl scoreAbl = abl(dao, new SchemaAwareValidator(false));

    try {
      scoreAbl.getByUuIdentity(AWID, new ScoreGetByUuIdentityDtoIn());
      fail("an invalid dtoIn has to be reported as invalidDtoIn");
    } catch (ScoreAblRuntimeException expected) {
      assertEquals(0, dao.reads());
    }
  }

  private static ScoreAbl abl(ScoreDao scoreDao) throws Exception {
    return abl(scoreDao, new SchemaAwareValidator(true));
  }

  private static ScoreAbl abl(ScoreDao scoreDao, Validator validator) throws Exception {
    ModelMapper modelMapper = new ModelMapper();
    // Same configuration as the bean in SubAppConfiguration, so the mapping is tested as it runs.
    modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);

    ScoreAbl scoreAbl = new ScoreAbl();
    inject(scoreAbl, "scoreDao", scoreDao);
    inject(scoreAbl, "validator", validator);
    inject(scoreAbl, "modelMapper", modelMapper);
    return scoreAbl;
  }

  private static void inject(Object target, String field, Object value) throws Exception {
    Field declared = target.getClass().getDeclaredField(field);
    declared.setAccessible(true);
    declared.set(target, value);
  }

  private static Player player(String id, int matchScore) {
    Player player = new Player(id);
    player.setPlayerName("name of " + id);
    player.setScore(matchScore);
    return player;
  }

  /**
   * In-memory stand in for the Mongo DAO. It copies on the way in and on the way out - a real
   * datastore never hands back the object you are about to write - and enforces the optimistic lock on
   * {@code sys.rev}, so a conflict happens for the same reason it happens in production.
   */
  private static final class FakeScoreDao implements ScoreDao {

    private final Map<String, Score> stored = new HashMap<>();
    private final Map<String, Integer> updateAttempts = new HashMap<>();
    private final Map<String, Integer> interferenceLeft = new HashMap<>();
    private final Map<String, Integer> interferenceRating = new HashMap<>();
    private PageInfo lastPageInfo;
    private int reads;
    private int idSequence;

    void seed(String uuIdentity, int rating) {
      Score score = new Score();
      score.setId("score-" + (++idSequence));
      score.setAwid(AWID);
      score.setUuIdentity(uuIdentity);
      score.setPlayerName("name of " + uuIdentity);
      score.setScore(rating);
      stored.put(uuIdentity, score);
    }

    /** Lets {@code times} of the upcoming updates of this identity be gazumped by another writer. */
    void interfereWith(String uuIdentity, int times, int ratingStoredByTheOtherWriter) {
      interferenceLeft.put(uuIdentity, times);
      interferenceRating.put(uuIdentity, ratingStoredByTheOtherWriter);
    }

    Score stored(String uuIdentity) {
      return stored.get(uuIdentity);
    }

    int rating(String uuIdentity) {
      return stored.get(uuIdentity).getScore();
    }

    int updateAttempts(String uuIdentity) {
      Integer attempts = updateAttempts.get(uuIdentity);
      return attempts == null ? 0 : attempts;
    }

    int reads() {
      return reads;
    }

    PageInfo lastPageInfo() {
      return lastPageInfo;
    }

    @Override
    public Score getByUuIdentity(String awid, String uuIdentity) {
      reads++;
      return copy(stored.get(uuIdentity));
    }

    @Override
    public Score create(Score entity) {
      if (stored.containsKey(entity.getUuIdentity())) {
        throw new DatastoreIdentityRuntimeException(DatastoreIdentityRuntimeException.Error.NOT_UNIQUE_ID,
          "Duplicate leaderboard row for %s.", entity.getUuIdentity());
      }
      Score persisted = copy(entity);
      persisted.setId("score-" + (++idSequence));
      persisted.getSys().setRev(0);
      stored.put(entity.getUuIdentity(), persisted);
      return copy(persisted);
    }

    @Override
    public Score update(Score entity) {
      String uuIdentity = entity.getUuIdentity();
      updateAttempts.put(uuIdentity, updateAttempts(uuIdentity) + 1);

      Integer left = interferenceLeft.get(uuIdentity);
      if (left != null && left > 0) {
        // The other writer gets there first: it stores its own value and bumps the revision, which is
        // exactly what makes our write below fail the revision check.
        interferenceLeft.put(uuIdentity, left - 1);
        Score current = stored.get(uuIdentity);
        current.setScore(interferenceRating.get(uuIdentity));
        current.getSys().setRev(current.getSys().getRev() + 1);
      }

      Score current = stored.get(uuIdentity);
      if (current == null) {
        throw new DatastoreIdentityRuntimeException(DatastoreIdentityRuntimeException.Error.UNKNOWN_ID,
          "No leaderboard row for %s.", uuIdentity);
      }
      if (current.getSys().getRev() != entity.getSys().getRev()) {
        throw new DatastoreConcurrencyRuntimeException(DatastoreConcurrencyRuntimeException.Error.REVISION_MISMATCH,
          "Revision of %s moved from %s to %s.", uuIdentity, entity.getSys().getRev(), current.getSys().getRev());
      }
      Score persisted = copy(entity);
      persisted.getSys().setRev(current.getSys().getRev() + 1);
      stored.put(uuIdentity, persisted);
      return copy(persisted);
    }

    @Override
    public PagedResult<Score> list(String awid, PageInfo pageInfo) {
      lastPageInfo = pageInfo;
      List<Score> itemList = new ArrayList<>();
      for (Score score : stored.values()) {
        itemList.add(copy(score));
      }
      pageInfo.setTotal(itemList.size());
      return new PagedResult<>(itemList, pageInfo);
    }

    private static Score copy(Score source) {
      if (source == null) {
        return null;
      }
      Score copy = new Score();
      copy.setId(source.getId());
      copy.setAwid(source.getAwid());
      copy.setUuIdentity(source.getUuIdentity());
      copy.setPlayerName(source.getPlayerName());
      copy.setScore(source.getScore());
      copy.getSys().setRev(source.getSys().getRev());
      return copy;
    }

    // Nothing below is used by ScoreAbl - failing loudly beats quietly pretending to work.

    @Override
    public void createSchema() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Score get(String awid, String id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Score> create(List<Score> list) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getCount(String awid) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getCount(Query query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void delete(String awid, String id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void delete(Score entity) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Score> list(String awid, Pageable pageable) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Score update(Score entity, Lock lock) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void delete(String awid, String id, Lock lock) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void delete(Score entity, Lock lock) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Lock createLock(String lockCode) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Lock createLock(String lockCode, int expirationInSeconds) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Score lock(String awid, String id, Lock lock) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void unlock(String awid, String id, Lock lock) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void unlock(Score entity, Lock lock) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Score create(Score entity, Lock lock) {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Validator that resolves the schema the way {@code ValidatorImpl} does: from the
   * {@link ValidationType} annotation on the class of the validated value. Anything else is a
   * configuration error there, not a failed validation, so it is one here too.
   */
  private static final class SchemaAwareValidator implements Validator {

    private final boolean valid;

    SchemaAwareValidator(boolean valid) {
      this.valid = valid;
    }

    @Override
    public ValidationResult validate(Object value) {
      if (value == null || value.getClass().getAnnotation(ValidationType.class) == null) {
        throw new ValidatorRuntimeException(ValidatorRuntimeException.Error.VALIDATION_TYPE_NOT_DEFINED,
          "Object passed to validation (%s) does not have a @ValidationType specified.", value);
      }
      return new FixedValidationResult(valid);
    }

    @Override
    public ValidationResult validate(Object value, String schemaName) {
      return new FixedValidationResult(valid);
    }
  }

  private static final class FixedValidationResult implements ValidationResult {

    private final boolean valid;

    FixedValidationResult(boolean valid) {
      this.valid = valid;
    }

    @Override
    public boolean isValid() {
      return valid;
    }

    @Override
    public Map<String, ErrorSet> getValidationErrorMap() {
      return Collections.emptyMap();
    }

    @Override
    public Set<String> getInvalidAttributeKeys(ValidationErrorType errorFilter) {
      return Collections.emptySet();
    }

    @Override
    public ValidationResult getValidationResult(String path) {
      return this;
    }

    @Override
    public Set<String> getMissingKeys() {
      return Collections.emptySet();
    }

    @Override
    public Set<String> getUnsupportedKeys() {
      return Collections.emptySet();
    }

    @Override
    public Set<String> getInvalidValueKeys() {
      return Collections.emptySet();
    }

    @Override
    public Set<String> getInvalidTypeKeys() {
      return Collections.emptySet();
    }
  }
}
