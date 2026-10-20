package uu.game.main.game.soldat;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import uu.game.main.game.common.GameArea;
import uu.game.main.game.common.GameRandom;

/**
 * The bot has to keep making progress towards whoever it is chasing.
 *
 * <p>It does not need to be clever - it is half a page of code and meant to be - but it does need to
 * stop doing something that is plainly not working. Walking into a wall taller than it can jump is
 * the case that shows up in play: the bot pressed into it and hopped on the spot for the rest of the
 * round, which reads as a broken opponent rather than an easy one.
 */
public class SoldatBotTest {

  /** Tall enough that no jump clears it, so shoving can never succeed. */
  private static final int UNJUMPABLE = 250;

  private static final int FLOOR_Y = 570;

  /**
   * Near enough to count as having got to somebody.
   *
   * <p>Generous on purpose: a bot on the terrace directly above its target has arrived for every
   * purpose that matters - it can see them and shoot them - and insisting on touching distance would
   * fail that as though it had never set off.
   */
  private static final int CLOSE_ENOUGH = 200;

  private static SoldatArena arenaWithFloor() {
    return SoldatArena.of(1400, 600).withPlatform(0, FLOOR_Y, 1400, 30);
  }

  /** Lets the bot act for one tick. */
  private static void step(SoldatArena arena, SoldatPlayer bot, SoldatBot ai, int tick) {
    for (SoldatMove move : ai.decide(bot, arena.board(), arena.board().getPlayers().values(), tick, new GameRandom(7))) {
      bot.applyInput(move);
    }
    arena.tick(tick);
  }

  @Test
  public void aBotWalksTowardsItsTarget() {
    SoldatArena arena = arenaWithFloor();
    SoldatPlayer bot = arena.addPlayer("0-0", 200, 500);
    SoldatPlayer target = arena.addPlayer("human", 600, 500);
    arena.settle(bot, 1);
    arena.settle(target, 1);
    double startX = bot.getX();

    SoldatBot ai = new SoldatBot();
    for (int tick = 1; tick <= 30; tick++) {
      step(arena, bot, ai, tick);
    }

    assertTrue("the bot must close on its target across open ground", bot.getX() > startX + 100);
  }

  /**
   * The whole job, on the real level: cross the arena and climb to whoever is up there.
   *
   * <p>This is the one that was actually broken in play. A single range check gated the entire
   * decision, so an opponent standing more than 700 px away on a 1400 px map was never chased - the
   * bot stood exactly where it spawned for the whole round. Everything else about it worked; it just
   * never started. The test therefore begins deliberately out of that range.
   */
  @Test
  public void aBotCrossesTheArenaAndClimbsToReachSomeone() {
    GameArea area = new GameArea(1400, 600);
    SoldatArena arena = SoldatArena.of(1400, 600);
    arena.board().setObstacles(SoldatMap.build(area, new GameRandom(1)));

    SoldatPlayer bot = arena.addPlayer("0-0", 300, 500);
    SoldatPlayer target = arena.addPlayer("human", 1100, 200 - SoldatPlayer.SIZE - 1);
    arena.settle(bot, 1);
    arena.settle(target, 1);

    double startDistance = distanceBetween(bot, target);
    assertTrue("the point of this test is a target out of firing range", startDistance > 800);

    SoldatBot ai = new SoldatBot();
    for (int tick = 1; tick <= 200; tick++) {
      step(arena, bot, ai, tick);
    }

    assertTrue(
      String.format("the bot started %.0f px away and is still %.0f px away after ten seconds - it "
        + "is not getting to anyone", startDistance, distanceBetween(bot, target)),
      distanceBetween(bot, target) < 100);
    assertTrue("it has to actually climb, not just walk under them", bot.getY() < 300);
  }

  private static double distanceBetween(SoldatPlayer a, SoldatPlayer b) {
    return Math.hypot(b.getCenterX() - a.getCenterX(), b.getCenterY() - a.getCenterY());
  }

  @Test
  public void aBotStopsShovingAWallItCannotJumpAndTriesTheOtherWay() {
    SoldatArena arena = arenaWithFloor()
      // A wall between the two of them, taller than any jump.
      .withPlatform(600, FLOOR_Y - UNJUMPABLE, 40, UNJUMPABLE);
    SoldatPlayer bot = arena.addPlayer("0-0", 400, 500);
    SoldatPlayer target = arena.addPlayer("human", 800, 500);
    arena.settle(bot, 1);
    arena.settle(target, 1);

    SoldatBot ai = new SoldatBot();

    // Walk it into the wall first. Where it comes to rest is the baseline: anything measured before
    // this point is just the approach, and comparing against the starting position would pass no
    // matter what the bot did afterwards.
    int tick = 1;
    for (int i = 0; i < 40 && !bot.isBlockedHorizontally(); i++) {
      step(arena, bot, ai, tick++);
    }
    assertTrue("the bot never even reached the wall - check the arena", bot.isBlockedHorizontally());
    double xAtWall = bot.getX();

    // Now give it long enough to try jumping, fail, and think better of it.
    double leftmost = xAtWall;
    for (int i = 0; i < 120; i++) {
      step(arena, bot, ai, tick++);
      leftmost = Math.min(leftmost, bot.getX());
    }

    assertTrue(
      String.format("the bot stayed pinned against the wall: it reached x %.0f and never backed off "
        + "further than %.0f, so it is still shoving something it cannot climb", xAtWall, leftmost),
      leftmost < xAtWall - SoldatPlayer.SIZE);
  }

  /**
   * The one from the room that was reported: the bot stopped and never moved again.
   *
   * <p>Chasing somebody on the terrace below and to the left, it came down on the corner of a ledge
   * on the way - and from there, the point it was steering for, where the next step down puts it,
   * was already twelve pixels away. Less than one stride. So it decided it had arrived, and stood on
   * the brink of that drop for the rest of the match while the player it was chasing walked around
   * in plain sight.
   *
   * <p>Which is why a walk-off is now steered at the edge it has to clear rather than at the ground
   * it is aiming for: you arrive at a mark, but you leave by running out of floor.
   */
  @Test
  public void aBotStepsOffALedgeInsteadOfStandingOnTheBrink() {
    GameArea area = new GameArea(1400, 600);
    SoldatArena arena = SoldatArena.of(1400, 600);
    arena.board().setObstacles(SoldatMap.build(area, new GameRandom(1)));

    // The far right of the map, chasing somebody on the long terrace on the other side of it: the
    // route down passes over the ledge the bot used to stop on.
    SoldatPlayer bot = arena.addPlayer("0-0", 1225, 500 - SoldatPlayer.SIZE - 1);
    SoldatPlayer target = arena.addPlayer("human", 900, 500 - SoldatPlayer.SIZE - 1);
    arena.settle(bot, 1);
    arena.settle(target, 1);
    double startDistance = distanceBetween(bot, target);

    SoldatBot ai = new SoldatBot();
    double closest = startDistance;
    for (int tick = 1; tick <= 200; tick++) {
      step(arena, bot, ai, tick);
      closest = Math.min(closest, distanceBetween(bot, target));
    }

    assertTrue(
      String.format("the bot set off %.0f px away and got no closer than %.0f in ten seconds - it is "
        + "stopped somewhere on the way", startDistance, closest),
      closest < CLOSE_ENOUGH);
  }

  /**
   * The backstop, for the ruts nobody has thought of yet.
   *
   * <p>Here there is nothing to think of: the target is on a shelf far too high to climb and directly
   * overhead, so the bot hops on the spot underneath it, forever. Every specific rule it has says
   * this is the right thing to do - it is not walking into anything, it is not being blocked - and
   * the only thing wrong with it is that it never gets anywhere. That is what has to be noticed.
   */
  @Test
  public void aBotThatIsGettingNowhereTriesSomethingElse() {
    SoldatArena arena = arenaWithFloor()
      // Out of reach by any jump, and nothing to climb on the way.
      .withPlatform(600, 200, 100, 25);
    SoldatPlayer bot = arena.addPlayer("0-0", 640, 500);
    SoldatPlayer target = arena.addPlayer("human", 640, 200 - SoldatPlayer.SIZE - 1);
    arena.settle(bot, 1);
    arena.settle(target, 1);
    double startX = bot.getX();

    SoldatBot ai = new SoldatBot();
    double furthest = 0;
    for (int tick = 1; tick <= 200; tick++) {
      step(arena, bot, ai, tick);
      furthest = Math.max(furthest, Math.abs(bot.getX() - startX));
    }

    assertTrue(
      String.format("ten seconds of hopping under an unreachable shelf and the bot never got further "
        + "than %.0f px from where it started - it is still trying the one thing that does not work",
        furthest),
      furthest > SoldatPlayer.SIZE * 3);
  }

  /**
   * The whole map, both ways round: wherever the bot starts, it can get to wherever anybody stands.
   *
   * <p>Every stuck bot so far has been a particular pair of places - land here, want to be there,
   * and the plan for getting between the two quietly does nothing. Cases like that are not found by
   * imagining them, so this walks all of them: every standable surface against every other, which on
   * this level is a few hundred runs and takes about as long as one of the tests above.
   */
  @Test
  public void aBotReachesAnyoneStandingAnywhereOnTheMap() {
    GameArea area = new GameArea(1400, 600);
    SoldatArena probe = SoldatArena.of(1400, 600);
    probe.board().setObstacles(SoldatMap.build(area, new GameRandom(1)));
    List<SoldatNavigation.Platform> platforms = probe.board().navigation().platforms();

    List<String> failures = new ArrayList<>();
    for (int from = 0; from < platforms.size(); from++) {
      for (int to = 0; to < platforms.size(); to++) {
        if (from != to) {
          String failure = chase(area, platforms, from, to);
          if (failure != null) {
            failures.add(failure);
          }
        }
      }
    }

    assertTrue(
      String.format("%d of %d starting positions leave the bot unable to reach a motionless "
          + "opponent within twenty-five seconds, e.g. %s", failures.size(),
        platforms.size() * (platforms.size() - 1), failures.subList(0, Math.min(5, failures.size()))),
      failures.isEmpty());
  }

  /**
   * Runs one chase: the bot on one surface, somebody standing still on another.
   *
   * @return what went wrong, or null when the bot got to them
   */
  private static String chase(GameArea area, List<SoldatNavigation.Platform> platforms, int from, int to) {
    SoldatArena arena = SoldatArena.of(1400, 600);
    arena.board().setObstacles(SoldatMap.build(area, new GameRandom(1)));

    SoldatNavigation.Platform start = platforms.get(from);
    SoldatNavigation.Platform theirs = platforms.get(to);
    SoldatPlayer bot = arena.addPlayer("0-0", middleOf(start), start.getTop() - SoldatPlayer.SIZE - 1);
    SoldatPlayer target = arena.addPlayer("human", middleOf(theirs), theirs.getTop() - SoldatPlayer.SIZE - 1);
    arena.settle(bot, 1);
    arena.settle(target, 1);
    // Enough lives that a fall into the pit respawns it and the chase carries on, as a match would.
    bot.setMaxLives(99);
    bot.setLives(99);

    SoldatBot ai = new SoldatBot();
    GameRandom random = new GameRandom(11);
    double closest = distanceBetween(bot, target);
    for (int tick = 1; tick <= 500; tick++) {
      for (SoldatMove move : ai.decide(bot, arena.board(), arena.board().getPlayers().values(), tick, random)) {
        bot.applyInput(move);
      }
      // The target stands its ground and takes no damage: this is about getting there, not fighting.
      target.setLives(99);
      arena.tick(tick);
      closest = Math.min(closest, distanceBetween(bot, target));
    }

    return closest <= CLOSE_ENOUGH ? null
      : String.format("[y %.0f x %.0f-%.0f] -> [y %.0f x %.0f-%.0f] (closest %.0f px)",
        start.getTop(), start.getLeft(), start.getRight(),
        theirs.getTop(), theirs.getLeft(), theirs.getRight(), closest);
  }

  private static double middleOf(SoldatNavigation.Platform platform) {
    return (platform.getLeft() + platform.getRight()) / 2 - SoldatPlayer.SIZE / 2.0;
  }

  /**
   * An opponent is not a wall, and the bot may not treat one as something to be climbed.
   *
   * <p>Players block each other, so pressing into somebody reads exactly like pressing into a crate:
   * blocked horizontally, therefore jump. The bot duly jumped, landed on their head, and stood
   * there - where it cannot shoot them, because everything it can shoot is in front of it and they
   * are underneath. In play that is a bot riding around on the player it is supposed to be fighting.
   */
  @Test
  public void aBotFightsItsTargetInsteadOfClimbingOnto() {
    SoldatArena arena = arenaWithFloor();
    SoldatPlayer bot = arena.addPlayer("0-0", 400, 500);
    SoldatPlayer target = arena.addPlayer("human", 600, 500);
    arena.settle(bot, 1);
    arena.settle(target, 1);

    SoldatBot ai = new SoldatBot();
    GameRandom random = new GameRandom(3);
    boolean stoodOnThem = false;
    int shots = 0;
    for (int tick = 1; tick <= 150; tick++) {
      for (SoldatMove move : ai.decide(bot, arena.board(), arena.board().getPlayers().values(), tick, random)) {
        if (move.getFired() != null) {
          shots++;
        }
        bot.applyInput(move);
      }
      arena.tick(tick);
      stoodOnThem = stoodOnThem || standingOn(bot, target);
    }

    assertTrue("the bot climbed onto its target and rode around on them", !stoodOnThem);
    assertTrue("the bot got next to its target and never took a shot", shots > 0);
  }

  /**
   * And if it ever does end up up there - two of them can land on the same spot - it gets down.
   */
  @Test
  public void aBotStandingOnSomebodyGetsOffThem() {
    SoldatArena arena = arenaWithFloor();
    SoldatPlayer target = arena.addPlayer("human", 600, 500);
    arena.settle(target, 1);
    // Squarely on their head, which is where a jump over them puts it.
    SoldatPlayer bot = arena.addPlayer("0-0", target.getX(), target.getY() - SoldatPlayer.SIZE);
    arena.settle(bot, 1);
    assertTrue("the test has to start with the bot standing on them", standingOn(bot, target));

    SoldatBot ai = new SoldatBot();
    GameRandom random = new GameRandom(3);
    for (int tick = 1; tick <= 40; tick++) {
      step(arena, bot, ai, tick);
    }

    assertTrue(
      String.format("two seconds on, the bot is still standing on its target at (%.0f, %.0f)",
        bot.getX(), bot.getY()),
      !standingOn(bot, target));
  }

  /** Feet on their head, which is what "blocked" looks like after a jump at somebody. */
  private static boolean standingOn(SoldatPlayer bot, SoldatPlayer other) {
    return bot.isOnGround()
      && Math.abs(bot.getBottom() - other.getY()) <= 2
      && other.getRight() > bot.getX() && other.getX() < bot.getRight();
  }
}
