package uu.game.main.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import uu.game.main.abl.dto.ObjectHolder;

/**
 * The hand-off slot behind long-polling. It replaced a hand-rolled wait/notify that could park a
 * request thread for good: the loop had no timeout and swallowed interrupts.
 */
public class ObjectHolderTest {

  @Test(timeout = 10_000)
  public void getReturnsNullOnTimeoutRatherThanParkingForever() {
    ObjectHolder<String> holder = new ObjectHolder<>();

    long startedAt = System.nanoTime();
    String value = holder.get(150, TimeUnit.MILLISECONDS);
    long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

    assertNull(value);
    assertTrue("waited " + elapsedMs + " ms for a 150 ms timeout", elapsedMs < 5_000);
  }

  @Test(timeout = 10_000)
  public void getReturnsAValuePublishedByAnotherThread() throws Exception {
    final ObjectHolder<String> holder = new ObjectHolder<>();
    final CountDownLatch waiting = new CountDownLatch(1);

    Thread publisher = new Thread(() -> {
      try {
        waiting.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      holder.set("event");
    });
    publisher.start();
    waiting.countDown();

    assertEquals("event", holder.get(5, TimeUnit.SECONDS));
    publisher.join(5_000);
  }

  @Test(timeout = 10_000)
  public void setNeverBlocksEvenWhenAValueIsAlreadyPending() {
    ObjectHolder<String> holder = new ObjectHolder<>();

    // The slot holds exactly one value; a second publish must return immediately rather than wait
    // for a reader that may never come.
    holder.set("first");
    holder.set("second");

    assertEquals("first", holder.get(1, TimeUnit.SECONDS));
  }

  @Test(timeout = 10_000)
  public void anInterruptedWaiterGivesUpAndKeepsTheInterruptFlag() {
    ObjectHolder<String> holder = new ObjectHolder<>();
    Thread.currentThread().interrupt();

    assertNull(holder.get(5, TimeUnit.SECONDS));
    // Swallowing the interrupt and re-entering the wait is what made the old holder unkillable.
    assertTrue("the interrupt must be handed back to the caller", Thread.interrupted());
  }
}
