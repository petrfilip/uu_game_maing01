package uu.game.main.game.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import uu.game.main.game.common.ammo.AmmoDamagable;
import uu.game.main.game.common.ammo.DamageResult;
import uu.game.main.game.common.ammo.Intersectable;

/**
 * A piece of level geometry, made of one or more solid boxes.
 *
 * <p>Lives in {@code common} rather than in the Bulanci package: Soldat has always used it, and a
 * game importing another game's classes is exactly the kind of coupling the rule abstraction exists
 * to prevent.
 */
public class Obstacle implements AmmoDamagable, Intersectable {

  private ObstacleTypeEnum type;

  private List<GameRectangle> walls = new ArrayList<>();

  public Obstacle(ObstacleTypeEnum type, double x, double y, Integer width, Integer height) {
    this.type = type;
    this.walls.add(new GameRectangle(x, y, width, height));
  }

  public Obstacle(ObstacleTypeEnum type, List<GameRectangle> walls) {
    this.type = type;
    this.walls = new ArrayList<>(walls);
  }

  public Obstacle(ObstacleTypeEnum type, GameRectangle... walls) {
    this.type = type;
    this.walls = new ArrayList<>(Arrays.asList(walls));
  }

  public ObstacleTypeEnum getType() {
    return type;
  }

  public void setType(ObstacleTypeEnum type) {
    this.type = type;
  }

  public List<GameRectangle> getWalls() {
    return Collections.unmodifiableList(walls);
  }

  public void setWalls(List<GameRectangle> walls) {
    this.walls = new ArrayList<>(walls);
  }

  /**
   * Obstacles soak up projectiles but never die and never award a kill.
   */
  @Override
  public DamageResult applyAmmoDamage(int damage) {
    return DamageResult.absorbed(new ArrayList<>());
  }

  @Override
  public boolean intersects(GameRectangle hitArea) {
    return intersectsWith(hitArea) != null;
  }

  /**
   * @return the specific wall that was hit, or {@code null}
   */
  public GameRectangle intersectsWith(GameRectangle hitArea) {
    for (GameRectangle wall : walls) {
      if (wall.intersects(hitArea)) {
        return wall;
      }
    }
    return null;
  }
}
