package uu.game.main.dao;

import uu.app.objectstore.dao.UuObjectDao;
import uu.game.main.abl.entity.GameMain;
import uu.game.main.abl.entity.Score;

public interface ScoreDao extends UuObjectDao<Score> {

  Score getByUuIdentity(String awid, String uuIdentity);

}
