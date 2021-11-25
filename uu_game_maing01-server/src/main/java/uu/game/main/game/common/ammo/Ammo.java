package uu.game.main.game.common.ammo;

import java.util.Collection;
import java.util.List;
import uu.game.main.game.bulanci.BulanciMove;
import uu.game.main.game.bulanci.BulanciPlayer;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;

public abstract class Ammo extends GameRectangle {

  private boolean isUsed = false;

  private String type;

  public Ammo() {
    this(null, null, null, null);
  }

  public Ammo(Integer x, Integer y, Integer width, Integer height) {
    super(x, y, width, height);
    this.type = this.getClass().getSimpleName();
  }

  public String getType() {
    return type;
  }

  public abstract List<GameRuleEvent> applyEffect(Collection<? extends AmmoDamagable> value, Integer tick);

  public boolean isUsed() {
    return isUsed;
  }

  public void setUsed(boolean used) {
    isUsed = used;
  }

  public abstract List<GameRuleEvent> init(BulanciPlayer bulanciPlayer, BulanciMove bulanciMove);
}
