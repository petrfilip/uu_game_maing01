package uu.game.main.game.bulanci;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import uu.game.main.game.bulanci.ammo.AmmoDamagable;
import uu.game.main.game.bulanci.ammo.AmmoHitable;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;

public class Obstacle implements AmmoDamagable, AmmoHitable {

  private ObstacleTypeEnum type;

  List<GameRectangle> walls = new ArrayList<>();

  public Obstacle(ObstacleTypeEnum type, Integer x, Integer y, Integer width, Integer height) {
    this.type = type;
    this.walls.add(new GameRectangle(x, y, width, height));
  }

  public Obstacle(ObstacleTypeEnum type, List<GameRectangle> walls) {
    this.type = type;
    this.walls = walls;
  }

  public Obstacle(ObstacleTypeEnum type, GameRectangle... walls) {
    this.type = type;
    this.walls = Arrays.asList(walls);
  }


  public ObstacleTypeEnum getType() {
    return type;
  }

  public void setType(ObstacleTypeEnum type) {
    this.type = type;
  }

  public List<GameRectangle> getWalls() {
    return walls;
  }

  public void setWalls(List<GameRectangle> walls) {
    this.walls = walls;
  }

  @Override
  public List<GameRuleEvent> applyAmmoDamage(Integer damage) {
    //todo
    return new ArrayList<>();
  }

  @Override
  public boolean hit(GameRectangle hitArea) {
    for (GameRectangle wall : walls) {
      if (wall.getRectangle().intersects(hitArea.getRectangle())) {
        return true;
      }
    }

    return false;
  }
}
