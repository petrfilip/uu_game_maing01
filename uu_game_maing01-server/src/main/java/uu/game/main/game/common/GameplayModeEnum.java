package uu.game.main.game.common;

public enum GameplayModeEnum {
  /** First player to reach the score limit wins. */
  SCORE_ACHIEVED,
  /** Highest score when the clock runs out wins. */
  TIME_LIMIT;

  /**
   * Lenient lookup for a value coming from client supplied game parameters.
   *
   * <p>The enum used to also declare {@code LAST_STANDING_MAN}, which no code implemented - asking
   * for it silently started a score-limited match instead.
   */
  public static GameplayModeEnum find(String name, GameplayModeEnum defaultValue) {
    if (name == null) {
      return defaultValue;
    }
    for (GameplayModeEnum value : values()) {
      if (value.name().equalsIgnoreCase(name)) {
        return value;
      }
    }
    return defaultValue;
  }
}
