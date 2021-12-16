package uu.game.main.game.common.ammo;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import uu.game.main.abl.dto.Player;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.Player2D;
import uu.game.main.game.common.PlayerMove;

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
        getOwner().setScore(getOwner().getScore()+1);
        return gameRuleEvents;
      }
    }

    return new ArrayList<>();
  }

  @Override
  public List<GameRuleEvent> init(Player userPlayer, Player2D player2D, PlayerMove playerMove) {
    this.setOwner(userPlayer);

    this.setX(player2D.getX());
    this.setY(player2D.getY());
    this.setWidth(40);
    this.setWidth(40);
    return new GameRuleEvent("fired", this.getClass().getSimpleName(), this).asList();
  }
}