package uu.game.main.game.soldat.specialability;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import uu.game.main.game.bulanci.Obstacle;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.soldat.SoldatPlayer;

public class BonusItem extends GameRectangle {

  public static final Integer BONUS_ITEM_MOVE_SPEED = 20;

  private boolean isUsed = false;

  @JsonIgnore
  private final SpecialAbility specialAbility;

  private final String specialAbilityName;

  public BonusItem(double x, double y, Integer width, Integer height, SpecialAbility specialAbility) {
    super(x, y, width, height);
    this.specialAbility = specialAbility;
    this.specialAbilityName = specialAbility.getClass().getSimpleName();
  }

  public List<GameRuleEvent> checkIntersection(Collection<SoldatPlayer> players) {

    for (SoldatPlayer player : players) {
      if (this.getRectangle().intersects(player.getRectangle())) {
        List<GameRuleEvent> gameRuleEvents = player.applySpecialAbility(players, specialAbility);
        gameRuleEvents.add(new GameRuleEvent("gathered", player.getClass().getSimpleName(), specialAbility));
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

  public void moveBonusItem(List<Obstacle> obstacles) {

    Optional<GameRectangle> first = obstacles
      .stream().map(o -> o.intersectsWith(this))
      .filter(Objects::nonNull)
      .findFirst();

    // stop falling when reach the bottom
    first.ifPresent(gameRectangle -> setY(gameRectangle.getY() - getHeight()));

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
