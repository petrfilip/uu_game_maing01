package uu.game.main.game.soldat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GameArea;
import uu.game.main.game.common.GameRandom;
import uu.game.main.game.common.Obstacle;

/**
 * Every part of the arena has to be somewhere a player can actually get to.
 *
 * <p>The terraces used to sit 100 px apart while a jump rose 90, which looks like a staircase and is
 * really a wall - the upper half of the level could only be entered by respawning into it. The trap
 * was that the obvious arithmetic said otherwise: {@code v²/2g} with the rule's constants gave
 * 100.2 px, comfortably over the gap. The simulation is a discrete Euler integration that applies
 * gravity before the first movement, so the take-off tick moves 18.8 px rather than 21 and the real
 * apex came out ten pixels lower. A formula agreed with the map and both were wrong, so the jump
 * here is always measured by running the physics.
 */
public class SoldatMapReachabilityTest {

  private static final int WIDTH = 1400;
  private static final int HEIGHT = 600;

  /**
   * The rise between two terraces, and equally a stack of two crates - the arena is drawn on a 50 px
   * grid and this is the unit an obstacle comes in.
   */
  private static final double TERRACE_STEP = 100;

  private static SoldatMove move(Direction direction) {
    SoldatMove move = new SoldatMove();
    move.setMove(direction);
    return move;
  }

  /** Runs a real jump on flat ground and reports how much height it gained. */
  private static double measuredJumpHeight() {
    SoldatArena arena = SoldatArena.of(1000, 600).withPlatform(0, 400, 900, 20);
    SoldatPlayer soldat = arena.addPlayer(100, 100);
    int tick = arena.settle(soldat, 1);
    double groundY = soldat.getY();

    soldat.applyInput(move(Direction.UP));
    arena.tick(tick++);

    double highest = soldat.getY();
    for (int i = 0; i < 120 && !soldat.isOnGround(); i++) {
      arena.tick(tick++);
      highest = Math.min(highest, soldat.getY());
    }
    return groundY - highest;
  }

  private static SoldatNavigation navigationForTheRealMap() {
    List<Obstacle> obstacles = SoldatMap.build(new GameArea(WIDTH, HEIGHT), new GameRandom(1));
    return SoldatNavigation.of(obstacles);
  }

  /**
   * The relationship the level is designed around, pinned from both sides.
   *
   * <p>This is what quietly broke: the jump cleared 90 px against a 100 px grid, so nothing in the
   * arena could be climbed - not a terrace, not even two crates - while every constant involved
   * still looked reasonable on its own.
   */
  @Test
  public void aJumpClearsOneStepOfTheLevelButNotTwo() {
    double measured = measuredJumpHeight();

    assertTrue(
      String.format("a jump reaches %.1f px but a step is %.0f - the level cannot be climbed at all",
        measured, TERRACE_STEP),
      measured > TERRACE_STEP);
    assertTrue(
      String.format("a jump reaches %.1f px and would clear two steps at once, which flattens the "
        + "level into one storey", measured),
      measured < 2 * TERRACE_STEP);
  }

  @Test
  public void theDiscreteJumpFallsShortOfTheTextbookFormula() {
    double measured = measuredJumpHeight();

    double formula = (SoldatPlayer.JUMP_VELOCITY * SoldatPlayer.JUMP_VELOCITY) / (2 * SoldatPlayer.GRAVITY);
    assertTrue(
      "the discrete simulation must not out-jump the continuous formula - if it does, the take-off "
        + "order in stepVertical has changed and every height in the level needs revisiting",
      measured < formula);
    assertTrue("a jump has to clear at least a player's own height", measured > SoldatPlayer.SIZE);
  }

  /**
   * The routing graph plans against a number; the physics deliver one. If they ever disagree the bot
   * walks confidently into things.
   */
  @Test
  public void theRoutingGraphAgreesWithThePhysicsAboutAJump() {
    assertEquals("SoldatNavigation must model the same jump the simulation performs",
      measuredJumpHeight(), SoldatNavigation.jumpHeight(), 0.001);
  }

  /**
   * No corner of the arena may be a dead end, in either direction.
   *
   * <p>Asked of the graph the bot actually navigates by, rather than of a second copy of the rules
   * written for the test - the first version of this test kept its own idea of what was reachable
   * and quietly assumed a floor the arena does not have, which made the map look connected when a
   * player walking off the left of the terraces simply died.
   */
  @Test
  public void everyPlatformCanBeReachedFromEveryOther() {
    SoldatNavigation navigation = navigationForTheRealMap();
    List<SoldatNavigation.Platform> platforms = navigation.platforms();
    assertTrue("the level should have a good handful of surfaces", platforms.size() > 10);

    List<String> strandings = new ArrayList<>();
    for (SoldatNavigation.Platform from : platforms) {
      for (SoldatNavigation.Platform to : platforms) {
        if (from != to && navigation.nextStepFrom(from, to) == null) {
          strandings.add(String.format("%s -> %s", describe(from), describe(to)));
        }
      }
    }

    assertTrue(String.format("%d pairs of surfaces have no route between them, e.g. %s",
      strandings.size(), strandings.isEmpty() ? "" : strandings.subList(0, Math.min(5, strandings.size()))),
      strandings.isEmpty());
  }

  private static String describe(SoldatNavigation.Platform platform) {
    return String.format("[y %.0f, x %.0f-%.0f]", platform.getTop(), platform.getLeft(), platform.getRight());
  }
}
