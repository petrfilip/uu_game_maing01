package uu.game.main.metrics;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * A lock-free, allocation-free histogram of tick durations, in microseconds.
 *
 * <h2>Why a histogram and not a mean</h2>
 * The failure this whole package exists to catch is a tick that occasionally runs longer than its
 * period. A mean hides exactly that: at a 50 ms period, nineteen ticks of 5 ms and one of 400 ms
 * average to 24.75 ms and look perfectly healthy, while the room they belong to visibly stutters
 * once a second. What an operator needs is the tail - "99% of ticks finish inside X" - and the tail
 * only comes out of a distribution.
 *
 * <h2>Why this histogram</h2>
 * The bucketing is HdrHistogram's scheme, reduced to the few lines this app needs (the library
 * itself is not an option: the {@code uu_appg01} stack is pinned and new dependencies are
 * forbidden). A value is placed by its exponent plus the top {@value #SUB_BUCKET_BITS} bits of its
 * mantissa, which costs one {@code numberOfLeadingZeros} - a single machine instruction - a shift,
 * a mask and one atomic increment. No allocation, no lock, no sorting and no reservoir sampling, so
 * it is safe to call on the tick thread of every room on the server.
 *
 * <p>The price is resolution: a bucket is at most 1/{@value #SUB_BUCKET_COUNT} wider than its lower
 * bound, so a reported percentile overstates the true value by at most 12.5%. That is far inside
 * what anyone acts on - the question is "is the tail near the 50 ms period", not "is it 47.3 ms".
 *
 * <h2>Threading</h2>
 * Writers (tick threads) only ever do {@link AtomicLongArray#incrementAndGet}, so no count can tear
 * and no increment can be lost. A reader copies every bucket once and computes <em>everything</em>
 * from that copy - see {@link Distribution}. Buckets read early in the copy may miss ticks that
 * buckets read later already include, which is unavoidable without stopping the simulation; it only
 * means the distribution describes marginally fewer samples than have really been observed, never
 * a value that was never measured.
 */
public final class TickHistogram {

  /** Bits of mantissa kept, i.e. the resolution: 3 bits = 8 sub-buckets per octave = 12.5% error. */
  static final int SUB_BUCKET_BITS = 3;

  static final int SUB_BUCKET_COUNT = 1 << SUB_BUCKET_BITS;

  /**
   * Enough buckets for every non-negative {@code long} of microseconds, so {@link #record} can never
   * be handed a duration it has nowhere to put. The highest index a value can reach is
   * {@code ((62 - SUB_BUCKET_BITS + 1) << SUB_BUCKET_BITS) + SUB_BUCKET_COUNT - 1}.
   */
  static final int BUCKET_COUNT = ((64 - SUB_BUCKET_BITS - 1) << SUB_BUCKET_BITS) + SUB_BUCKET_COUNT;

  private final AtomicLongArray buckets = new AtomicLongArray(BUCKET_COUNT);

  /**
   * Counts one observation. The only thing on the hot path: one bucket computation and one atomic
   * increment.
   *
   * @param micros the observed duration; negative values are folded into the first bucket rather
   *     than rejected, because a dropped observation is worse than a slightly wrong one
   */
  public void record(long micros) {
    buckets.incrementAndGet(indexOf(micros));
  }

  /**
   * Copies every bucket once, so that the count, the percentiles and the sample total a caller sees
   * all describe the same reading. Computing the total separately from the scan is the classic way
   * to produce a percentile that is off the end of the data.
   */
  public Distribution snapshot() {
    long[] copy = new long[BUCKET_COUNT];
    for (int i = 0; i < BUCKET_COUNT; i++) {
      copy[i] = buckets.get(i);
    }
    return new Distribution(copy);
  }

  /** The bucket a duration belongs in. Package private: the bounds are the tested contract. */
  static int indexOf(long micros) {
    if (micros < SUB_BUCKET_COUNT) {
      // Below the resolution limit every microsecond gets a bucket of its own.
      return micros <= 0 ? 0 : (int) micros;
    }
    int exponent = 63 - Long.numberOfLeadingZeros(micros);
    int shift = exponent - SUB_BUCKET_BITS;
    int subBucket = (int) ((micros >>> shift) & (SUB_BUCKET_COUNT - 1));
    return ((shift + 1) << SUB_BUCKET_BITS) + subBucket;
  }

  /** Exclusive upper bound, in microseconds, of everything {@link #indexOf} maps to {@code index}. */
  static long upperBoundOf(int index) {
    if (index < SUB_BUCKET_COUNT) {
      return index + 1L;
    }
    int shift = (index >>> SUB_BUCKET_BITS) - 1;
    int subBucket = index & (SUB_BUCKET_COUNT - 1);
    return ((long) SUB_BUCKET_COUNT + subBucket + 1) << shift;
  }

  /**
   * One reading of a histogram: a private copy of the buckets, from which the sample count and every
   * percentile are derived.
   *
   * <p>The copy is the point. A percentile can only be asked of a total that came out of the same
   * array it is scanning, so "the 99th percentile fell off the end of the buckets" and "the
   * percentile is larger than anything ever recorded" are not states this class can reach.
   */
  public static final class Distribution {

    private final long[] buckets;
    private final long sampleCount;

    private Distribution(long[] buckets) {
      this.buckets = buckets;
      long total = 0;
      for (long count : buckets) {
        total += count;
      }
      this.sampleCount = total;
    }

    /** How many observations this reading is built from. */
    public long getSampleCount() {
      return sampleCount;
    }

    /**
     * @param percentile between 0 and 100
     * @return an upper bound, in microseconds, on the duration of the fastest {@code percentile}%
     *     of the observations, or 0 when nothing has been recorded
     */
    public long microsAtPercentile(double percentile) {
      if (sampleCount == 0) {
        return 0;
      }
      long target = (long) Math.ceil(percentile / 100.0d * sampleCount);
      if (target < 1) {
        target = 1;
      }
      long seen = 0;
      for (int i = 0; i < buckets.length; i++) {
        seen += buckets[i];
        if (seen >= target) {
          return upperBoundOf(i);
        }
      }
      // Unreachable: target is at most sampleCount, which is the sum of this very array.
      return upperBoundOf(buckets.length - 1);
    }
  }
}
