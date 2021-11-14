package uu.game.main.game.bulanci.ammo;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import uu.game.main.game.bulanci.BulanciMove;
import uu.game.main.game.bulanci.BulanciPlayer;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;

public class Mine extends Ammo {

  private Integer activatedInTick;

  public Mine() {
  }


  @Override
  public List<GameRuleEvent> applyEffect(Collection<? extends AmmoDamagable> values, Integer tick) {

    for (AmmoDamagable damagable : values) {
      if (!(damagable instanceof GameRectangle)) {
        continue;
      }


      GameRectangle damagableRect = (GameRectangle) damagable;

      if (this.getRectangle().intersects(damagableRect.getRectangle()) && this.activatedInTick == null) {
        this.activatedInTick = tick;
      }

      if (this.getRectangle().intersects(damagableRect.getRectangle()) && this.activatedInTick - tick > 30) {
        List<GameRuleEvent> gameRuleEvents = damagable.applyAmmoDamage(10);
        gameRuleEvents.add(new GameRuleEvent("used", damagable.getClass().getSimpleName(), damagable));
        setUsed(true);
        return gameRuleEvents;
      }
    }

    return new ArrayList<>();
  }

  @Override
  public List<GameRuleEvent> init(BulanciPlayer bulanciPlayer, BulanciMove bulanciMove) {
    this.setX(bulanciPlayer.getX());
    this.setY(bulanciPlayer.getY());
    this.setWidth(40);
    this.setWidth(40);
    return Collections.singletonList(new GameRuleEvent("fired", this.getClass().getSimpleName(), this));
  }
}
