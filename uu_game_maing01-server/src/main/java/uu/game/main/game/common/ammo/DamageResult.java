package uu.game.main.game.common.ammo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import uu.game.main.game.common.GameRuleEvent;

/**
 * Outcome of a projectile hitting something.
 *
 * <p>Replaces a bare {@code List<GameRuleEvent>} return, which could not express the two facts the
 * caller actually needed. Because it could not, {@code Bullet} credited its owner with a point for
 * hitting <em>anything</em> - including a wall. Shooting at a crate was an infinite score machine,
 * and in SCORE_ACHIEVED mode an instant win.
 */
public final class DamageResult {

  private static final DamageResult NONE = new DamageResult(Collections.emptyList(), false, false);

  private final List<GameRuleEvent> events;
  private final boolean absorbed;
  private final boolean lethal;

  private DamageResult(List<GameRuleEvent> events, boolean absorbed, boolean lethal) {
    this.events = events;
    this.absorbed = absorbed;
    this.lethal = lethal;
  }

  /** Nothing happened; the projectile passes through. */
  public static DamageResult none() {
    return NONE;
  }

  /** The target was hit and the projectile is consumed, but the target survived. */
  public static DamageResult absorbed(List<GameRuleEvent> events) {
    return new DamageResult(events, true, false);
  }

  /** The target was hit, the projectile is consumed and the hit was the killing blow. */
  public static DamageResult lethal(List<GameRuleEvent> events) {
    return new DamageResult(events, true, true);
  }

  public List<GameRuleEvent> getEvents() {
    return events;
  }

  public boolean isAbsorbed() {
    return absorbed;
  }

  /**
   * @return {@code true} only on the transition to dead, so repeated hits on a corpse cannot farm
   * score.
   */
  public boolean isLethal() {
    return lethal;
  }

  public List<GameRuleEvent> mutableEvents() {
    return new ArrayList<>(events);
  }
}
