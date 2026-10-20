package uu.game.main.game.soldat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import uu.game.main.game.common.CollisionSpace;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.Obstacle;

/**
 * Where you can get to from where, for anything that has to cross the level on foot.
 *
 * <h2>Why a graph of platforms and not a grid</h2>
 * The obvious shape for this is A* over a grid of the arena, but that is the wrong unit twice over.
 * A player cannot stop in mid-air, so most grid cells are not places they can be; and the arena is
 * 1400x600, which at any useful resolution is a thousand-odd cells to search. The surfaces a player
 * can actually stand on number about twenty. A breadth-first search over twenty nodes is free, and
 * the answer it gives - "get onto that terrace, then that one" - is the decision being made.
 *
 * <h2>Why it is built once</h2>
 * The level cannot change after {@code init}, which the collision space already relies on. So the
 * graph is built with it, not per tick: a tick only looks up which platform something is standing on
 * and walks an already-computed route. Nothing here allocates per tick, which matters more than the
 * arithmetic does - allocation, not processor time, was the bottleneck this simulation actually had.
 *
 * <h2>Edges are flown before they are believed</h2>
 * An edge means "making this move from here gets you somewhere else". Heights and gaps alone cannot
 * say that, because they say nothing about what stands in between, and on this level that is often a
 * crate against the edge or a ledge low enough to bang your head on. So each candidate is flown once
 * at build time against the real collision geometry, and one that ends back where it started is not
 * an edge - see {@code carriesYouThere}.
 *
 * <p>The cost of the two mistakes is not symmetric, which is why the test is where it is: a missing
 * edge sends the bot the long way round, while an edge that is not really there sends it into a wall
 * to jump at it forever. The bot keeps its own escape for the second case, but it should not need
 * it.
 */
final class SoldatNavigation {

  /** A run of touching surfaces at one height - one terrace, one crate, the floor. */
  static final class Platform {
    private final double top;
    private final double left;
    private double right;

    private Platform(double top, double left, double right) {
      this.top = top;
      this.left = left;
      this.right = right;
    }

    double getTop() {
      return top;
    }

    double getLeft() {
      return left;
    }

    double getRight() {
      return right;
    }

    boolean spans(double x) {
      return x >= left && x <= right;
    }
  }

  /**
   * Height a standing jump gains, measured by stepping the same arithmetic {@link SoldatPlayer} uses
   * rather than by the textbook formula.
   *
   * <p>The formula overstates it. The simulation adds gravity before the first movement, so take-off
   * rises by {@code JUMP_VELOCITY - GRAVITY}, and the error is about ten pixels - which happens to be
   * the difference between a level that can be climbed and one that cannot.
   */
  static double jumpHeight() {
    double velocity = -SoldatPlayer.JUMP_VELOCITY;
    double travelled = 0;
    double apex = 0;
    for (int i = 0; i < 100; i++) {
      velocity = Math.min(velocity + SoldatPlayer.GRAVITY, SoldatPlayer.MAX_FALL_SPEED);
      travelled += velocity;
      apex = Math.min(apex, travelled);
      if (velocity > 0) {
        break;
      }
    }
    return -apex;
  }

  /**
   * How far sideways a jump may carry someone between two platforms.
   *
   * <p>A jump is airborne for roughly {@code 2 * JUMP_VELOCITY / GRAVITY} ticks and covers speed
   * pixels in each, but half of that is spent coming back down onto the same height. Half the span
   * is the conservative reading, and this is a gap between platform edges, not a distance to cover
   * from a standing start.
   */
  private static double jumpSpan() {
    double airborneTicks = 2 * SoldatPlayer.JUMP_VELOCITY / SoldatPlayer.GRAVITY;
    return airborneTicks * SoldatPlayer.INIT_SPEED / 2;
  }

  private final List<Platform> platforms;
  /** platforms.get(i) leads to each Step in steps.get(i). */
  private final List<List<Step>> steps;

  private SoldatNavigation(List<Platform> platforms, List<List<Step>> steps) {
    this.platforms = platforms;
    this.steps = steps;
  }

  static SoldatNavigation of(List<Obstacle> obstacles) {
    List<Platform> platforms = walkableSurfaces(obstacles);
    // The same geometry a player collides with, so "can this jump be made" is answered by the
    // physics rather than by a second opinion about it.
    CollisionSpace level = new CollisionSpace();
    for (Obstacle obstacle : obstacles) {
      level.addAll(obstacle.getWalls());
    }
    return new SoldatNavigation(platforms, buildSteps(platforms, level));
  }

  /**
   * Merges the level's tiles into the surfaces a player experiences.
   *
   * <p>Tiles that touch at the same height are one platform: a terrace is stepped onto once and
   * walked along from there, so treating each 25 px of it as its own node would both bloat the graph
   * and invent decisions nobody makes.
   *
   * <p>Every solid counts, the floating troll face included. It is decoration, but it is decoration
   * you land on, and leaving it out of the graph did not stop the bot getting up there - it only
   * stopped it knowing how to get down again, which it did by standing on the thing until the round
   * ended.
   */
  private static List<Platform> walkableSurfaces(List<Obstacle> obstacles) {
    List<GameRectangle> solids = new ArrayList<>();
    for (Obstacle obstacle : obstacles) {
      solids.addAll(obstacle.getWalls());
    }

    List<Platform> tiles = new ArrayList<>();
    for (GameRectangle wall : solids) {
      tiles.add(new Platform(wall.getY(), wall.getX(), wall.getRight()));
    }
    tiles.sort((a, b) -> a.top != b.top ? Double.compare(a.top, b.top) : Double.compare(a.left, b.left));

    List<Platform> merged = new ArrayList<>();
    for (Platform tile : tiles) {
      Platform last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
      if (last != null && last.top == tile.top && tile.left <= last.right) {
        last.right = Math.max(last.right, tile.right);
      } else {
        merged.add(new Platform(tile.top, tile.left, tile.right));
      }
    }
    // No floor is added, because the arena has none: below the last platform is the pit that kills
    // you - see GameArea.isBelow, and findSpawnPoint, which goes out of its way not to drop anyone
    // over it. Inventing one here told the bot the bottom of the screen was somewhere to walk, and
    // it obligingly walked off the map.
    return withHeadroom(merged, solids);
  }

  /**
   * Keeps only the parts of each surface something could actually stand on.
   *
   * <p>A stack of crates has a top face for every crate in it, but all except the highest are the
   * inside of a solid column - there is no room above them for anyone to be. Taken at face value
   * they become platforms that can be routed to and never reached, and the route ends at a wall.
   *
   * <p>Where a surface is only partly built over, the free parts of it survive as platforms of their
   * own: half a terrace under an overhang is still half a terrace.
   */
  private static List<Platform> withHeadroom(List<Platform> candidates, List<GameRectangle> solids) {
    List<Platform> standable = new ArrayList<>();
    for (Platform candidate : candidates) {
      List<double[]> free = new ArrayList<>();
      free.add(new double[] {candidate.left, candidate.right});

      for (GameRectangle solid : solids) {
        // Occupying the head height directly above this surface, rather than resting on it.
        if (solid.getBottom() <= candidate.top - SoldatPlayer.SIZE || solid.getY() >= candidate.top) {
          continue;
        }
        List<double[]> remaining = new ArrayList<>();
        for (double[] span : free) {
          if (solid.getRight() <= span[0] || solid.getX() >= span[1]) {
            remaining.add(span);
            continue;
          }
          if (solid.getX() > span[0]) {
            remaining.add(new double[] {span[0], solid.getX()});
          }
          if (solid.getRight() < span[1]) {
            remaining.add(new double[] {solid.getRight(), span[1]});
          }
        }
        free = remaining;
      }

      for (double[] span : free) {
        // Narrower than a body is nowhere to stand.
        if (span[1] - span[0] >= SoldatPlayer.SIZE) {
          standable.add(new Platform(candidate.top, span[0], span[1]));
        }
      }
    }
    return standable;
  }

  private static List<List<Step>> buildSteps(List<Platform> platforms, CollisionSpace level) {
    double climb = jumpHeight();
    double span = jumpSpan();
    List<List<Step>> all = new ArrayList<>(platforms.size());

    for (int from = 0; from < platforms.size(); from++) {
      Platform a = platforms.get(from);
      List<Step> row = new ArrayList<>();
      for (int to = 0; to < platforms.size(); to++) {
        if (from == to) {
          continue;
        }
        Platform b = platforms.get(to);
        Step step = stepBetween(to, a, b, climb, span);
        if (step != null && carriesYouThere(level, a, b, step)) {
          row.add(step);
        }
      }
      all.add(row);
    }
    return all;
  }

  /**
   * Whether making this move actually puts you on the platform it promises.
   *
   * <p>Everything above this works in heights and gaps, which is enough to say that two surfaces are
   * within a jump of each other and nothing at all about what stands between them. On this level
   * that gap is often not empty: a crate against the edge, a ledge overhead low enough to bang your
   * head on halfway up. The arithmetic passes the step, the jump does not, and the bot is left
   * running at the edge and hopping into the wall behind it for the rest of the round - which is
   * exactly the "edge that is not really there" this class set out not to have.
   *
   * <p>So the move is flown, once, at build time, against the same collision geometry a player moves
   * through and with the same arithmetic {@link SoldatPlayer} steps with - including the bot's own
   * habit of holding still while it climbs. What gets somewhere is an edge; what bangs its head and
   * lands back where it started is not.
   *
   * <p>Deliberately no stricter than that. Demanding that the flight end on the named platform reads
   * every near miss as a wall - a drop that clips the terrace below on the way, a jump that lands a
   * crate short - and those are not failures: the mover re-reads the graph from wherever it comes
   * down and carries on from there. Cutting them turned a connected level into islands. The one
   * thing it cannot do is nothing, so that is the one thing rejected here.
   */
  private static boolean carriesYouThere(CollisionSpace level, Platform from, Platform to, Step step) {
    GameRectangle body = new GameRectangle(
      step.launchX - SoldatPlayer.SIZE / 2.0, from.top - SoldatPlayer.SIZE, SoldatPlayer.SIZE, SoldatPlayer.SIZE);
    if (level.collides(body)) {
      // Nobody can stand at the take-off mark in the first place.
      return false;
    }

    // Standing, as the bot is on the tick it sets off: the jump's impulse belongs to the first step
    // below, not to the state the decision is made in.
    double velocityY = 0;
    boolean jumping = step.jump;

    for (int tick = 0; tick < FLIGHT_TICKS; tick++) {
      // Aimed where the bot aims: at the edge while it is still walking off one, at the landing once
      // it is in the air. Pushing blindly in one direction instead sails it straight over the
      // platform it was supposed to come down on.
      boolean airborne = hasLeft(from, body);
      double waypoint = step.jump || airborne ? step.landingX : step.walkOffX;
      double toWaypoint = waypoint - body.getCenterX();
      // The climber holds still until its feet are above the surface it is climbing onto; leaning
      // across any earlier walks the jump into the underside of it. The bot does this, so the check
      // has to as well, or it rejects the jumps the bot makes and passes the ones it cannot.
      boolean stillClimbing = velocityY < 0 && body.getBottom() > to.top;
      // A walk-off keeps walking until the floor runs out; everything else stops a stride short of
      // its mark, the way the bot does, rather than jittering either side of it.
      boolean leavingOnFoot = !airborne && !step.jump;
      boolean steering = leavingOnFoot || Math.abs(toWaypoint) > SoldatPlayer.INIT_SPEED;
      if (steering && !stillClimbing && Math.abs(toWaypoint) > 1) {
        level.moveX(body, Math.signum(toWaypoint) * SoldatPlayer.INIT_SPEED);
      }

      if (jumping) {
        velocityY = -SoldatPlayer.JUMP_VELOCITY;
        jumping = false;
      }
      velocityY = Math.min(velocityY + SoldatPlayer.GRAVITY, SoldatPlayer.MAX_FALL_SPEED);
      double travelled = level.moveY(body, velocityY);

      if (Math.abs(travelled - velocityY) > 1e-9) {
        if (velocityY < 0) {
          // Bumped its head. The rest of the jump is a fall from wherever that happened, which is
          // not the move being checked.
          velocityY = 0;
          continue;
        }
        if (!hasLeft(from, body)) {
          // Still held up by the platform it is walking off: a body is supported by the last pixel
          // of overlap, so the first steps of a walk-off are caught here, and they are the walk, not
          // the end of it.
          velocityY = 0;
          continue;
        }
        // Down somewhere else. Not necessarily the platform the step named - a jump can clip a crate
        // on the way and come down on that - and that is fine: the mover re-reads the graph from
        // wherever it lands and carries on. What must not survive is the move that ends where it
        // began, because nothing about the next tick will differ and the bot will make it again, and
        // again, until the round is over.
        return true;
      }
      if (body.getY() > from.top + FALL_GIVE_UP) {
        // Past anything it could have been aiming at, and on its way to the pit.
        return false;
      }
    }
    return false;
  }

  /**
   * A way from one platform to another, including where to stand and where it puts you.
   *
   * <p>The graph carries these rather than leaving them to the caller, because whether a move is
   * possible and where it has to be made from are the same question. "That terrace is within a jump"
   * is true of a spot directly underneath it, and jumping from there only bangs your head on its
   * underside.
   */
  static final class Step {
    private final int to;
    private final double launchX;
    private final double walkOffX;
    private final double landingX;
    private final double landingTop;
    private final boolean jump;

    private Step(int to, double launchX, double walkOffX, double landingX, double landingTop, boolean jump) {
      this.to = to;
      this.launchX = launchX;
      this.walkOffX = walkOffX;
      this.landingX = landingX;
      this.landingTop = landingTop;
      this.jump = jump;
    }

    /**
     * Height of the surface being aimed at.
     *
     * <p>Carried so a jumper knows when it has won the height: below this it is still climbing and
     * must not drift, above it the way across is clear.
     */
    double getLandingTop() {
      return landingTop;
    }

    /** Where on the platform being left from to stand before moving. */
    double getLaunchX() {
      return launchX;
    }

    /** Where on the platform being moved to this aims to put you down. */
    double getLandingX() {
      return landingX;
    }

    /**
     * Where to walk to in order to leave this platform, for a step that is taken by walking off it.
     *
     * <p>Distinct from the landing, and that distinction is what stops a walker in its tracks when
     * the two are confused. A landing is a point on the platform below, and where the drop is a
     * short one, that point can be within a stride of where the walker already stands - so it
     * decides it has arrived, on the terrace above, and stands on the brink for the rest of the
     * round. It has not arrived; it has not even set off. Leaving is not reaching a mark, it is
     * clearing an edge, so it is aimed past the edge and only ends when the ground goes away.
     */
    double getWalkOffX() {
      return walkOffX;
    }

    /** Whether it takes a jump, as opposed to walking off an edge. */
    boolean isJump() {
      return jump;
    }
  }

  /** Room left either side of a landing so a jump aims at a platform, not at its corner. */
  private static final double LANDING_INSET = SoldatPlayer.SIZE;

  /**
   * How long a move is flown before it is written off - three seconds, several times any jump in
   * this arena, so only something that is going nowhere runs out.
   */
  private static final int FLIGHT_TICKS = 60;

  /** How far below its take-off a fall has to get before it is clearly not a move to anywhere. */
  private static final double FALL_GIVE_UP = 400;

  private static Step stepBetween(int toIndex, Platform from, Platform to, double climb, double span) {
    // Horizontal gap between the two, zero when they overlap.
    double gap = Math.max(0, Math.max(from.left - to.right, to.left - from.right));
    if (gap > span) {
      return null;
    }
    double rise = from.top - to.top;

    if (rise <= 0) {
      // Going down. Gravity needs no convincing, but you have to be able to get off the edge: a
      // platform directly below and no wider than this one is under an overhang, not below a drop.
      boolean stepOffLeft = to.left < from.left;
      boolean stepOffRight = to.right > from.right;
      if (gap == 0 && !stepOffLeft && !stepOffRight) {
        return null;
      }
      boolean goRight = to.left > from.right || (to.right >= from.left && stepOffRight);
      double launchX = goRight ? from.right : from.left;
      // Past the edge, not level with it. Aiming at the edge itself leaves the walker standing on
      // the brink deciding it has arrived, which is true and useless - it never steps off.
      //
      // Brought onto the destination rather than merely past the edge left behind: with a gap
      // between the two, a step beyond this platform is a step into the pit, and that is precisely
      // where something aiming at it walks.
      double beyondTheEdge = goRight ? from.right + SoldatPlayer.SIZE : from.left - SoldatPlayer.SIZE;
      return new Step(toIndex, launchX, beyondTheEdge, inset(to, beyondTheEdge), to.top, gap > 0);
    }

    if (rise > climb) {
      return null;
    }

    // Climbing. The take-off has to be clear of what is being climbed onto, or the rise is spent on
    // its underside. Standing beside it is enough, because the climb is made straight up and the
    // drift across only happens once the height is won - which is how a person plays it, and what
    // lets a 50 px crate be a launch pad at all. Demanding a full sideways run-up instead cut the
    // level to pieces: nothing narrow could be jumped from.
    double besideLeft = to.left - SoldatPlayer.SIZE;
    double besideRight = to.right + SoldatPlayer.SIZE;
    Double launchX = null;
    if (from.spans(besideLeft)) {
      launchX = besideLeft;
    } else if (from.spans(besideRight)) {
      launchX = besideRight;
    } else if (gap > 0) {
      // No overlap at all: the near edge is already clear of it.
      launchX = to.left > from.right ? from.right : from.left;
    }
    if (launchX == null) {
      return null;
    }
    return new Step(toIndex, launchX, launchX, inset(to, launchX), to.top, true);
  }

  /** The point on {@code platform} nearest {@code x}, kept clear of its corners where it can be. */
  private static double inset(Platform platform, double x) {
    double room = Math.min(LANDING_INSET, (platform.right - platform.left) / 2);
    return Math.max(platform.left + room, Math.min(platform.right - room, x));
  }

  /** The platform something is standing on, or null when it is in the air or off the level. */
  Platform under(GameRectangle body) {
    Platform best = null;
    double bestDistance = Double.MAX_VALUE;
    double feet = body.getBottom();
    double centre = body.getCenterX();
    for (Platform platform : platforms) {
      // Feet within a pixel of the surface, allowing for the resting position collision leaves them
      // in, and horizontally over it.
      double distance = platform.top - feet;
      if (distance < -1 || distance > 2) {
        continue;
      }
      if (!platform.spans(centre) && !overlapsBody(platform, body)) {
        continue;
      }
      if (distance < bestDistance) {
        bestDistance = distance;
        best = platform;
      }
    }
    return best;
  }

  private static boolean overlapsBody(Platform platform, GameRectangle body) {
    return platform.right > body.getX() && platform.left < body.getRight();
  }

  /**
   * Whether anything below {@code top} would catch someone who walked off at {@code x}.
   *
   * <p>The arena has no floor - under the lowest platform is the pit that kills you - so "keep
   * walking that way" is not always survivable advice, and whatever is wandering has to be able to
   * ask before it takes the step. A route through the graph never needs this because a step is only
   * ever planned onto a platform; anything improvising does.
   */
  boolean hasGroundBelow(double x, double top) {
    for (Platform platform : platforms) {
      if (platform.top > top && platform.spans(x)) {
        return true;
      }
    }
    return false;
  }

  /**
   * First step of the way from one platform to another, or null when there is no route or none is
   * needed.
   *
   * <p>Only the next hop is returned because that is all a tick can act on, and recomputing it is
   * cheaper than keeping a route that the target has already invalidated by moving.
   */
  Step nextStepFrom(Platform from, Platform to) {
    if (from == null || to == null || from == to) {
      return null;
    }
    int start = platforms.indexOf(from);
    int goal = platforms.indexOf(to);
    if (start < 0 || goal < 0) {
      return null;
    }

    int[] cameFrom = new int[platforms.size()];
    Arrays.fill(cameFrom, -1);
    cameFrom[start] = start;

    Deque<Integer> queue = new ArrayDeque<>();
    queue.add(start);
    while (!queue.isEmpty()) {
      int current = queue.poll();
      if (current == goal) {
        return firstHop(cameFrom, start, goal);
      }
      for (Step next : steps.get(current)) {
        if (cameFrom[next.to] == -1) {
          cameFrom[next.to] = current;
          queue.add(next.to);
        }
      }
    }
    return null;
  }

  /** Walks the search back to the step taken from {@code start}. */
  private Step firstHop(int[] cameFrom, int start, int goal) {
    int step = goal;
    while (cameFrom[step] != start) {
      step = cameFrom[step];
    }
    for (Step candidate : steps.get(start)) {
      if (candidate.to == step) {
        return candidate;
      }
    }
    return null;
  }

  /**
   * Whether the body has left the platform it set off from - in the air, or already on something
   * else.
   *
   * <p>Being clear of it horizontally is not the question, and asking that instead read a landing on
   * the terrace above a wide floor as "still walking off the floor": the two overlap in x for the
   * whole flight. Standing on it means feet on its surface and over it, which is what support is.
   */
  private static boolean hasLeft(Platform from, GameRectangle body) {
    return Math.abs(body.getBottom() - from.top) > 2 || !overlapsBody(from, body);
  }

  /** The platform a step leads to. */
  Platform platformOf(Step step) {
    return platforms.get(step.to);
  }

  List<Platform> platforms() {
    return Collections.unmodifiableList(platforms);
  }
}
