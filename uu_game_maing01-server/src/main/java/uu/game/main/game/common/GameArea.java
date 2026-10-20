package uu.game.main.game.common;

import java.util.Map;

/**
 * Playfield bounds. Replaces the 1400x600 literals that were duplicated in
 * {@code SoldatPlayer}, {@code Projectile} and {@code BonusItem} - changing the map size used to
 * mean changing three unrelated classes, and they silently drifted apart from the
 * {@code areaWidth}/{@code areaHeight} game parameters that were already being published to the
 * client.
 */
public class GameArea {

  public static final String PARAM_WIDTH = "areaWidth";
  public static final String PARAM_HEIGHT = "areaHeight";

  private final int width;
  private final int height;

  public GameArea(int width, int height) {
    this.width = width;
    this.height = height;
  }

  /**
   * Reads the area out of the game parameters, defaulting when the caller did not specify them.
   * Parameters arrive from JSON, so an {@code Integer} may turn up as any {@link Number}.
   */
  public static GameArea fromParams(Map<String, Object> params, int defaultWidth, int defaultHeight) {
    return new GameArea(intParam(params, PARAM_WIDTH, defaultWidth), intParam(params, PARAM_HEIGHT, defaultHeight));
  }

  private static int intParam(Map<String, Object> params, String key, int defaultValue) {
    Object value = params == null ? null : params.get(key);
    return value instanceof Number ? ((Number) value).intValue() : defaultValue;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  /**
   * @return {@code true} when the box has left the playfield entirely or fallen out of the bottom.
   */
  public boolean isOutOfBounds(GameRectangle box) {
    return box.getRight() < 0
      || box.getX() > width
      || box.getBottom() < 0
      || box.getY() > height;
  }

  /**
   * @return {@code true} when the box has fallen below the playfield - the pit that kills players.
   */
  public boolean isBelow(GameRectangle box) {
    return box.getY() > height;
  }

  public double clampX(double x, int boxWidth) {
    return Math.max(0, Math.min(x, width - (double) boxWidth));
  }
}
