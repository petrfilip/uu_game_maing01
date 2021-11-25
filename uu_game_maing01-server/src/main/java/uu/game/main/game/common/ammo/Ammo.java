package uu.game.main.game.common.ammo;

import java.util.Collection;
import java.util.List;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.Player2D;
import uu.game.main.game.common.PlayerMove;

public abstract class Ammo extends GameRectangle {

  private boolean isUsed = false;

  public Ammo() {
    this(null, null, null, null);
  }

  public Ammo(Integer x, Integer y, Integer width, Integer height) {
    super(x, y, width, height);
  }

  public final String getType() {
    return this.getClass().getSimpleName();
  }

  public abstract List<GameRuleEvent> applyEffect(Collection<? extends AmmoDamagable> value, Integer tick);

  public boolean isUsed() {
    return isUsed;
  }

  public void setUsed(boolean used) {
    isUsed = used;
  }

  public abstract List<GameRuleEvent> init(Player2D player2D, PlayerMove playerMove);
}
