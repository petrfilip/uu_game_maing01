package uu.game.main.game.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The set of boxes an entity cannot move through during a single tick.
 *
 * <p>Collision used to be scattered: obstacles were probed ad-hoc inside {@code SoldatPlayer} with
 * hand-rolled {@code getY() - 2} offsets, projectiles tested only against damagables, and players
 * were not blockers at all. Collecting every blocker into one place means a single definition of
 * "solid" and one swept-move implementation that all entities share.
 */
public class CollisionSpace {

  /**
   * Movement is resolved in steps no larger than this, so a fast-moving entity cannot tunnel
   * through a thin wall between two ticks.
   */
  private static final double MAX_STEP = 6.0;

  private final List<GameRectangle> blockers;

  /**
   * Blockers that move, layered on top of {@link #blockers} without being copied into it. Held by
   * reference and never modified here - it belongs to the caller.
   */
  private final Collection<? extends GameRectangle> movers;

  /** Whether {@link #blockers} is shared with another space, and so must not be written to. */
  private final boolean shared;

  public CollisionSpace() {
    this.blockers = new ArrayList<>();
    this.movers = Collections.emptyList();
    this.shared = false;
  }

  private CollisionSpace(List<GameRectangle> blockers, Collection<? extends GameRectangle> movers) {
    this.blockers = blockers;
    this.movers = movers;
    this.shared = true;
  }

  /**
   * The same fixed geometry with a set of moving blockers on top - other players, typically.
   *
   * <p>The level is shared, not copied. Building the space per entity per tick meant copying some
   * seventy walls for every player twenty times a second, to describe geometry that cannot change
   * for the whole match; all that varies between one player's view of the world and the next one's
   * is the handful of movers.
   *
   * <p>The returned space is a read-only view: {@link #add} on it would write through to the
   * geometry every other entity is using, so it refuses.
   */
  public CollisionSpace withMovers(Collection<? extends GameRectangle> movers) {
    return new CollisionSpace(blockers, movers == null ? Collections.<GameRectangle>emptyList() : movers);
  }

  public CollisionSpace add(GameRectangle blocker) {
    if (shared) {
      throw new IllegalStateException(
        "this space shares its geometry with others; layer blockers on with withMovers instead");
    }
    if (blocker != null) {
      blockers.add(blocker);
    }
    return this;
  }

  public CollisionSpace addAll(Collection<? extends GameRectangle> boxes) {
    for (GameRectangle box : boxes) {
      add(box);
    }
    return this;
  }

  /**
   * Adds every wall of every obstacle.
   */
  public CollisionSpace addWalls(Collection<? extends Collection<GameRectangle>> wallGroups) {
    for (Collection<GameRectangle> walls : wallGroups) {
      addAll(walls);
    }
    return this;
  }

  public boolean collides(GameRectangle box) {
    return firstHit(box) != null;
  }

  public GameRectangle firstHit(GameRectangle box) {
    // Fixed geometry first, then movers - the order blockers were added in before the two were
    // separated, so what an entity stops against does not change.
    GameRectangle hit = firstHitIn(blockers, box);
    return hit != null ? hit : firstHitIn(movers, box);
  }

  private static GameRectangle firstHitIn(Collection<? extends GameRectangle> candidates, GameRectangle box) {
    for (GameRectangle blocker : candidates) {
      if (blocker != box && blocker.intersects(box)) {
        return blocker;
      }
    }
    return null;
  }

  /**
   * Moves the entity horizontally as far as it can go, stopping flush against the first blocker.
   *
   * @return the distance actually travelled; smaller in magnitude than {@code dx} when blocked.
   */
  public double moveX(GameRectangle entity, double dx) {
    return move(entity, dx, true);
  }

  /**
   * Moves the entity vertically as far as it can go, stopping flush against the first blocker.
   *
   * @return the distance actually travelled; smaller in magnitude than {@code dy} when blocked.
   */
  public double moveY(GameRectangle entity, double dy) {
    return move(entity, dy, false);
  }

  private double move(GameRectangle entity, double delta, boolean horizontal) {
    if (delta == 0) {
      return 0;
    }

    double remaining = Math.abs(delta);
    double sign = Math.signum(delta);
    double travelled = 0;

    while (remaining > 0) {
      double step = Math.min(MAX_STEP, remaining) * sign;
      double before = horizontal ? entity.getX() : entity.getY();

      if (horizontal) {
        entity.setX(before + step);
      } else {
        entity.setY(before + step);
      }

      GameRectangle hit = firstHit(entity);
      if (hit != null) {
        // Back out of the blocker and sit flush against the edge we came from.
        if (horizontal) {
          entity.setX(sign > 0 ? hit.getX() - entity.getWidth() : hit.getRight());
        } else {
          entity.setY(sign > 0 ? hit.getY() - entity.getHeight() : hit.getBottom());
        }
        return travelled;
      }

      travelled += step;
      remaining -= Math.abs(step);
    }

    return travelled;
  }
}
