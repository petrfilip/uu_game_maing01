package uu.game.main.game.bulanci.ammo;

import java.util.Collection;
import uu.game.main.game.bulanci.BulanciMove;
import uu.game.main.game.bulanci.BulanciPlayer;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GameRectangle;

public class Bullet extends Ammo {

  private Direction direction;

  public Bullet() {
    super(null, null, 10, 10);
  }

  @Override
  public void applyEffect(Collection<? extends AmmoDamagable> players, Integer tick) {

    nextAmmoMove(20);

    for (AmmoDamagable damagable : players) {
      if (
        damagable instanceof AmmoHitable && ((AmmoHitable) damagable).hit(this) ||
        damagable instanceof GameRectangle && this.getRectangle().intersects(((GameRectangle) damagable).getRectangle())
      ) {
        damagable.applyAmmoDamage(-1);
        setUsed(true);
        return; // only the first damagable is affected
      }
    }

    //todo used when colision with obstacle

    //todo used when out of the board

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
  public void init(BulanciPlayer bulanciPlayer, BulanciMove bulanciMove) {
    this.setDirection(bulanciPlayer.getDirection());
    this.setX(bulanciPlayer.getX());
    this.setY(bulanciPlayer.getY());
    this.setWidth(10);
    this.setWidth(10);
    nextAmmoMove(100);
  }

  public Direction getDirection() {
    return direction;
  }

  public void setDirection(Direction direction) {
    this.direction = direction;
  }
}
