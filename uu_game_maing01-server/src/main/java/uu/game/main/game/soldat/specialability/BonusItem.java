package uu.game.main.game.soldat.specialability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.ammo.AmmoDamagable;
import uu.game.main.game.soldat.SoldatPlayer;

public class BonusItem extends GameRectangle {

  private boolean isUsed = false;

  private SpecialAbility specialAbility;

  public BonusItem(double x, double y, Integer width, Integer height, SpecialAbility specialAbility) {
    super(x, y, width, height);
    this.specialAbility = specialAbility;
  }

  public List<GameRuleEvent> checkIntersection(Collection<SoldatPlayer> players) {

    for (SoldatPlayer player : players) {
      if (this.getRectangle().intersects(player.getRectangle()) ) {
        List<GameRuleEvent> gameRuleEvents = player.applySpecialAbility(players, specialAbility);
        gameRuleEvents.add(new GameRuleEvent("used", player.getClass().getSimpleName(), specialAbility));
        isUsed = true;
        return gameRuleEvents;
      }
    }

    return new ArrayList<>();
  }

  public boolean isUsed() {
    return isUsed;
  }

  public SpecialAbility getSpecialAbility() {
    return specialAbility;
  }
}
