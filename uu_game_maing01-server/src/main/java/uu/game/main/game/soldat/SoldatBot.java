package uu.game.main.game.soldat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GameRandom;
import uu.game.main.helper.MathUtils;

/**
 * A very small opponent AI.
 *
 * <p>The bot player already existed but nothing ever drove it, so it stood still and served as a
 * target. It now chases the nearest living player, jumps when it walks into something or when the
 * target is overhead, and shoots when it has line of sight along roughly the right angle.
 */
final class SoldatBot {

  /** The bot only shoots when the target is within this many degrees of its aim. */
  private static final double AIM_TOLERANCE_DEGREES = 12;

  /**
   * How far the bot will shoot from.
   *
   * <p>This gates firing only. It used to gate the whole decision - beyond it the bot returned no
   * moves at all - which meant that on a 1400 px map an opponent who simply stood far enough away
   * was never chased: the bot parked where it was and waited to be walked into. A range limit on
   * shooting is an opponent that misses less; the same limit on walking is an opponent that never
   * arrives.
   */
  private static final int FIRING_RANGE = 700;

  /**
   * How long the bot keeps trying to climb something before accepting that it cannot.
   *
   * <p>A jump takes about twenty ticks to land, so this is roughly a failed attempt and a half -
   * long enough that an obstacle it can actually clear is never abandoned, short enough that a wall
   * it cannot does not hold it for the rest of the round.
   */
  private static final int SHOVING_TICKS_BEFORE_DETOUR = 30;

  /** How long it then walks the other way: about 325 px, wider than anything in the arena. */
  private static final int DETOUR_TICKS = 25;

  /**
   * How long the bot is watched before its lack of progress counts as being stuck - two seconds.
   *
   * <p>This is the backstop, not the plan. Everything above it reasons about a particular way of
   * being stuck and knows what to do about it; this one only notices that nothing is happening, and
   * is deliberately ignorant of why. Every specific fix so far has been to a rut nobody predicted,
   * so the useful question is not which rut this is but whether the bot is still getting anywhere -
   * and that can be answered without knowing.
   */
  private static final int STALL_WINDOW_TICKS = 40;

  /**
   * How far it must travel within that window to count as going somewhere.
   *
   * <p>Its own width, which at walking pace takes two ticks: over two seconds, anything less is not
   * slow progress, it is a bot standing on a spot or hopping on one.
   */
  private static final double STALL_TRAVEL = SoldatPlayer.SIZE;

  /**
   * How long it may fail to get any closer to its target before that counts as being stuck - three
   * seconds.
   *
   * <p>Standing still is the obvious rut and the easiest to spot, but not the common one. The
   * expensive ones are loops: walk to the edge, jump at the wall behind it, fall back, walk to the
   * edge again, for the rest of the round. Every part of that is movement, and none of it is
   * progress, so distance to the target is what is watched here rather than ground covered.
   */
  private static final int NO_PROGRESS_TICKS = 60;

  /** How much closer counts as having got somewhere: a stride, so a rounding error does not. */
  private static final double PROGRESS_MARGIN = SoldatPlayer.INIT_SPEED;

  /**
   * How far the target may wander before the bot is given a clean slate.
   *
   * <p>Chasing somebody who is running away is the one honest way to make no progress for seconds at
   * a time, and a bot that abandoned the chase to wander off at random would be worse than the rut
   * this is here to break. So a target that has moved resets the clock: only a bot that cannot close
   * on somebody standing still is stuck.
   */
  private static final double TARGET_MOVED = 60;

  /** Close enough that standing and shooting is a fight rather than a rut. */
  private static final double ENGAGED_RANGE = 100;

  /** Bounds on how long a shaken-loose bot commits to its random direction. */
  private static final int ESCAPE_MIN_TICKS = 15;
  private static final int ESCAPE_MAX_TICKS = 36;

  /** Consecutive ticks spent walking into something that has not moved. */
  private int shovingTicks;

  private int detourTicksLeft;
  private Direction detourDirection;

  /**
   * Where the bot has been for the last {@link #STALL_WINDOW_TICKS} ticks, oldest first.
   *
   * <p>A ring buffer rather than a list, because this is read and written every tick of every match
   * and the one thing the simulation cannot afford is per-tick allocation.
   */
  private final double[] recentX = new double[STALL_WINDOW_TICKS];
  private final double[] recentY = new double[STALL_WINDOW_TICKS];
  private int recorded;

  /** The closest it has been to its target since it last got closer, and how long ago that was. */
  private double closestSoFar = Double.MAX_VALUE;
  private int ticksWithoutProgress;
  private double targetXWhenLastCloser;
  private double targetYWhenLastCloser;

  /** Where that jump is meant to put it down. */
  private double committedLandingX;
  private double committedLandingTop;
  private boolean committed;

  /**
   * Produces this tick's moves for a bot - walking, jumping and firing are separate moves and it may
   * do all three - or an empty list when it has nothing to do.
   *
   * @param random the match's randomness; the bot's hesitation and aim wobble are part of the
   *     simulation and have to replay with it
   */
  List<SoldatMove> decide(SoldatPlayer bot, SoldatBoard board, Collection<SoldatPlayer> everyone, int tick,
                          GameRandom random) {
    if (bot.isDead()) {
      // It respawns somewhere else entirely, so whatever it was working around is no longer its
      // problem.
      forgetWhatItWasDoing();
      return Collections.emptyList();
    }

    SoldatPlayer target = nearestTarget(bot, everyone);
    if (target == null) {
      return Collections.emptyList();
    }

    double dx = target.getCenterX() - bot.getCenterX();
    double dy = target.getCenterY() - bot.getCenterY();
    double distance = Math.hypot(dx, dy);

    // A tick's intent is several moves, not one. Walking and jumping are separate inputs to the
    // player, so packing both into a single move meant the later one silently replaced the earlier:
    // the bot could walk, or jump straight up, but never jump *while* walking - which is the only
    // way anything gets over an obstacle. It hopped on the spot against walls instead.
    List<SoldatMove> moves = new ArrayList<>(3);

    // Whatever it thinks it is doing, it has to be getting somewhere while it does it.
    if (hasStopped(bot, target, distance)) {
      shakeLoose(random);
    }

    // Contact. An opponent is not a wall, and this has to be settled before anything that reasons
    // about obstacles, because to that code they look like one: players block each other, so
    // pressing into somebody reads as "blocked" exactly like pressing into a crate, and the answer
    // to a crate is to jump it. The bot duly jumped, landed on their head, and stood there - the one
    // place from which it cannot shoot them, since everything it can shoot is in front of it and
    // they are underneath.
    if (isInContactWith(bot, target)) {
      // Shoving and detours are about walls. Somebody standing in front of the bot is not a problem
      // to be routed around; it is the reason the bot is there.
      shovingTicks = 0;
      detourTicksLeft = 0;
      Direction step = standingOn(bot, target)
        ? offTheHeadOf(bot, target, board.navigation()) : (dx > 0 ? Direction.RIGHT : Direction.LEFT);
      if (step != null) {
        moves.add(moveTowards(step));
      }
      return firing(moves, bot, target, dx, dy, distance, random);
    }

    if (detourTicksLeft > 0) {
      // Already decided this one is not worth climbing. Walking away from the target looks wrong for
      // a tick or two, and it is the only thing that gets it past a wall it cannot jump.
      detourTicksLeft--;
      // Improvising, not routing, so nothing has checked that the way it is walking is still a
      // floor. Turning at a drop with nothing under it is the difference between a bot that works
      // its way out of a corner and one that walks off the map trying to.
      if (walkingIntoThePit(bot, board.navigation(), detourDirection)) {
        detourDirection = detourDirection == Direction.LEFT ? Direction.RIGHT : Direction.LEFT;
      }
      moves.add(moveTowards(detourDirection));
      if (bot.isOnGround() && bot.isBlockedHorizontally()) {
        moves.add(moveTowards(Direction.UP));
      }
    } else {
      // Where to walk, which is not always towards the target: getting onto the terrace they are
      // standing on may well mean walking away from them first, to wherever it can be climbed.
      SoldatNavigation navigation = board.navigation();
      SoldatNavigation.Platform standingOn = navigation.under(bot);
      SoldatNavigation.Step hop = standingOn == null
        ? null : navigation.nextStepFrom(standingOn, navigation.under(target));

      double waypointX;
      boolean needsJump;
      // Walking off an edge ends when the ground runs out, not at a mark, and the difference is not
      // academic: aimed at its landing instead, a bot dropping onto a terrace a few pixels below and
      // to the side found that mark already within one stride of where it stood, concluded it had
      // arrived, and spent the rest of the round standing on the lip of the drop.
      boolean steppingOff = false;
      // Whether reaching the waypoint would actually put the bot where it wants to be. It would,
      // for anything the graph planned; walking straight at a target is the case where it might not.
      boolean sameLevel = true;
      if (hop != null) {
        // Both ends of the move come from the graph. Working them out here instead was how the bot
        // ended up aiming at the point of a platform directly beneath it, deciding it had arrived,
        // and standing there.
        // A jump is aimed at its take-off mark; a walk-off is aimed past the edge it has to clear.
        needsJump = hop.isJump();
        steppingOff = !needsJump;
        waypointX = needsJump ? hop.getLaunchX() : hop.getWalkOffX();
        committedLandingX = hop.getLandingX();
        committedLandingTop = hop.getLandingTop();
        committed = true;
      } else if (committed && !bot.isOnGround()) {
        // Mid-jump there is no platform underneath to plan from, and re-deciding every tick would
        // have it change its mind in mid-air and land back where it took off. Aim at where it meant
        // to land, not at the edge it has already left.
        //
        // Except while still climbing: leaning across before the height is won walks the jump into
        // the underside of the very thing being climbed onto. Feet above the surface, then across -
        // the same order a person does it in.
        //
        // Only while rising, though. On the way down the same test is true again, and holding still
        // for it lets go of the controls mid-fall: over a gap that is not a jump, it is a drop.
        if (bot.getVelocityY() < 0 && bot.getBottom() > committedLandingTop) {
          return firing(moves, bot, target, dx, dy, distance, random);
        }
        waypointX = committedLandingX;
        needsJump = false;
      } else {
        // Same platform as the target, or nowhere to route from: just close on them.
        waypointX = target.getCenterX();
        needsJump = false;
        committed = false;
        // Standing above somebody is not standing next to them. Closing on a target by its x alone
        // counts a bot on a ledge three storeys up as having arrived, and a bot that has arrived
        // does nothing at all - which is how one that landed on the floating troll face came to
        // spend a round up there, directly over the player it was chasing.
        sameLevel = Math.abs(dy) <= bot.getHeight();
      }

      double toWaypoint = waypointX - bot.getCenterX();
      Direction towards = toWaypoint > 0 ? Direction.RIGHT : Direction.LEFT;
      // One tick of walking. A tighter tolerance than this and it jitters either side of the mark,
      // because a tick moves it further than the error it is trying to correct. A walk-off has no
      // mark to be near: it is done when the platform is behind it, and until then, whatever the
      // arithmetic says, it has not arrived.
      boolean arrived = !steppingOff && sameLevel && Math.abs(toWaypoint) <= SoldatPlayer.INIT_SPEED;
      if (!arrived) {
        moves.add(moveTowards(towards));
      } else if (needsJump) {
        // Standing on the mark with a gap to clear: it has to take off moving, or it jumps straight
        // up and lands exactly where it started.
        Direction across = directionTowards(bot, committedLandingX);
        if (across != null) {
          moves.add(moveTowards(across));
        }
      }

      // Jump when there is a reason to, rather than at random: it has arrived at the point it means
      // to jump from, something is in the way, or the target is directly overhead. The old code
      // rolled an 8% chance every tick, which both wasted jumps in the open and left the bot stuck
      // for as long as the dice said so.
      boolean atLaunchPoint = needsJump && arrived;
      boolean somethingInTheWay = !arrived && bot.isBlockedHorizontally();
      boolean targetOverhead = hop == null && dy < -bot.getHeight();
      if (bot.isOnGround() && (atLaunchPoint || somethingInTheWay || targetOverhead)) {
        moves.add(moveTowards(Direction.UP));
      }

      // Shoving only counts while it is actually being shoved into: clearing the obstacle, or
      // turning away from it, starts the count again.
      shovingTicks = somethingInTheWay ? shovingTicks + 1 : 0;
      if (shovingTicks >= SHOVING_TICKS_BEFORE_DETOUR) {
        // Jumping has had its chances and the thing has not moved. Anything in this arena can be
        // walked around, so go and look for the way.
        detourTicksLeft = DETOUR_TICKS;
        detourDirection = towards == Direction.RIGHT ? Direction.LEFT : Direction.RIGHT;
        shovingTicks = 0;
      }
    }

    return firing(moves, bot, target, dx, dy, distance, random);
  }

  /** Adds this tick's shot, if it has one worth taking, and hands back the finished list. */
  private List<SoldatMove> firing(List<SoldatMove> moves, SoldatPlayer bot, SoldatPlayer target,
                                  double dx, double dy, double distance, GameRandom random) {
    double aim = Math.toDegrees(Math.atan2(dy, dx));
    double facing = bot.getDirection() == Direction.LEFT ? 180 : 0;
    // Straight up and straight down are included. They are on the boundary of what the bot can aim
    // at, they are rare, and the one case that reaches them is a bot stuck on somebody's head with
    // nowhere to step down to - which without this is a bot that cannot shoot at all.
    if (distance <= FIRING_RANGE
      && Math.abs(MathUtils.normalizeDegrees(aim - facing)) <= 90
      && random.getRandomNumber(0, 100) < 35) {
      SoldatMove shot = new SoldatMove();
      shot.setFiredAngle(aim + random.getRandomNumber(-(int) AIM_TOLERANCE_DEGREES, (int) AIM_TOLERANCE_DEGREES));
      shot.setFired(new uu.game.main.game.common.ammo.Bullet());
      moves.add(shot);
    }
    return moves;
  }

  /**
   * Whether the bot has been in the same place long enough that whatever it is doing is not working.
   *
   * <p>Position, not intent: "it has pressed right for two seconds" is what a bot crossing the map
   * also looks like, and there is nothing wrong with that one. What separates them is whether the
   * pressing moves it, so this measures the ground covered over the window and nothing else - which
   * catches standing still, hopping on a spot, and shuffling between two pixels alike, without
   * having to recognise any of them.
   *
   * <p>A bot that is already on top of someone is exempt. Standing still at arm's length is a fight,
   * not a rut, and shaking it loose would only walk it away from the one thing it is there to do.
   */
  private boolean hasStopped(SoldatPlayer bot, SoldatPlayer target, double distanceToTarget) {
    boolean noProgress = noticeProgress(target, distanceToTarget);

    int slot = recorded % STALL_WINDOW_TICKS;
    recentX[slot] = bot.getCenterX();
    recentY[slot] = bot.getCenterY();
    recorded++;

    if (distanceToTarget <= ENGAGED_RANGE) {
      return false;
    }
    return noProgress || (recorded >= STALL_WINDOW_TICKS && !hasMoved());
  }

  /** Ground covered over the whole window - the fast tell, for a bot that is simply not moving. */
  private boolean hasMoved() {
    double minX = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE;
    double minY = Double.MAX_VALUE;
    double maxY = -Double.MAX_VALUE;
    for (int i = 0; i < STALL_WINDOW_TICKS; i++) {
      minX = Math.min(minX, recentX[i]);
      maxX = Math.max(maxX, recentX[i]);
      minY = Math.min(minY, recentY[i]);
      maxY = Math.max(maxY, recentY[i]);
    }
    return maxX - minX >= STALL_TRAVEL || maxY - minY >= STALL_TRAVEL;
  }

  /**
   * Keeps the record of when the bot was last getting closer.
   *
   * @return whether it has now gone {@link #NO_PROGRESS_TICKS} without getting closer to a target
   *     that was not itself running away
   */
  private boolean noticeProgress(SoldatPlayer target, double distanceToTarget) {
    boolean targetHasMoved = Math.hypot(target.getCenterX() - targetXWhenLastCloser,
      target.getCenterY() - targetYWhenLastCloser) > TARGET_MOVED;

    if (distanceToTarget < closestSoFar - PROGRESS_MARGIN || targetHasMoved) {
      // Either it is getting somewhere, or the question has changed under it. Both deserve the
      // benefit of the doubt, and the new distance is the one to beat from here.
      closestSoFar = distanceToTarget;
      targetXWhenLastCloser = target.getCenterX();
      targetYWhenLastCloser = target.getCenterY();
      ticksWithoutProgress = 0;
      return false;
    }
    ticksWithoutProgress++;
    return ticksWithoutProgress >= NO_PROGRESS_TICKS;
  }

  /**
   * Abandons the current idea and walks somewhere else for a moment.
   *
   * <p>Deliberately random rather than clever. Something it cannot see has it pinned, so there is
   * nothing to reason from; what matters is that the bot ends up somewhere it has not just been,
   * because from a different spot the ordinary routing gets a fresh question and usually a different
   * answer. The length varies for the same reason - a fixed one can walk into a wall, walk back, and
   * do it again forever in step with itself.
   *
   * @param random the match's randomness, so a shaken bot still replays with its seed
   */
  private void shakeLoose(GameRandom random) {
    detourTicksLeft = random.getRandomNumber(ESCAPE_MIN_TICKS, ESCAPE_MAX_TICKS);
    detourDirection = random.getRandomNumber(0, 2) == 0 ? Direction.LEFT : Direction.RIGHT;
    shovingTicks = 0;
    committed = false;
    // Otherwise the window it was judged on is still full of the rut it has just been pulled out of,
    // and it would be shaken loose again on each of the next forty ticks.
    forgetProgress();
  }

  /**
   * Whether one more stride that way takes the bot off what it is standing on with nothing below it.
   *
   * <p>Only asked of a bot that is walking somewhere nothing planned it: a step taken from the graph
   * always lands on a platform, and a bot in mid-air has already committed.
   *
   * <p>Answered from its own feet rather than from the platform under them, because sometimes there
   * is no platform under them - a bot on somebody's head is standing on a player, which is solid,
   * moves, and is in no graph.
   */
  private static boolean walkingIntoThePit(SoldatPlayer bot, SoldatNavigation navigation, Direction direction) {
    if (!bot.isOnGround() || direction == null) {
      return false;
    }
    // Where its leading edge will be next tick. Support goes when the body clears what holds it up,
    // so that edge is what has to still have something under it.
    double ahead = direction == Direction.RIGHT
      ? bot.getRight() + SoldatPlayer.INIT_SPEED : bot.getX() - SoldatPlayer.INIT_SPEED;
    SoldatNavigation.Platform standing = navigation.under(bot);
    if (standing != null && standing.spans(ahead)) {
      return false;
    }
    return !navigation.hasGroundBelow(ahead, bot.getBottom());
  }

  /**
   * Whether the bot is close enough to its target that the two are touching, one way or another.
   *
   * <p>Within a stride counts as touching: at walking pace the gap between "beside them" and "into
   * them" is crossed in a single tick, and a rule that only holds while they are flush would let go
   * of the bot every other tick.
   */
  private static boolean isInContactWith(SoldatPlayer bot, SoldatPlayer target) {
    if (standingOn(bot, target)) {
      return true;
    }
    boolean sideBySide = bot.getBottom() > target.getY() && bot.getY() < target.getBottom();
    double gap = Math.max(target.getX() - bot.getRight(), bot.getX() - target.getRight());
    return sideBySide && gap <= SoldatPlayer.INIT_SPEED;
  }

  /** Feet on their head: standing on a player rather than beside one. */
  private static boolean standingOn(SoldatPlayer bot, SoldatPlayer target) {
    return bot.isOnGround()
      && Math.abs(bot.getBottom() - target.getY()) <= 2
      && target.getRight() > bot.getX() && target.getX() < bot.getRight();
  }

  /**
   * The way off somebody's head: whichever side the bot is already nearer, unless that is a drop
   * into the pit, in which case the other one.
   *
   * @return the way to step, or null when both sides are a fall - better to stand on them and shoot
   *     downwards than to step off the map to get a cleaner angle
   */
  private static Direction offTheHeadOf(SoldatPlayer bot, SoldatPlayer target, SoldatNavigation navigation) {
    Direction nearer = bot.getCenterX() <= target.getCenterX() ? Direction.LEFT : Direction.RIGHT;
    if (!walkingIntoThePit(bot, navigation, nearer)) {
      return nearer;
    }
    Direction other = nearer == Direction.LEFT ? Direction.RIGHT : Direction.LEFT;
    return walkingIntoThePit(bot, navigation, other) ? null : other;
  }

  /** Which way to lean to get to {@code x}, or null when already there. */
  private static Direction directionTowards(SoldatPlayer bot, double x) {
    double delta = x - bot.getCenterX();
    if (Math.abs(delta) <= 1) {
      return null;
    }
    return delta > 0 ? Direction.RIGHT : Direction.LEFT;
  }

  private void forgetWhatItWasDoing() {
    shovingTicks = 0;
    detourTicksLeft = 0;
    detourDirection = null;
    committed = false;
    // It comes back somewhere else on the map, so the trail it left before dying says nothing about
    // whether the bot that respawns is getting anywhere.
    forgetProgress();
  }

  /** Wipes the record of where it has been and how close it got, leaving it judged from here on. */
  private void forgetProgress() {
    recorded = 0;
    closestSoFar = Double.MAX_VALUE;
    ticksWithoutProgress = 0;
  }

  private static SoldatMove moveTowards(Direction direction) {
    SoldatMove move = new SoldatMove();
    move.setMove(direction);
    return move;
  }

  private static SoldatPlayer nearestTarget(SoldatPlayer bot, Collection<SoldatPlayer> everyone) {
    SoldatPlayer best = null;
    double bestDistance = Double.MAX_VALUE;
    for (SoldatPlayer candidate : everyone) {
      if (candidate == bot || candidate.isDead()) {
        continue;
      }
      double distance = Math.hypot(candidate.getCenterX() - bot.getCenterX(), candidate.getCenterY() - bot.getCenterY());
      if (distance < bestDistance) {
        bestDistance = distance;
        best = candidate;
      }
    }
    return best;
  }
}
