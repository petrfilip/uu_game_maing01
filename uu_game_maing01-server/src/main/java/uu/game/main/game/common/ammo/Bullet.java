package uu.game.main.game.common.ammo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import uu.game.main.game.bulanci.BulanciMove;
import uu.game.main.game.bulanci.BulanciPlayer;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.Player2D;

public class Bullet extends Ammo {

  private Direction direction;
  private Integer bulletSpeed = 20;

  public Bullet() {
    super(null, null, 10, 10);
  }

  @Override
  public List<GameRuleEvent> applyEffect(Collection<? extends AmmoDamagable> players, Integer tick) {

    nextAmmoMove(bulletSpeed);

    for (AmmoDamagable damagable : players) {
      if (damagable instanceof Intersectable && ((Intersectable) damagable).intersects(this)) {
        List<GameRuleEvent> gameRuleEvents = damagable.applyAmmoDamage(-1);
        gameRuleEvents.add(new GameRuleEvent("used", this.getClass().getSimpleName(), this));
        setUsed(true);
        return gameRuleEvents;
      }
    }

    //todo used when colision with obstacle

    //todo used when out of the board

    return new ArrayList<>();

  }

  private void nextAmmoMove(Integer bulletSpeed) {
    switch (direction) {
      case RIGHT:
        setX(getX() + bulletSpeed);
        break;
      case LEFT:
        setX(getX() - bulletSpeed);
        break;
      case UP:
        setY(getY() + bulletSpeed);
        break;
      case DOWN:
        setY(getY() - bulletSpeed);
        break;
    }
  }

  @Override
  public List<GameRuleEvent> init(Player2D bulanciPlayer) {
    this.setDirection(bulanciPlayer.getDirection());
    this.setX(bulanciPlayer.getX());
    this.setY(bulanciPlayer.getY());
    this.setWidth(10);
    this.setWidth(10);
    nextAmmoMove(bulletSpeed);
    return Collections.singletonList(new GameRuleEvent("fired", this.getClass().getSimpleName(), this));
  }

  public Direction getDirection() {
    return direction;
  }

  public void setDirection(Direction direction) {
    this.direction = direction;
  }

  public Integer getBulletSpeed() {
    return bulletSpeed;
  }

  public void setBulletSpeed(Integer bulletSpeed) {
    this.bulletSpeed = bulletSpeed;
  }
}
