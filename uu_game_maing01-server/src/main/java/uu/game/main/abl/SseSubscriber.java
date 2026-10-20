package uu.game.main.abl;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import uu.game.main.metrics.BroadcastMetrics;

/**
 * One SSE client, with its own outbound queue.
 *
 * <p>The tick thread must never block on a socket. {@link SseEmitter#send} writes straight to the
 * response stream, so a client that stops reading will stall whoever calls it - previously that was
 * the simulation thread, holding a server-wide lock, for every room at once.
 *
 * <p>Here the producer only enqueues. A single drain task per subscriber does the writing, which
 * both keeps events in order for that client and confines a slow one to its own queue. When the
 * queue overflows the oldest frames are dropped: for a game the newest state is the only one that
 * matters, and a client falling behind should degrade rather than apply back-pressure to the game.
 *
 * <p>Those drops are counted into {@link BroadcastMetrics}. Dropping is the correct behaviour, but
 * silent dropping is not: a player watching a match with gaps in it used to leave no trace anywhere
 * on the server.
 */
class SseSubscriber {

  private static final int MAX_QUEUED_EVENTS = 32;

  /** Whose stream this is; used to tell a disconnect apart from that player's other open tabs. */
  private final String playerId;

  private final SseEmitter emitter;
  private final Executor executor;
  private final Queue<Object> queue = new ConcurrentLinkedQueue<>();
  private final AtomicInteger queued = new AtomicInteger();
  private final AtomicBoolean draining = new AtomicBoolean();
  private final BroadcastMetrics metrics;

  private volatile boolean dead;

  SseSubscriber(String playerId, SseEmitter emitter, Executor executor, BroadcastMetrics metrics) {
    this.playerId = playerId;
    this.emitter = emitter;
    this.executor = executor;
    this.metrics = metrics;
  }

  SseEmitter getEmitter() {
    return emitter;
  }

  String getPlayerId() {
    return playerId;
  }

  boolean isDead() {
    return dead;
  }

  void enqueue(Object payload) {
    if (dead) {
      return;
    }

    queue.add(payload);
    if (queued.incrementAndGet() > MAX_QUEUED_EVENTS && queue.poll() != null) {
      queued.decrementAndGet();
      // Counted only when a frame was really discarded: poll() can legitimately come back empty if
      // the drain task emptied the queue in between, and counting that would invent a loss.
      metrics.frameDropped();
    }

    scheduleDrain();
  }

  private void scheduleDrain() {
    // compareAndSet keeps exactly one thread writing to this emitter at a time, so events cannot be
    // delivered out of order even though the pool has many threads.
    if (draining.compareAndSet(false, true)) {
      try {
        executor.execute(this::drain);
      } catch (RuntimeException e) {
        draining.set(false);
        throw e;
      }
    }
  }

  private void drain() {
    try {
      Object payload;
      while (!dead && (payload = queue.poll()) != null) {
        queued.decrementAndGet();
        try {
          emitter.send(payload);
        } catch (Exception e) {
          // Includes the client simply going away; nothing to log at error level.
          die(e);
          return;
        }
      }
    } finally {
      draining.set(false);
      // A producer may have enqueued between the last poll and releasing the flag.
      if (!dead && !queue.isEmpty()) {
        scheduleDrain();
      }
    }
  }

  void die(Exception cause) {
    if (dead) {
      return;
    }
    dead = true;
    queue.clear();
    queued.set(0);
    if (cause != null) {
      // Counted before the emitter is touched: the container may well throw on a connection that
      // has already gone, and the stream still failed.
      metrics.streamFailed();
    }
    try {
      if (cause == null) {
        emitter.complete();
      } else {
        emitter.completeWithError(cause);
      }
    } catch (Exception ignored) {
      // The emitter may already be closed by the container.
    }
  }
}
