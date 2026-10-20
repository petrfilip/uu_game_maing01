package uu.game.main.metrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * What the fan-out to SSE clients is losing.
 *
 * <p>A subscriber that stops reading has its oldest frames thrown away once its queue is full -
 * deliberately, because a game client wants the newest state and must never apply back-pressure to
 * the simulation. That is the right behaviour and it was completely invisible: a player could be
 * watching a stuttering, half-updated match while the server reported nothing at all.
 *
 * <p>Plain atomic counters, written from the dispatcher threads and from whichever thread publishes
 * an event, read from a request thread. They are independent totals with no invariant between them,
 * so a 64-bit atomic is the whole concurrency story: no value can tear and none can be lost.
 */
public final class BroadcastMetrics {

  private final AtomicLong droppedFrames = new AtomicLong();
  private final AtomicLong failedStreams = new AtomicLong();

  /**
   * One frame was discarded because the subscriber's queue was full.
   *
   * <p>Only queue overflow is counted. Frames discarded when a stream is torn down are not a health
   * signal - the client is gone and nobody is waiting for them - whereas overflow means a client
   * that is still connected is being shown an incomplete match.
   */
  public void frameDropped() {
    droppedFrames.incrementAndGet();
  }

  /** A stream was closed because writing to it threw, which usually means the client went away. */
  public void streamFailed() {
    failedStreams.incrementAndGet();
  }

  public long getDroppedFrames() {
    return droppedFrames.get();
  }

  public long getFailedStreams() {
    return failedStreams.get();
  }
}
