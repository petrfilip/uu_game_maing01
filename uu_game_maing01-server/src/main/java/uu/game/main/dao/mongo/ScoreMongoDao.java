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
   createIndex(new Index().on(ATTR_AWID, Direction.ASC).unique());
   createIndex(new Index().on(ATTR_AWID, Direction.ASC).on(ATTR_UU_IDENTITY, Direction.ASC).on(ATTR_SCORE, Direction.DESC));

 }

  @Override
  public Score getByUuIdentity(String awid, String uuIdentity) {
    Query query = awidQuery(awid).addCriteria(Criteria.where(ATTR_UU_IDENTITY).is(uuIdentity));
    return super.findOne(query);
  }
}
