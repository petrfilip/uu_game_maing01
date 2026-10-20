package uu.game.main.helper;

public final class MathUtils {

  private MathUtils() {
  }

  public static double sin(double angleDegrees) {
    return Math.sin(Math.toRadians(angleDegrees));
  }

  public static double cos(double angleDegrees) {
    return Math.cos(Math.toRadians(angleDegrees));
  }

  public static int roundUp(double toRound) {
    return (int) ((toRound >= 0 ? 1 : -1) * Math.ceil(Math.abs(toRound)));
  }

  /**
   * Folds an angle into (-180, 180], so comparisons near the wrap-around behave.
   */
  public static double normalizeDegrees(double degrees) {
    double normalized = degrees % 360;
    if (normalized > 180) {
      normalized -= 360;
    } else if (normalized <= -180) {
      normalized += 360;
    }
    return normalized;
  }
}
