package uu.game.main.game.bulanci.ammo;

import java.util.Collection;
import uu.game.main.game.bulanci.BulanciPlayer;
import uu.game.main.game.common.Direction;

public class Bullet extends Ammo {

  private Direction direction;

  public Bullet() {
  }

  @Override
  public void applyEffect(Collection<BulanciPlayer> players, Integer tick) {

    switch (direction) {
      case RIGHT:
        setX(getX() + 20);
        break;
      case LEFT:
        setX(getX() - 20);
        break;
      case UP:
        setY(getY() + 20);
        break;
      case DOWN:
        setY(getY() - 20);
        break;
    }

    for (BulanciPlayer player : players) {
      if (this.getRectangle().intersects(player.getRectangle())) {
        player.setLives(player.getLives() - 1);
        setUsed(true);
        return; // only the first player can be killed
      }
    }

  }

  public Direction getDirection() {
    return direction;
  }

  public void setDirection(Direction direction) {
    this.direction = direction;
  }
}
