package uu.game.main.abl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A scheduler that records what was scheduled instead of running it, so tests drive time by hand
 * rather than waiting for it. Extending {@link ScheduledThreadPoolExecutor} with no core threads
 * keeps this to the two methods production code actually uses.
 */
public class RecordingScheduler extends ScheduledThreadPoolExecutor {

  private final List<RecordedFuture> repeating = new ArrayList<>();
  private final List<Runnable> delayed = new ArrayList<>();

  public RecordingScheduler() {
    super(0);
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
    RecordedFuture future = new RecordedFuture();
    repeating.add(future);
    return future;
  }

  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    delayed.add(command);
    return new RecordedFuture();
  }

  /** The handle of the n-th repeating task, i.e. of the n-th {@code startGame}. */
  public RecordedFuture repeating(int index) {
    return repeating.get(index);
  }

  public int repeatingCount() {
    return repeating.size();
  }

  public int delayedCount() {
    return delayed.size();
  }

  public static final class RecordedFuture implements ScheduledFuture<Object> {

    private boolean cancelled;

    public boolean wasCancelled() {
      return cancelled;
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return 0;
    }

    @Override
    public int compareTo(Delayed other) {
      return 0;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      cancelled = true;
      return true;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public boolean isDone() {
      return cancelled;
    }

    @Override
    public Object get() {
      return null;
    }

    @Override
    public Object get(long timeout, TimeUnit unit) {
      return null;
    }
  }
}
