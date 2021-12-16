package uu.game.main.dao.mongo;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Query;
import uu.app.datastore.domain.PageInfo;
import uu.app.datastore.domain.PagedResult;
import uu.app.datastore.exceptions.DatastoreRuntimeException;
import uu.app.objectstore.annotations.ObjectStoreDao;
import uu.app.objectstore.mongodb.dao.UuObjectMongoDao;
import uu.game.main.abl.entity.Calculate;
import uu.game.main.dao.CalculateDao;

@ObjectStoreDao(entity = Calculate.class, store = "primary")
public class CalculateMongoDao extends UuObjectMongoDao<Calculate> implements CalculateDao {

 public void createSchema() {
   super.createSchema();
   createIndex(new Index().on(ATTR_AWID, Direction.ASC).unique());
 }


  @Override
  public PagedResult<Calculate> list(String awid, PageInfo pageInfo) throws DatastoreRuntimeException {
    Query query = awidQuery(awid);
    query.with(new Sort(Direction.DESC, "key"));
    return find(query, pageInfo);
  }
}
