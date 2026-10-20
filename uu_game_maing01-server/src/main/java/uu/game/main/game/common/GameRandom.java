package uu.game.main.game.common;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The randomness of one match, and the only source of randomness simulation code may use.
 *
 * <p>Everything else about a match is already a pure function of (initial conditions, input,
 * tick): durations are counted in ticks rather than wall clock, and physics is stepped exactly
 * once per tick. Randomness was the last thing that was not, because it came from the process-wide
 * {@link ThreadLocalRandom} behind {@code uu.game.main.helper.Utils} - so no match could ever be
 * replayed, and a bug reported as "I spawned inside a crate" could not be reproduced.
 *
 * <p>A rule creates one of these in {@code init} from {@link #PARAM_SEED} and threads it through
 * every decision that rolls a die. Because the instance belongs to a single match, which is ticked
 * by one thread at a time, the CAS contention that originally motivated {@link ThreadLocalRandom}
 * does not arise: matches no longer share a generator at all.
 *
 * <p>{@link Random} is used deliberately rather than a faster generator: its algorithm is part of
 * its specification, so the same seed replays identically on any JVM, today and in five years.
 */
public final class GameRandom {

  /** Game parameter carrying the seed, both inbound (replay) and outbound (what was used). */
  public static final String PARAM_SEED = "seed";

  /**
   * Generated seeds stay below 2^48 for two reasons: {@link Random} only distinguishes 48 bits of
   * seed anyway, and the seed is published to the browser as a JSON number, which silently loses
   * precision above 2^53. A seed that cannot survive the round trip to the client and back is not
   * a replayable one.
   */
  private static final long SEED_BOUND = 1L << 48;

  private final long seed;

  private final Random random;

  public GameRandom(long seed) {
    this.seed = seed;
    this.random = new Random(seed);
  }

  /**
   * Builds the match's randomness from the game parameters, taking the seed the caller supplied or
   * generating one, and writing back the seed that was actually used.
   *
   * <p>The write-back is the whole point: the client can show the seed, and handing that same seed
   * back in {@code params} replays the match exactly.
   */
  public static GameRandom fromParams(Map<String, Object> params) {
    Long supplied = readSeed(params);
    GameRandom gameRandom = new GameRandom(supplied == null ? generateSeed() : supplied);
    if (params != null) {
      params.put(PARAM_SEED, gameRandom.getSeed());
    }
    return gameRandom;
  }

  /** For boards built outside a rule, which still need somewhere to spawn a player. */
  public static GameRandom withGeneratedSeed() {
    return new GameRandom(generateSeed());
  }

  /**
   * Parameters arrive as JSON, so a seed may turn up as any {@link Number} - and as a string, since
   * that is how a client avoids JavaScript number precision altogether.
   *
   * @return the supplied seed, or {@code null} when none was given or it was not readable as a
   *     number, in which case a generated seed is better than failing to start the match
   */
  private static Long readSeed(Map<String, Object> params) {
    Object value = params == null ? null : params.get(PARAM_SEED);
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    if (value instanceof String) {
      try {
        return Long.parseLong(((String) value).trim());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  private static long generateSeed() {
    // The seed itself is the one draw that is allowed to be unpredictable - it is chosen once, per
    // match, before the simulation starts, and is recorded.
    return ThreadLocalRandom.current().nextLong(SEED_BOUND);
  }

  /**
   * @param min inclusive lower bound
   * @param max exclusive upper bound; values below {@code min + 1} yield {@code min}
   */
  public int getRandomNumber(int min, int max) {
    return max <= min ? min : min + random.nextInt(max - min);
  }

  public <T extends Enum<?>> T randomEnum(Class<T> clazz) {
    T[] constants = clazz.getEnumConstants();
    return constants[random.nextInt(constants.length)];
  }

  /** The seed this match is running on; feed it back in to replay the match. */
  public long getSeed() {
    return seed;
  }
}
