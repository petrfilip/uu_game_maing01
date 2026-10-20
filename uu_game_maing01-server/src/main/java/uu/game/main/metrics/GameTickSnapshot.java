package uu.game.main.metrics;

/**
 * One immutable reading of a game's tick loop, taken at a single moment for a caller to report.
 *
 * <p>Durations are in microseconds rather than milliseconds: a healthy tick of a small match is
 * well under a millisecond, and rounding the fast case to "0 ms" would throw away the baseline you
 * need in order to notice it moving.
 */
public final class GameTickSnapshot {

  private final String gameName;
  private final long ticks;
  private final long overruns;
  private final long failed;
  private final long p50Micros;
  private final long p90Micros;
  private final long p99Micros;
  private final long maxMicros;

  GameTickSnapshot(String gameName, long ticks, long overruns, long failed,
                   long p50Micros, long p90Micros, long p99Micros, long maxMicros) {
    this.gameName = gameName;
    this.ticks = ticks;
    this.overruns = overruns;
    this.failed = failed;
    this.p50Micros = p50Micros;
    this.p90Micros = p90Micros;
    this.p99Micros = p99Micros;
    this.maxMicros = maxMicros;
  }

  public String getGameName() {
    return gameName;
  }

  /** How many ticks this reading describes. */
  public long getTicks() {
    return ticks;
  }

  /** Ticks that took longer than the configured period, i.e. that made the match run slow. */
  public long getOverruns() {
    return overruns;
  }

  /** Ticks where the rule threw, so the state did not advance at all. */
  public long getFailed() {
    return failed;
  }

  public long getP50Micros() {
    return p50Micros;
  }

  public long getP90Micros() {
    return p90Micros;
  }

  public long getP99Micros() {
    return p99Micros;
  }

  public long getMaxMicros() {
    return maxMicros;
  }
}
