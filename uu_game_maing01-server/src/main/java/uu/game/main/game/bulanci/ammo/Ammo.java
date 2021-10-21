package uu.game.main.game.bulanci.ammo;

import java.util.Collection;
import uu.game.main.game.bulanci.BulanciPlayer;
import uu.game.main.game.common.GameRectangle;

public abstract class Ammo extends GameRectangle {

  private boolean isUsed = false;

  public Ammo() {
    super(null, null, null, null);
  }

  public Ammo(Integer x, Integer y, Integer width, Integer height) {
    super(x, y, width, height);
  }

  public abstract void applyEffect(Collection<BulanciPlayer> value, Integer tick);

  public boolean isUsed() {
    return isUsed;
  }

  public void setUsed(boolean used) {
    isUsed = used;
  }
}
