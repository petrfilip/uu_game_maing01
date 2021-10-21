package uu.game.main.helper;

import java.util.Random;

public class Utils {

  private Utils() {
  }

  private static final Random random = new Random();

  public static <T extends Enum<?>> T randomEnum(Class<T> clazz) {
    int x = random.nextInt(clazz.getEnumConstants().length);
    return clazz.getEnumConstants()[x];
  }

  public static int getRandomNumber(int min, int max) {
    return random.nextInt(max - min) + min;
  }

}
