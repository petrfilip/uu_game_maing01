package uu.game.main.dao.mongo;

import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.index.Index;
import uu.app.objectstore.annotations.ObjectStoreDao;
import uu.app.objectstore.mongodb.dao.UuObjectMongoDao;
import uu.game.main.dao.GameMainDao;
import uu.game.main.abl.entity.GameMain;

@ObjectStoreDao(entity = GameMain.class, store = "primary")
public class GameMainMongoDao extends UuObjectMongoDao<GameMain> implements GameMainDao {

 public void createSchema() {
   super.createSchema();
   createIndex(new Index().on(ATTR_AWID, Direction.ASC).unique());
 }

}
