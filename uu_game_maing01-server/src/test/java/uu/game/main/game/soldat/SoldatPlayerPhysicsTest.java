package uu.game.main.game.soldat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import uu.game.main.game.common.Direction;

/**
 * Physics is driven by calling {@link SoldatPlayer#step} directly - the simulation is tick based and
 * fully deterministic, so nothing here waits on a clock.
 */
public class SoldatPlayerPhysicsTest {

  private static final double EPSILON = 1e-9;

  private static SoldatMove move(Direction direction) {
    SoldatMove move = new SoldatMove();
    move.setMove(direction);
    return move;
  }

  @Test
  public void gravityAcceleratesAFallTickByTick() {
    SoldatArena arena = SoldatArena.bottomless();
    SoldatPlayer soldat = arena.addPlayer(100, 0);

    arena.tick(1);
    assertEquals(SoldatPlayer.GRAVITY, soldat.getVelocityY(), EPSILON);
    arena.tick(2);
    assertEquals(2 * SoldatPlayer.GRAVITY, soldat.getVelocityY(), EPSILON);
    arena.tick(3);
    assertEquals(3 * SoldatPlayer.GRAVITY, soldat.getVelocityY(), EPSILON);
  }

  @Test
  public void fallSpeedIsCapped() {
    SoldatArena arena = SoldatArena.bottomless();
    SoldatPlayer soldat = arena.addPlayer(100, 0);

    for (int tick = 1; tick <= 60; tick++) {
      arena.tick(tick);
    }

    // Uncapped, sixty ticks of 2.2 would be 132 px per tick - far more than collision resolution
    // can step through.
    assertEquals(SoldatPlayer.MAX_FALL_SPEED, soldat.getVelocityY(), EPSILON);
  }

  @Test
  public void landingOnAPlatformSetsOnGroundAndZeroesVerticalVelocity() {
    SoldatArena arena = SoldatArena.of(1000, 600).withPlatform(0, 400, 500, 20);
    SoldatPlayer soldat = arena.addPlayer(100, 100);

    arena.settle(soldat, 1);

    assertTrue(soldat.isOnGround());
    assertEquals(0, soldat.getVelocityY(), EPSILON);
    assertEquals(400, soldat.getBottom(), EPSILON);
  }

  @Test
  public void aJumpFromTheGroundRisesAndThenComesBackDown() {
    SoldatArena arena = SoldatArena.of(1000, 600).withPlatform(0, 400, 500, 20);
    SoldatPlayer soldat = arena.addPlayer(100, 100);
    int tick = arena.settle(soldat, 1);
    double groundY = soldat.getY();

    soldat.applyInput(move(Direction.UP));
    arena.tick(tick++);
    double afterTakeOff = soldat.getY();
    assertTrue("jump must gain height immediately", afterTakeOff < groundY);
    assertFalse(soldat.isOnGround());

    double highest = afterTakeOff;
    for (int i = 0; i < 60 && !soldat.isOnGround(); i++) {
      arena.tick(tick++);
      highest = Math.min(highest, soldat.getY());
    }

    assertTrue("player must land again", soldat.isOnGround());
    assertTrue("apex should be well above the platform", groundY - highest > SoldatPlayer.JUMP_VELOCITY);
    assertEquals("must land back on the platform", groundY, soldat.getY(), EPSILON);
  }

  @Test
  public void jumpingIsImpossibleInMidAir() {
    SoldatArena arena = SoldatArena.of(1000, 600).withPlatform(0, 400, 500, 20);
    SoldatPlayer soldat = arena.addPlayer(100, 100);
    int tick = arena.settle(soldat, 1);

    soldat.applyInput(move(Direction.UP));
    arena.tick(tick++);
    double velocityWhileRising = soldat.getVelocityY();
    double heightWhileRising = soldat.getY();

    // Second jump while still airborne: it must be ignored, not stack another take-off impulse.
    soldat.applyInput(move(Direction.UP));
    arena.tick(tick);

    // Gravity and nothing else: a honoured mid-air jump would reset the velocity to -JUMP_VELOCITY.
    assertEquals(velocityWhileRising + SoldatPlayer.GRAVITY, soldat.getVelocityY(), EPSILON);
    assertEquals(heightWhileRising + velocityWhileRising + SoldatPlayer.GRAVITY, soldat.getY(), EPSILON);
  }

  @Test
  public void aPlayerBlockedByAnotherPlayerDoesNotPassThrough() {
    SoldatArena arena = SoldatArena.of(1000, 600).withPlatform(0, 400, 900, 20);
    SoldatPlayer walker = arena.addPlayer("walker", 100, 370 - SoldatPlayer.SIZE);
    SoldatPlayer blocker = arena.addPlayer("blocker", 140, 370 - SoldatPlayer.SIZE);
    arena.settle(walker, 1);
    arena.settle(blocker, 1);

    for (int tick = 10; tick < 20; tick++) {
      walker.applyInput(move(Direction.RIGHT));
      walker.setCurrentTick(tick);
      walker.step(arena.board(), tick);
    }

    // Players used to walk straight through one another.
    assertEquals(blocker.getX(), walker.getRight(), EPSILON);
  }

  @Test
  public void playerCollisionCanBeSwitchedOffByGameParameter() {
    SoldatArena arena = SoldatArena.of(1000, 600)
      .withPlatform(0, 400, 900, 20)
      .withPlayerCollision(false);
    SoldatPlayer walker = arena.addPlayer("walker", 100, 370 - SoldatPlayer.SIZE);
    SoldatPlayer blocker = arena.addPlayer("blocker", 140, 370 - SoldatPlayer.SIZE);
    arena.settle(walker, 1);
    arena.settle(blocker, 1);

    for (int tick = 10; tick < 20; tick++) {
      walker.applyInput(move(Direction.RIGHT));
      walker.setCurrentTick(tick);
      walker.step(arena.board(), tick);
    }

    // Same geometry as the blocking test above; only the game parameter differs.
    assertTrue("with collision off the walker passes the blocker", walker.getX() > blocker.getRight());
  }

  @Test
  public void walkingIntoTheArenaEdgeStopsThePlayerRatherThanKillingThem() {
    SoldatArena arena = SoldatArena.of(400, 600).withPlatform(0, 400, 400, 20);
    SoldatPlayer soldat = arena.addPlayer(300, 370 - SoldatPlayer.SIZE);
    int tick = arena.settle(soldat, 1);

    for (int i = 0; i < 20; i++) {
      soldat.applyInput(move(Direction.RIGHT));
      soldat.setCurrentTick(tick);
      soldat.step(arena.board(), tick++);
    }

    assertEquals(400, soldat.getRight(), EPSILON);
    assertFalse("walking into the side of the map is not a death", soldat.isDead());
  }

  @Test
  public void fallingBelowTheArenaKillsThePlayer() {
    SoldatArena arena = SoldatArena.of(1000, 600);
    SoldatPlayer soldat = arena.addPlayer(100, 500);

    int tick = 1;
    for (; tick <= 50 && !soldat.isDead(); tick++) {
      arena.tick(tick);
    }

    assertTrue("the pit must kill", soldat.isDead());
    assertEquals(0, soldat.getLives());
  }
}
