package uu.game.main.game.soldat.specialability;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import uu.game.main.game.common.CollisionSpace;
import uu.game.main.game.common.CompactDoubleSerializer;
import uu.game.main.game.common.GameArea;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.soldat.SoldatPlayer;

/**
 * A crate that parachutes in, lands on the first surface below it and grants an ability when
 * touched.
 */
public class BonusItem extends GameRectangle {

  public static final int SIZE = 30;

  /** Fall speed, in px per tick. */
  private static final double FALL_SPEED = 6;

  /**
   * How far this crate actually moved on the last tick; zero once it has settled.
   *
   * <p>Sent so the client can carry the crate between states the way it already does for players.
   * The simulation runs at twenty ticks a second and the canvas draws at sixty, so a crate drawn
   * only where the server last reported it steps down the screen three frames at a time - which is
   * exactly as jerky as it sounds. Omitted from the payload once it is zero, so a landed crate
   * costs nothing.
   */
  private double velocityY = FALL_SPEED;

  private boolean isUsed = false;

  @JsonIgnore
  private final SpecialAbility specialAbility;

  private final String specialAbilityName;

  public BonusItem(double x, double y, SpecialAbility specialAbility) {
    super(x, y, SIZE, SIZE);
    this.specialAbility = specialAbility;
    this.specialAbilityName = specialAbility.getName();
  }

  public List<GameRuleEvent> checkIntersection(Collection<SoldatPlayer> players) {
    for (SoldatPlayer player : players) {
      if (player.isDead() || !intersects(player)) {
        continue;
      }
      List<GameRuleEvent> events = new ArrayList<>();
      events.add(player.applySpecialAbility(players, specialAbility));
      isUsed = true;
      return events;
    }
    return new ArrayList<>();
  }

  /**
   * Falls one tick's worth and stops on the first solid surface.
   *
   * <p>The old version snapped to the top of an overlapping obstacle and then added the fall speed
   * again unconditionally, so a landed crate sank back into the floor every tick and jittered.
   */
  public void fall(CollisionSpace space, GameArea area) {
    // The distance it managed is this tick's velocity: a full FALL_SPEED in the air, less on the
    // tick it meets a surface, and zero every tick after that.
    velocityY = space.moveY(this, FALL_SPEED);
    if (area.isBelow(this)) {
      isUsed = true;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_DEFAULT)
  @JsonSerialize(using = CompactDoubleSerializer.class)
  public double getVelocityY() {
    return velocityY;
  }

  public boolean isUsed() {
    return isUsed;
  }

  @JsonIgnore
  public SpecialAbility getSpecialAbility() {
    return specialAbility;
  }

  public String getSpecialAbilityName() {
    return specialAbilityName;
  }
}
