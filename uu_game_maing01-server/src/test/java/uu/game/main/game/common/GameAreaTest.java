package uu.game.main.game.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class GameAreaTest {

  private static Map<String, Object> params(Object width, Object height) {
    Map<String, Object> params = new HashMap<>();
    params.put(GameArea.PARAM_WIDTH, width);
    params.put(GameArea.PARAM_HEIGHT, height);
    return params;
  }

  /**
   * Game parameters come straight off the wire, so the same value may arrive as any of these
   * depending on how the client encoded it. A bare cast used to throw ClassCastException.
   */
  @Test
  public void readsTheAreaWhateverNumericTypeJsonProduced() {
    assertEquals(800, GameArea.fromParams(params(800, 600), 1, 1).getWidth());
    assertEquals(800, GameArea.fromParams(params(800L, 600L), 1, 1).getWidth());
    assertEquals(800, GameArea.fromParams(params(800.0d, 600.0d), 1, 1).getWidth());
    assertEquals(600, GameArea.fromParams(params(800.0d, 600.0d), 1, 1).getHeight());
  }

  @Test
  public void fallsBackToTheDefaultWhenTheParameterIsMissingOrNotANumber() {
    assertEquals(1400, GameArea.fromParams(new HashMap<String, Object>(), 1400, 600).getWidth());
    assertEquals(600, GameArea.fromParams(null, 1400, 600).getHeight());
    assertEquals(1400, GameArea.fromParams(params("wide", null), 1400, 600).getWidth());
  }

  @Test
  public void reportsOutOfBoundsOnlyOnceTheBoxHasFullyLeftThePlayfield() {
    GameArea area = new GameArea(100, 100);

    // Still partly visible on the left, then completely past it.
    assertFalse(area.isOutOfBounds(new GameRectangle(-10, 10, 10, 10)));
    assertTrue(area.isOutOfBounds(new GameRectangle(-11, 10, 10, 10)));
    // Same on the right hand edge.
    assertFalse(area.isOutOfBounds(new GameRectangle(100, 10, 10, 10)));
    assertTrue(area.isOutOfBounds(new GameRectangle(101, 10, 10, 10)));
  }

  @Test
  public void isBelowOnlyReactsToTheBottomEdgeNotToTheSides() {
    GameArea area = new GameArea(100, 100);

    assertFalse(area.isBelow(new GameRectangle(-500, 50, 10, 10)));
    assertFalse(area.isBelow(new GameRectangle(10, 100, 10, 10)));
    assertTrue(area.isBelow(new GameRectangle(10, 101, 10, 10)));
  }

  @Test
  public void clampXKeepsTheWholeBoxInsideTheArena() {
    GameArea area = new GameArea(100, 100);

    assertEquals(0, area.clampX(-40, 10), 1e-9);
    assertEquals(90, area.clampX(500, 10), 1e-9);
    assertEquals(42, area.clampX(42, 10), 1e-9);
  }
}
