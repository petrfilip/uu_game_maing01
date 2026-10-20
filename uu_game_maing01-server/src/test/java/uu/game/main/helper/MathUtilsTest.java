package uu.game.main.helper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MathUtilsTest {

  @Test
  public void normalizesAngleAcrossTheWrapAround() {
    assertEquals(-170, MathUtils.normalizeDegrees(190), 1e-9);
    assertEquals(10, MathUtils.normalizeDegrees(370), 1e-9);
    assertEquals(180, MathUtils.normalizeDegrees(180), 1e-9);
  }

  @Test
  public void normalizesNegativeAnglesIntoTheSameHalfOpenRange() {
    // -180 and 180 are the same direction; the range is (-180, 180] so both must come out as 180.
    assertEquals(180, MathUtils.normalizeDegrees(-180), 1e-9);
    assertEquals(-90, MathUtils.normalizeDegrees(-450), 1e-9);
    assertEquals(170, MathUtils.normalizeDegrees(-190), 1e-9);
  }

  @Test
  public void trigTakesDegreesNotRadians() {
    assertEquals(0, MathUtils.sin(180), 1e-9);
    assertEquals(-1, MathUtils.cos(180), 1e-9);
    assertEquals(1, MathUtils.sin(90), 1e-9);
  }

  @Test
  public void roundUpMovesAwayFromZeroInBothDirections() {
    assertEquals(3, MathUtils.roundUp(2.1));
    assertEquals(-3, MathUtils.roundUp(-2.1));
    assertEquals(2, MathUtils.roundUp(2.0));
  }
}
