package uu.game.main.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static uu.game.main.abl.RoomAblFixture.AWID;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uu.game.main.abl.BroadcastAbl;
import uu.game.main.abl.RoomAblFixture;
import uu.game.main.abl.dto.Room;
import uu.game.main.abl.dto.event.GameServerEvent;

/**
 * What the fan-out reports about itself: how many people are connected and what it is losing.
 *
 * <p>Dropping frames from a client that has stopped reading is the right behaviour and is tested
 * elsewhere. What is tested here is that it leaves a trace - it used to happen in complete silence,
 * so a player watching a match full of holes produced no signal anywhere on the server.
 */
public class BroadcastMetricsTest {

  private static final long WAIT_MS = 5_000;
  private static final String OWNER = "alice";
  private static final String PLAYER = "bob";

  private RoomAblFixture fixture;
  private BroadcastAbl broadcast;
  private CountDownLatch releaseSlowSend;

  private String roomA;
  private String roomB;

  private static final class TestEvent extends GameServerEvent<String> {

    private TestEvent(String awid, String roomId, String payload) {
      super("test", awid, roomId, payload);
    }
  }

  @Before
  public void setUp() throws Exception {
    fixture = new RoomAblFixture(60_000, 100, 15_000);
    broadcast = new BroadcastAbl();
    Field roomAblField = BroadcastAbl.class.getDeclaredField("roomAbl");
    roomAblField.setAccessible(true);
    roomAblField.set(broadcast, fixture.roomAbl());

    Room a = fixture.createRoom(OWNER);
    Room b = fixture.createRoom(OWNER);
    roomA = a.getId();
    roomB = b.getId();
    fixture.join(roomA, PLAYER);
    fixture.join(roomB, PLAYER);
  }

  @After
  public void tearDown() {
    if (releaseSlowSend != null) {
      releaseSlowSend.countDown();
    }
    broadcast.shutdown();
    fixture.shutdown();
  }

  private void publish(String roomId, String payload) {
    broadcast.onApplicationEvent(new TestEvent(AWID, roomId, payload));
  }

  private void subscribeQuietly(String roomId, RecordingEmitter emitter) throws Exception {
    CountDownLatch snapshot = emitter.expect(1);
    broadcast.subscribe(AWID, roomId, PLAYER, emitter);
    assertTrue("the initial snapshot never arrived", snapshot.await(WAIT_MS, TimeUnit.MILLISECONDS));
    emitter.clearReceived();
  }

  /** The queue bound the production code enforces; read rather than copied so the two cannot drift. */
  private static int maxQueuedEvents() throws Exception {
    Field field = Class.forName("uu.game.main.abl.SseSubscriber").getDeclaredField("MAX_QUEUED_EVENTS");
    field.setAccessible(true);
    return field.getInt(null);
  }

  @Test
  public void everyFrameAFullQueueThrowsAwayIsCounted() throws Exception {
    RecordingEmitter slow = new RecordingEmitter();
    subscribeQuietly(roomA, slow);

    releaseSlowSend = new CountDownLatch(1);
    CountDownLatch sendStarted = new CountDownLatch(1);
    slow.blockSendUntil(releaseSlowSend, sendStarted);

    // Park the client inside send(). Until the latch is released nothing is drained, so the queue
    // fills at exactly the rate we publish and the arithmetic below is not a race.
    publish(roomA, "parks-the-writer");
    assertTrue("the client never started writing", sendStarted.await(WAIT_MS, TimeUnit.MILLISECONDS));

    int capacity = maxQueuedEvents();
    int overflow = 8;
    for (int i = 0; i < capacity + overflow; i++) {
      publish(roomA, "frame-" + i);
    }

    assertEquals(overflow, broadcast.getBroadcastMetrics().getDroppedFrames());
  }

  @Test
  public void aClientThatKeepsUpLosesNothingAndIsNotCounted() throws Exception {
    RecordingEmitter healthy = new RecordingEmitter();
    subscribeQuietly(roomA, healthy);

    CountDownLatch received = healthy.expect(4);
    for (int i = 0; i < 4; i++) {
      publish(roomA, "frame-" + i);
    }
    assertTrue("the client did not receive everything", received.await(WAIT_MS, TimeUnit.MILLISECONDS));

    // A drop counter that fires on every enqueue, or on a poll that legitimately came back empty,
    // would make a perfectly healthy server look like it was shedding load.
    assertEquals(0, broadcast.getBroadcastMetrics().getDroppedFrames());
    assertEquals(4, healthy.getReceived().size());
  }

  @Test
  public void aStreamThatDiesMidWriteIsCounted() throws Exception {
    RecordingEmitter broken = new RecordingEmitter();
    broken.failEverySend();

    broadcast.subscribe(AWID, roomA, PLAYER, broken);
    assertTrue("the broken subscriber was never closed",
      broken.getCompletedWithError().await(WAIT_MS, TimeUnit.MILLISECONDS));

    assertEquals(1, broadcast.getBroadcastMetrics().getFailedStreams());
    // Closing a healthy stream is not a failure and must not be counted as one.
    RecordingEmitter healthy = new RecordingEmitter();
    subscribeQuietly(roomB, healthy);
    healthy.fireCompletion();
    assertEquals(1, broadcast.getBroadcastMetrics().getFailedStreams());
  }

  @Test
  public void theSubscriberCountIsPeopleConnectedNotRoomsWatched() throws Exception {
    RecordingEmitter firstTab = new RecordingEmitter();
    RecordingEmitter secondTab = new RecordingEmitter();
    RecordingEmitter otherRoom = new RecordingEmitter();
    subscribeQuietly(roomA, firstTab);
    subscribeQuietly(roomA, secondTab);
    subscribeQuietly(roomB, otherRoom);

    assertEquals(3, broadcast.subscriberCount());
    assertEquals(2, broadcast.roomsWithSubscribers());

    firstTab.fireCompletion();
    assertEquals(2, broadcast.subscriberCount());
    assertEquals(2, broadcast.roomsWithSubscribers());

    otherRoom.fireCompletion();
    assertEquals(1, broadcast.subscriberCount());
    // The room's whole entry goes when its last stream does, otherwise the map grows forever.
    assertEquals(1, broadcast.roomsWithSubscribers());
  }
}
