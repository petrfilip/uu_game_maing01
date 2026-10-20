package uu.game.main.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A scheduler that captures the tick task instead of running it, so the engine tests drive the
 * simulation by hand and stay deterministic. Extending {@link ScheduledThreadPoolExecutor} with no
 * core threads keeps this to the one method the engine actually uses.
 */
class CapturingScheduler extends ScheduledThreadPoolExecutor {

  private final List<Runnable> scheduled = new ArrayList<>();
  private final List<CapturedFuture> futures = new ArrayList<>();

  CapturingScheduler() {
    super(0);
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
    scheduled.add(command);
    CapturedFuture future = new CapturedFuture();
    futures.add(future);
    return future;
  }

  /** The tick task of the n-th {@code startGame}. */
  Runnable task(int index) {
    return scheduled.get(index);
  }

  Runnable lastTask() {
    return scheduled.get(scheduled.size() - 1);
  }

  int scheduleCount() {
    return scheduled.size();
  }

  CapturedFuture future(int index) {
    return futures.get(index);
  }

  static final class CapturedFuture implements ScheduledFuture<Object> {

    private boolean cancelled;

    boolean isCancelledByEngine() {
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
