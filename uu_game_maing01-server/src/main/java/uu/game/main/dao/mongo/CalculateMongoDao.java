package uu.game.main.dao.mongo;

import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.index.Index;
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

}
