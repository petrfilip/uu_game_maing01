package uu.game.main.broadcast;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * An SSE client under test control: it records what was written to it, and can be told to fail or
 * to stall inside {@code send}. Overriding the emitter is the only seam available - the subscriber
 * that wraps it is package private in the production code.
 */
class RecordingEmitter extends SseEmitter {

  private final List<Object> received = new CopyOnWriteArrayList<>();
  private final CountDownLatch completedWithError = new CountDownLatch(1);

  private volatile CountDownLatch expected = new CountDownLatch(Integer.MAX_VALUE);
  private volatile boolean failOnSend;
  private volatile CountDownLatch blockSendUntil;
  private volatile CountDownLatch sendStarted;
  private volatile Runnable completionCallback;

  /** Arms a latch that is released once {@code count} further payloads have been written. */
  CountDownLatch expect(int count) {
    CountDownLatch latch = new CountDownLatch(count);
    this.expected = latch;
    return latch;
  }

  void failEverySend() {
    this.failOnSend = true;
  }

  /**
   * Makes {@code send} park until {@code release} is counted down, so a test can hold a client in
   * the middle of a write.
   */
  void blockSendUntil(CountDownLatch release, CountDownLatch started) {
    this.blockSendUntil = release;
    this.sendStarted = started;
  }

  @Override
  public void send(Object object) throws IOException {
    if (failOnSend) {
      throw new IOException("client went away");
    }

    CountDownLatch release = blockSendUntil;
    if (release != null) {
      if (sendStarted != null) {
        sendStarted.countDown();
      }
      try {
        // Bounded so a broken test cannot wedge the build.
        release.await(30, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    received.add(object);
    expected.countDown();
  }

  @Override
  public synchronized void onCompletion(Runnable callback) {
    // Captured so a test can fire it the way the servlet container would.
    this.completionCallback = callback;
    super.onCompletion(callback);
  }

  /** Simulates the container finishing the response. */
  void fireCompletion() {
    Runnable callback = completionCallback;
    if (callback != null) {
      callback.run();
    }
  }

  @Override
  public void completeWithError(Throwable ex) {
    completedWithError.countDown();
  }

  @Override
  public void complete() {
    // The production code completes emitters on unsubscribe; nothing to do here.
  }

  List<Object> getReceived() {
    return received;
  }

  /**
   * Forgets what has been written so far, so a test can subscribe - which now hands the client an
   * immediate snapshot - and then assert only on what the fan-out delivered afterwards.
   */
  void clearReceived() {
    received.clear();
  }

  CountDownLatch getCompletedWithError() {
    return completedWithError;
  }
}
