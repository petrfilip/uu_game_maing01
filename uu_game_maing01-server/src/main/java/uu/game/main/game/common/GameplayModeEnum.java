package uu.game.main.game.common;

public enum GameplayModeEnum {
  SCORE_ACHIEVED,
  LAST_STANDING_MAN,
  TIME_LIMIT;


  public static GameplayModeEnum find(String find, GameplayModeEnum defaultValue) {
    if (find == null) {
      return defaultValue;
    }

    for (GameplayModeEnum v : values()) {
      if (v.name().equals(find)) {
        return v;
      }
    }
    return defaultValue;
  }

}
