package uu.game.main.api.dto;

/**
 * The tick health of one game, as reported by {@code metrics/get}.
 *
 * <p>Percentiles carry a {@code Micros} suffix rather than being rounded to milliseconds because a
 * healthy tick of a small match is well under a millisecond: rounding would report the normal case
 * as zero and hide it drifting upwards, which is the earliest warning there is.
 */
public final class MetricsGameDtoOut {

  private String gameName;
  private long ticks;
  private long overruns;
  private long failed;
  private long p50Micros;
  private long p90Micros;
  private long p99Micros;
  private long maxMicros;

  public String getGameName() {
    return gameName;
  }

  public void setGameName(String gameName) {
    this.gameName = gameName;
  }

  /** Ticks measured since {@code sinceMillis}. */
  public long getTicks() {
    return ticks;
  }

  public void setTicks(long ticks) {
    this.ticks = ticks;
  }

  /**
   * Ticks that took longer than {@code tickMillis}. Every one of these made the next tick start
   * late; a rate that is anything but a rounding error means matches are running slow.
   */
  public long getOverruns() {
    return overruns;
  }

  public void setOverruns(long overruns) {
    this.overruns = overruns;
  }

  /** Ticks where the rule threw, so the game state did not advance at all. */
  public long getFailed() {
    return failed;
  }

  public void setFailed(long failed) {
    this.failed = failed;
  }

  public long getP50Micros() {
    return p50Micros;
  }

  public void setP50Micros(long p50Micros) {
    this.p50Micros = p50Micros;
  }

  public long getP90Micros() {
    return p90Micros;
  }

  public void setP90Micros(long p90Micros) {
    this.p90Micros = p90Micros;
  }

  /**
   * The number to watch. Accurate to within 12.5% and never above {@code maxMicros}; when it
   * approaches {@code tickMillis} the simulation is one bad frame from falling behind.
   */
  public long getP99Micros() {
    return p99Micros;
  }

  public void setP99Micros(long p99Micros) {
    this.p99Micros = p99Micros;
  }

  public long getMaxMicros() {
    return maxMicros;
  }

  public void setMaxMicros(long maxMicros) {
    this.maxMicros = maxMicros;
  }
}
