package uu.game.main.dao.mongo;

import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import uu.app.objectstore.annotations.ObjectStoreDao;
import uu.app.objectstore.mongodb.dao.UuObjectMongoDao;
import uu.game.main.abl.entity.Score;
import uu.game.main.dao.ScoreDao;

@ObjectStoreDao(entity = Score.class, store = "primary")
public class ScoreMongoDao extends UuObjectMongoDao<Score> implements ScoreDao {

  protected static final String ATTR_UU_IDENTITY = "uuIdentity";
  protected static final String ATTR_SCORE = "score";


  public void createSchema() {
   super.createSchema();
   // One leaderboard row per identity per workspace. This was unique on awid alone (the shape copied
   // from GameMainMongoDao, where a single document per workspace is in fact correct), which meant
   // only the first player ever stored in a workspace could exist - every other create failed on the
   // duplicate key. The unique key is also what makes the create race in ScoreAbl detectable instead
   // of producing two rows for the same player.
   // Note: createSchema only creates indexes, it does not drop the old one, so an already deployed
   // workspace needs the awid-only unique index removed by hand.
   createIndex(new Index().on(ATTR_AWID, Direction.ASC).on(ATTR_UU_IDENTITY, Direction.ASC).unique());
   createIndex(new Index().on(ATTR_AWID, Direction.ASC).on(ATTR_SCORE, Direction.DESC));

 }

  @Override
  public Score getByUuIdentity(String awid, String uuIdentity) {
    Query query = awidQuery(awid).addCriteria(Criteria.where(ATTR_UU_IDENTITY).is(uuIdentity));
    return super.findOne(query);
  }
}
