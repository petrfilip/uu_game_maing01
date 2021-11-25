package uu.game.main.helper;

public class MathUtils {

  private MathUtils(){}

  public static double sin(double angleDegrees) {
    return Math.sin(angleDegrees * Math.PI / 180);
  }

  public static double cos(double angleDegrees) {
    return Math.cos(angleDegrees * Math.PI / 180);
  }

  public static int roundUp(double toRound) {
    return (int) ((toRound >= 0 ? 1 : -1) * Math.round(Math.abs(toRound)));
  }

}
