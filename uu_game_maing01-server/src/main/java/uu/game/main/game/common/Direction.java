package uu.game.main.game.common;

public enum Direction {
  UP(-90),
  DOWN(90),
  LEFT(180),
  RIGHT(0);

  private final int angle;

  Direction(int angle) {
    this.angle = angle;
  }

  public int getAngle() {
    return angle;
  }
}
