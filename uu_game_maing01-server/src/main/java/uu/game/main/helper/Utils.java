package uu.game.main.helper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Process-wide randomness for incidental, non-simulation use.
 *
 * <h2>Simulation code must not use this class</h2>
 * Every random decision inside a match - obstacle placement, spawn points, bonus drops, bot
 * behaviour - must come from the match's own
 * {@link uu.game.main.game.common.GameRandom}, created by the rule in {@code init} from a recorded
 * seed. A single call to this class anywhere in {@code uu.game.main.game} makes the whole match
 * unreplayable, because {@link ThreadLocalRandom} depends on which thread happened to run the tick
 * and on nothing the seed can control. That rule is enforced by a test that scans the compiled
 * simulation classes for references to this class, so a stray call fails the build rather than
 * quietly costing reproducibility.
 */
public final class Utils {

  private Utils() {
  }

  /**
   * {@link ThreadLocalRandom} rather than a shared {@link java.util.Random}: this is the unseeded
   * path, where no caller can be replaying anything, so there is no reason to pay for a contended
   * CAS on every call.
   */
  public static <T extends Enum<?>> T randomEnum(Class<T> clazz) {
    T[] constants = clazz.getEnumConstants();
    return constants[ThreadLocalRandom.current().nextInt(constants.length)];
  }

  /**
   * @param min inclusive lower bound
   * @param max exclusive upper bound; values below {@code min + 1} yield {@code min}
   */
  public static int getRandomNumber(int min, int max) {
    return max <= min ? min : ThreadLocalRandom.current().nextInt(min, max);
  }
}
