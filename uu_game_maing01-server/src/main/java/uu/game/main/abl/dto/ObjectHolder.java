package uu.game.main.abl.dto;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Hand-off slot for one long-poll request.
 *
 * <p>Previously a hand-rolled wait/notify that could park a request thread forever: {@code get()}
 * looped until a value arrived, {@code notify()} woke only one waiter, and an interrupt was
 * swallowed and the loop re-entered. A bounded queue gives the same semantics with a timeout and
 * correct interrupt handling.
 */
public class ObjectHolder<T> {

  private final BlockingQueue<T> slot = new ArrayBlockingQueue<>(1);

  /**
   * Publishes a value. Never blocks: if a value is already pending it is kept, because for a game
   * the client is about to be told the newest state anyway.
   */
  public void set(T value) {
    slot.offer(value);
  }

  /**
   * Waits for a value.
   *
   * @return the value, or {@code null} when the timeout elapsed or the thread was interrupted
   */
  public T get(long timeout, TimeUnit unit) {
    try {
      return slot.poll(timeout, unit);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
  }
}
