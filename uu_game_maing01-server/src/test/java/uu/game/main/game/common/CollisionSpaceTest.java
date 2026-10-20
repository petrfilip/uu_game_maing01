package uu.game.main.game.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class CollisionSpaceTest {

  private static final double EPSILON = 1e-9;

  private static GameRectangle box(double x, double y, int width, int height) {
    return new GameRectangle(x, y, width, height);
  }

  @Test
  public void moversLayeredOnASpaceAreNotVisibleToAnybodyElse() {
    GameRectangle wall = box(100, 0, 2, 200);
    CollisionSpace level = new CollisionSpace().add(wall);
    GameRectangle player = box(10, 10, 10, 10);

    CollisionSpace mine = level.withMovers(Collections.singletonList(player));

    assertTrue("the mover is missing from the space it was layered on", mine.collides(box(12, 12, 4, 4)));
    // The level is built once and shared by every entity in the match, so one player's view of the
    // world must not become part of everybody's. Copying it per entity was what this replaces.
    assertFalse("a mover leaked into the shared level", level.collides(box(12, 12, 4, 4)));
  }

  @Test
  public void aSpaceThatSharesItsLevelRefusesToBeAddedTo() {
    CollisionSpace level = new CollisionSpace().add(box(100, 0, 2, 200));
    CollisionSpace mine = level.withMovers(Collections.<GameRectangle>emptyList());

    try {
      mine.add(box(0, 0, 5, 5));
      throw new AssertionError("a shared space let itself be written to, which writes through to "
        + "the geometry every other entity is using");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("withMovers"));
    }
  }

  @Test
  public void aWallIsReportedBeforeAMoverWhenBothAreHit() {
    GameRectangle wall = box(10, 10, 20, 20);
    GameRectangle mover = box(10, 10, 20, 20);
    CollisionSpace space = new CollisionSpace().add(wall).withMovers(Arrays.asList(mover));

    // Level first, then movers - the order blockers were added in before the two were separated.
    // What an entity stops flush against decides where it ends up, so the order is behaviour.
    assertSame(wall, space.firstHit(box(15, 15, 2, 2)));
  }

  @Test
  public void fastMoverDoesNotTunnelThroughAThinWall() {
    GameRectangle wall = box(100, 0, 2, 200);
    CollisionSpace space = new CollisionSpace().add(wall);
    GameRectangle mover = box(0, 50, 10, 10);

    // 200 px in one tick is more than 30 times the wall thickness; a naive "move then test" would
    // put the mover past it without ever overlapping.
    double travelled = space.moveX(mover, 200);

    assertEquals(90, mover.getX(), EPSILON);
    assertEquals(90, travelled, EPSILON);
  }

  @Test
  public void moveRightStopsFlushAgainstTheBlockersLeftEdge() {
    GameRectangle wall = box(100, 0, 20, 200);
    CollisionSpace space = new CollisionSpace().add(wall);
    GameRectangle mover = box(0, 50, 10, 10);

    space.moveX(mover, 500);

    assertEquals(wall.getX(), mover.getRight(), EPSILON);
  }

  @Test
  public void moveLeftStopsFlushAgainstTheBlockersRightEdge() {
    GameRectangle wall = box(100, 0, 20, 200);
    CollisionSpace space = new CollisionSpace().add(wall);
    GameRectangle mover = box(300, 50, 10, 10);

    space.moveX(mover, -500);

    assertEquals(wall.getRight(), mover.getX(), EPSILON);
  }

  @Test
  public void moveDownStopsFlushOnTopOfTheBlocker() {
    GameRectangle floor = box(0, 400, 500, 20);
    CollisionSpace space = new CollisionSpace().add(floor);
    GameRectangle mover = box(10, 0, 10, 10);

    space.moveY(mover, 900);

    assertEquals(floor.getY(), mover.getBottom(), EPSILON);
  }

  @Test
  public void moveUpStopsFlushUnderTheBlocker() {
    GameRectangle ceiling = box(0, 100, 500, 20);
    CollisionSpace space = new CollisionSpace().add(ceiling);
    GameRectangle mover = box(10, 400, 10, 10);

    space.moveY(mover, -900);

    assertEquals(ceiling.getBottom(), mover.getY(), EPSILON);
  }

  @Test
  public void anUnblockedMoveTravelsTheFullRequestedDistance() {
    CollisionSpace space = new CollisionSpace().add(box(1000, 1000, 10, 10));
    GameRectangle mover = box(0, 0, 10, 10);

    // Deliberately not a multiple of the internal step size, so a rounding error in the sub-stepping
    // would show up here.
    assertEquals(37.5, space.moveX(mover, 37.5), EPSILON);
    assertEquals(37.5, mover.getX(), EPSILON);
    assertEquals(-13.25, space.moveY(mover, -13.25), EPSILON);
    assertEquals(-13.25, mover.getY(), EPSILON);
  }

  @Test
  public void anEntityDoesNotCollideWithItself() {
    GameRectangle mover = box(0, 0, 10, 10);
    // The board hands the mover its own collision space, which contains every player including the
    // one being moved.
    CollisionSpace space = new CollisionSpace().add(mover);

    assertFalse(space.collides(mover));
    assertEquals(50, space.moveX(mover, 50), EPSILON);
    assertEquals(50, mover.getX(), EPSILON);
  }

  @Test
  public void aBoxTouchingEdgeToEdgeDoesNotCountAsOverlapping() {
    CollisionSpace space = new CollisionSpace().add(box(100, 0, 20, 20));

    assertFalse(space.collides(box(80, 0, 20, 20)));
    assertTrue(space.collides(box(81, 0, 20, 20)));
  }
}
