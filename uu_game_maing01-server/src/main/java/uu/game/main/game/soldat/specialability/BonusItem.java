package uu.game.main.game.soldat.specialability;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.ammo.AmmoDamagable;
import uu.game.main.game.soldat.SoldatPlayer;

public class BonusItem extends GameRectangle {

  public static final Integer BONUS_ITEM_MOVE_SPEED = 20;

  private boolean isUsed = false;

  @JsonIgnore
  private SpecialAbility specialAbility;

  private String specialAbilityName;

  public BonusItem(double x, double y, Integer width, Integer height, SpecialAbility specialAbility) {
    super(x, y, width, height);
    this.specialAbility = specialAbility;
    this.specialAbilityName = specialAbility.getClass().getSimpleName();
  }

  public List<GameRuleEvent> checkIntersection(Collection<SoldatPlayer> players) {

    for (SoldatPlayer player : players) {
      if (this.getRectangle().intersects(player.getRectangle())) {
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

  public String getSpecialAbilityName() {
    return specialAbilityName;
  }

  public void moveBonusItem() {

    setY(getY() + BONUS_ITEM_MOVE_SPEED);
    if (checkOutOfBound()) {
      isUsed = true;
    }
  }


  //todo params from game settings
  public boolean checkOutOfBound() {
    return getX() < 0 || getX() > 800 || getY() < 0 || getY() > 600;
  }

}
