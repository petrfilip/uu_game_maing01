package uu.game.main.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import uu.game.main.engine.TickObserver;
import uu.game.main.metrics.GameMetrics;
import uu.game.main.metrics.GameTickSnapshot;
import uu.game.main.metrics.TickHistogram;

/**
 * The tick counters themselves.
 *
 * <p>Lives in the broadcast test package because that is one of the packages the {@code unitTest}
 * task runs; the engine test package belongs to someone else, and a metrics package of its own
 * would simply not be executed.
 */
public class GameMetricsTest {

  private static final String GAME = "soldat";
  private static final String OTHER_GAME = "bulanci";
  private static final long PERIOD_MILLIS = 50;
  private static final long SINCE_MILLIS = 1_700_000_000_000L;

  /** Slower than anything the sweep records, so it is always the slowest sample in its reading. */
  private static final long SENTINEL_NANOS = 9_000_000_000_000_000L;

  private final GameMetrics metrics = new GameMetrics(SINCE_MILLIS);

  private static long millis(long value) {
    return TimeUnit.MILLISECONDS.toNanos(value);
  }

  private TickObserver observer() {
    return metrics.observerFor(GAME, PERIOD_MILLIS);
  }

  private GameTickSnapshot snapshotOf(String gameName) {
    for (GameTickSnapshot snapshot : metrics.snapshot()) {
      if (gameName.equals(snapshot.getGameName())) {
        return snapshot;
      }
    }
    throw new AssertionError("no measurements for " + gameName);
  }

  // -------------------------------------------------------------------------------------------
  // What is measured
  // -------------------------------------------------------------------------------------------

  @Test
  public void aTickIsAnOverrunOnlyWhenItRanLongerThanThePeriod() {
    TickObserver observer = observer();

    observer.onTick(GAME, millis(PERIOD_MILLIS) - 1, false);
    observer.onTick(GAME, millis(PERIOD_MILLIS), false);
    observer.onTick(GAME, millis(PERIOD_MILLIS) + 1, false);

    GameTickSnapshot snapshot = snapshotOf(GAME);
    assertEquals(3, snapshot.getTicks());
    // A tick that finishes exactly on its period has delivered on time; only the one nanosecond
    // over it delays the next tick, which is the whole thing being measured.
    assertEquals(1, snapshot.getOverruns());
  }

  @Test
  public void theOverrunThresholdIsTheOneTheObserverWasCreatedWith() {
    TickObserver fastGame = metrics.observerFor(GAME, 10);
    TickObserver slowGame = metrics.observerFor(OTHER_GAME, 100);

    fastGame.onTick(GAME, millis(20), false);
    slowGame.onTick(OTHER_GAME, millis(20), false);

    // Same duration, different period: a shared or hardcoded threshold would score these alike.
    assertEquals(1, snapshotOf(GAME).getOverruns());
    assertEquals(0, snapshotOf(OTHER_GAME).getOverruns());
  }

  @Test
  public void aTickWhoseRuleThrewIsCountedAndStillTimed() {
    TickObserver observer = observer();

    observer.onTick(GAME, millis(70), true);

    GameTickSnapshot snapshot = snapshotOf(GAME);
    assertEquals(1, snapshot.getFailed());
    // The work up to the exception really happened and really delayed the next tick, so a failed
    // tick has to appear in the distribution and in the overruns as well as in its own counter.
    assertEquals(1, snapshot.getTicks());
    assertEquals(1, snapshot.getOverruns());
    assertTrue(snapshot.getMaxMicros() >= 70_000);
  }

  @Test
  public void eachGameKeepsItsOwnCounters() {
    metrics.observerFor(GAME, PERIOD_MILLIS).onTick(GAME, millis(1), false);
    metrics.observerFor(OTHER_GAME, PERIOD_MILLIS).onTick(OTHER_GAME, millis(1), false);
    metrics.observerFor(OTHER_GAME, PERIOD_MILLIS).onTick(OTHER_GAME, millis(1), false);

    List<GameTickSnapshot> snapshots = metrics.snapshot();
    assertEquals(2, snapshots.size());
    // Ordered by name, so that a human comparing two readings is comparing the same rows.
    assertEquals(OTHER_GAME, snapshots.get(0).getGameName());
    assertEquals(2, snapshots.get(0).getTicks());
    assertEquals(GAME, snapshots.get(1).getGameName());
    assertEquals(1, snapshots.get(1).getTicks());
  }

  @Test
  public void aGameThatHasNotTickedYetReadsAsZeroRatherThanFailing() {
    observer();

    GameTickSnapshot snapshot = snapshotOf(GAME);
    assertEquals(0, snapshot.getTicks());
    assertEquals(0, snapshot.getOverruns());
    assertEquals(0, snapshot.getFailed());
    assertEquals(0, snapshot.getP99Micros());
    assertEquals(0, snapshot.getMaxMicros());
  }

  @Test
  public void theCountersCarryTheMomentTheyStarted() {
    assertEquals(SINCE_MILLIS, metrics.getSinceMillis());
  }

  // -------------------------------------------------------------------------------------------
  // The distribution
  // -------------------------------------------------------------------------------------------

  @Test
  public void theTailIsVisibleWhereAMeanWouldHideIt() {
    TickObserver observer = observer();
    for (int i = 0; i < 90; i++) {
      observer.onTick(GAME, millis(1), false);
    }
    for (int i = 0; i < 10; i++) {
      observer.onTick(GAME, millis(200), false);
    }

    GameTickSnapshot snapshot = snapshotOf(GAME);
    assertEquals(100, snapshot.getTicks());
    // The arithmetic mean of this sample is 20.9 ms - comfortably inside the 50 ms period, and a
    // lie: one tick in ten is four periods long and every room running this game stutters.
    assertTrue("p50 was " + snapshot.getP50Micros(), snapshot.getP50Micros() <= 1_200);
    assertTrue("p99 was " + snapshot.getP99Micros(), snapshot.getP99Micros() >= 200_000);
    assertEquals(10, snapshot.getOverruns());
  }

  @Test
  public void percentilesRiseWithTheDataAndNeverPassTheSlowestTick() {
    TickObserver observer = observer();
    for (int i = 1; i <= 100; i++) {
      observer.onTick(GAME, millis(i), false);
    }

    GameTickSnapshot snapshot = snapshotOf(GAME);
    assertTrue(snapshot.getP50Micros() <= snapshot.getP90Micros());
    assertTrue(snapshot.getP90Micros() <= snapshot.getP99Micros());
    // The reported percentile is the end of the bucket it fell in, which for the slowest bucket
    // sits above anything ever measured; a reading where p99 beats the maximum is nonsense.
    assertTrue(snapshot.getP99Micros() <= snapshot.getMaxMicros());
    assertEquals(100_000, snapshot.getMaxMicros());
    assertTrue("p50 was " + snapshot.getP50Micros(), snapshot.getP50Micros() >= 50_000);
    assertTrue("p50 was " + snapshot.getP50Micros(), snapshot.getP50Micros() <= 57_000);
  }

  @Test
  public void aValueIsAlwaysInsideTheBucketItIsReportedIn() {
    for (long micros : durationSweep()) {
      long reported = bucketEndOf(micros);
      // Bucket bounds are the whole contract of the histogram: one wrong shift and every
      // percentile in the app silently moves by a factor of two. The reported figure is the end of
      // the bucket, so it is above the value itself but by no more than the 12.5% resolution.
      assertTrue(micros + " reported as " + reported, reported > micros);
      assertTrue(micros + " reported as " + reported, reported <= micros + micros / 8 + 1);
    }
  }

  @Test
  public void aSlowerTickIsNeverReportedAsFasterThanAQuickerOne() {
    long previous = -1;
    for (long micros : durationSweep()) {
      long reported = bucketEndOf(micros);
      assertTrue("bucketing is not monotone at " + micros, reported >= previous);
      previous = reported;
    }
  }

  /**
   * The end of the bucket a duration lands in.
   *
   * <p>Read as the median of that duration and one absurdly slow tick: with a single sample the
   * percentile is clamped to the maximum and the bucket it came from cannot be seen at all.
   */
  private long bucketEndOf(long micros) {
    GameMetrics single = new GameMetrics(SINCE_MILLIS);
    TickObserver observer = single.observerFor(GAME, PERIOD_MILLIS);
    observer.onTick(GAME, micros * 1_000L, false);
    observer.onTick(GAME, SENTINEL_NANOS, false);
    return single.snapshot().get(0).getP50Micros();
  }

  @Test
  public void theHistogramItselfSwallowsANegativeDurationRatherThanThrowing() {
    TickHistogram histogram = new TickHistogram();

    // The public entry point, reached without the guard in GameTickMetrics in front of it: a
    // negative index here is an ArrayIndexOutOfBounds on a tick thread, and the observer contract
    // is that it never throws.
    histogram.record(-5);
    histogram.record(0);

    assertEquals(2, histogram.snapshot().getSampleCount());
  }

  @Test
  public void impossibleDurationsAreCountedRatherThanCrashingTheTickThread() {
    TickObserver observer = observer();

    // An observer runs inside the tick's finally block. Whatever it is handed - a clock that went
    // backwards, a zero, an absurd value - it has to come back quietly.
    observer.onTick(GAME, 0, false);
    observer.onTick(GAME, -1, false);
    observer.onTick(GAME, Long.MAX_VALUE, false);

    assertEquals(3, snapshotOf(GAME).getTicks());
  }

  // -------------------------------------------------------------------------------------------
  // Concurrency
  // -------------------------------------------------------------------------------------------

  /**
   * Ticks are recorded from several tick threads while a request thread reads. Every reading has to
   * be self-consistent: a subset can never be bigger than the total it is a subset of, and no
   * percentile can exceed the slowest tick ever seen.
   */
  @Test
  public void aReaderNeverSeesASubsetLargerThanItsTotal() throws Exception {
    final int writers = 4;
    final int readings = 3_000;
    // Every tick is both an overrun and a failure, so any skew between the counters shows up at
    // once instead of hiding behind a ratio of one in a thousand.
    final TickObserver observer = metrics.observerFor(GAME, 1);

    final CountDownLatch started = new CountDownLatch(writers);
    final CountDownLatch stop = new CountDownLatch(1);
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < writers; i++) {
      Thread thread = new Thread(() -> {
        started.countDown();
        while (stop.getCount() > 0) {
          observer.onTick(GAME, millis(2), true);
        }
      }, "tick-" + i);
      thread.setDaemon(true);
      threads.add(thread);
      thread.start();
    }
    assertTrue(started.await(5, TimeUnit.SECONDS));

    AtomicReference<String> broken = new AtomicReference<>();
    long previousTicks = 0;
    try {
      for (int i = 0; i < readings && broken.get() == null; i++) {
        GameTickSnapshot snapshot = snapshotOf(GAME);
        if (snapshot.getOverruns() > snapshot.getTicks()) {
          broken.set("overruns " + snapshot.getOverruns() + " > ticks " + snapshot.getTicks());
        } else if (snapshot.getFailed() > snapshot.getTicks()) {
          broken.set("failed " + snapshot.getFailed() + " > ticks " + snapshot.getTicks());
        } else if (snapshot.getP99Micros() > snapshot.getMaxMicros()) {
          broken.set("p99 " + snapshot.getP99Micros() + " > max " + snapshot.getMaxMicros());
        } else if (snapshot.getTicks() < previousTicks) {
          broken.set("ticks went backwards: " + previousTicks + " then " + snapshot.getTicks());
        }
        previousTicks = snapshot.getTicks();
      }
    } finally {
      stop.countDown();
      for (Thread thread : threads) {
        thread.join(5_000);
      }
    }

    assertTrue(String.valueOf(broken.get()), broken.get() == null);
    assertTrue("the writers never got going", previousTicks > readings);
    GameTickSnapshot finalSnapshot = snapshotOf(GAME);
    // Nothing may be lost either: every tick recorded is a tick reported.
    assertEquals(finalSnapshot.getTicks(), finalSnapshot.getOverruns());
    assertEquals(finalSnapshot.getTicks(), finalSnapshot.getFailed());
  }

  /** Values spread over the whole range a tick can plausibly - or implausibly - take. */
  private static List<Long> durationSweep() {
    List<Long> values = new ArrayList<>();
    for (long micros = 0; micros <= 200; micros++) {
      values.add(micros);
    }
    for (long micros = 256; micros <= 4_000_000_000L; micros *= 2) {
      values.add(micros - 1);
      values.add(micros);
      values.add(micros + 1);
      values.add(micros + micros / 3);
    }
    return values;
  }
}
