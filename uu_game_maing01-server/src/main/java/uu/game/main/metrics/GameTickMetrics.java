package uu.game.main.metrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Everything measured about one game's tick loop, across every room running that game.
 *
 * <h2>Ordering, and why a reader can never see an impossible value</h2>
 * Four counters are written per tick and read, separately, from a request thread. Nothing here is
 * locked, so the only tool available is the order the writes and the reads happen in. Every field is
 * an atomic 64-bit cell, so no single value can tear; the interesting part is the relationships
 * between them - {@code overruns <= ticks}, {@code failed <= ticks}, {@code p99 <= max} - which a
 * naive implementation breaks the moment a tick lands mid-read.
 *
 * <p>The rule used throughout is: <b>the writer updates the total before the subsets, and the reader
 * reads the subsets before the total.</b> Concretely, {@link #record} writes
 * <ol>
 *   <li>{@code maxNanos},</li>
 *   <li>the histogram bucket - which <em>is</em> the tick count,</li>
 *   <li>{@code overruns},</li>
 *   <li>{@code failed},</li>
 * </ol>
 * and {@link #snapshot} reads them in exactly the reverse order. Atomic updates are ordered in the
 * JMM's synchronization order, so if a reader observes tick <i>X</i> in {@code failed}, every write
 * that <i>X</i> made earlier - including its bucket increment - is already visible to the reads that
 * follow. That gives {@code failed <= ticks} and {@code overruns <= ticks} outright, and
 * {@code max >= } every sample the bucket scan saw, which is what lets the percentiles be clamped to
 * the maximum honestly rather than hopefully.
 *
 * <p>The counters can only ever read <em>low</em>, by the handful of ticks in flight during the
 * read. That is the right direction to be wrong in: a monitor never invents an overrun.
 */
public final class GameTickMetrics {

  private static final long NANOS_PER_MICRO = 1_000L;

  private final TickHistogram histogram = new TickHistogram();

  /** Longest tick ever seen, in nanoseconds. Monotone, so it is safe to read at any moment. */
  private final AtomicLong maxNanos = new AtomicLong();

  private final AtomicLong overruns = new AtomicLong();
  private final AtomicLong failed = new AtomicLong();

  /**
   * Records one tick. Called on the tick thread of every room running this game, so it allocates
   * nothing, takes no lock, branches on nothing unpredictable and never throws.
   *
   * @param durationNanos how long the tick took
   * @param periodNanos the configured tick period; anything strictly longer than this is an overrun,
   *     because a tick that takes exactly its period has still delivered on time
   * @param tickFailed whether the rule threw. A failed tick is still timed - the work up to the
   *     exception was really done - and is counted in both the distribution and its own counter
   */
  public void record(long durationNanos, long periodNanos, boolean tickFailed) {
    recordMax(durationNanos);
    // A nonsensical duration - a clock that went backwards, say - is folded into the first bucket
    // by the histogram rather than guarded against twice; see TickHistogram#record.
    histogram.record(durationNanos / NANOS_PER_MICRO);
    if (periodNanos > 0 && durationNanos > periodNanos) {
      // The silent failure this package exists for: scheduleWithFixedDelay does not complain, it
      // just starts the next tick late, and the match runs in slow motion for everyone in it.
      overruns.incrementAndGet();
    }
    if (tickFailed) {
      failed.incrementAndGet();
    }
  }

  private void recordMax(long durationNanos) {
    long current = maxNanos.get();
    // Usually one volatile read and no write at all: the maximum stops moving almost immediately.
    while (durationNanos > current) {
      if (maxNanos.compareAndSet(current, durationNanos)) {
        return;
      }
      current = maxNanos.get();
    }
  }

  /** Reads the counters in the reverse of the order {@link #record} writes them; see the class doc. */
  public GameTickSnapshot snapshot(String gameName) {
    long failedTicks = failed.get();
    long overrunTicks = overruns.get();
    TickHistogram.Distribution distribution = histogram.snapshot();
    long maxMicros = toMicrosRoundingUp(maxNanos.get());

    return new GameTickSnapshot(gameName,
      distribution.getSampleCount(),
      overrunTicks,
      failedTicks,
      clamp(distribution.microsAtPercentile(50), maxMicros),
      clamp(distribution.microsAtPercentile(90), maxMicros),
      clamp(distribution.microsAtPercentile(99), maxMicros),
      maxMicros);
  }

  /**
   * A percentile is the upper bound of the bucket it fell in, which for the slowest bucket can sit
   * above the slowest tick actually seen. Clamping keeps the numbers a human reads consistent with
   * each other; it is sound because {@code max} is read after the buckets and therefore already
   * covers every sample the scan counted.
   */
  private static long clamp(long micros, long maxMicros) {
    return micros > maxMicros ? maxMicros : micros;
  }

  /** Rounded up so the reported maximum is never smaller than the bucket a sample was counted in. */
  private static long toMicrosRoundingUp(long nanos) {
    return (nanos + NANOS_PER_MICRO - 1) / NANOS_PER_MICRO;
  }
}
