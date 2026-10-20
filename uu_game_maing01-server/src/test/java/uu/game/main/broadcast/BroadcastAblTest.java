package uu.game.main.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static uu.game.main.abl.RoomAblFixture.AWID;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uu.game.main.abl.BroadcastAbl;
import uu.game.main.abl.RoomAblFixture;
import uu.game.main.abl.dto.Player;
import uu.game.main.abl.dto.PlayerStateEnum;
import uu.game.main.abl.dto.Room;
import uu.game.main.abl.dto.event.GameServerEvent;
import uu.game.main.api.dto.PollDtoIn;
import uu.game.main.api.dto.PollDtoOut;
import uu.game.main.api.exceptions.RoomAblRuntimeException;
import uu.game.main.domain.GameState;
import uu.game.main.engine.GameEngine;

public class BroadcastAblTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final long WAIT_MS = 5_000;
  private static final long GRACE_MILLIS = 15_000;

  private static final String OWNER = "alice";
  private static final String PLAYER = "bob";
  private static final String STRANGER = "mallory";

  private RoomAblFixture fixture;
  private BroadcastAbl broadcast;
  private CountDownLatch releaseSlowSend;

  private String roomA;
  private String roomB;

  /** Any {@link GameServerEvent} will do; the ABL only reads awid, roomId and the payload. */
  private static final class TestEvent extends GameServerEvent<String> {

    private TestEvent(String awid, String roomId, String payload) {
      super("test", awid, roomId, payload);
    }
  }

  @Before
  public void setUp() throws Exception {
    fixture = new RoomAblFixture(60_000, 100, GRACE_MILLIS);
    broadcast = new BroadcastAbl();
    // Spring injects this; the membership check and the initial snapshot both go through it.
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

  /**
   * The payload as the client on the other end would read it.
   *
   * <p>A fan-out frame arrives already rendered - one JSON per room rather than one per person
   * watching - so this parses what actually goes on the wire. The snapshot a single subscriber is
   * handed on connect has nobody to share it with and is still passed as an object.
   */
  private static String payloadOf(Object received) {
    if (received instanceof PollDtoOut) {
      return (String) ((PollDtoOut) received).getOutput();
    }
    assertTrue("expected a rendered frame or a PollDtoOut, got " + received, received instanceof String);
    try {
      return MAPPER.readTree((String) received).get("output").asText();
    } catch (Exception e) {
      throw new AssertionError("what went on the wire is not JSON: " + received, e);
    }
  }

  private static void awaitLatch(String what, CountDownLatch latch) throws InterruptedException {
    assertTrue(what, latch.await(WAIT_MS, TimeUnit.MILLISECONDS));
  }

  /**
   * Subscribes and waits out the snapshot every new subscriber now receives, so a test can assert
   * on what the fan-out delivered and nothing else.
   */
  private void subscribeQuietly(String roomId, RecordingEmitter emitter) throws Exception {
    CountDownLatch snapshot = emitter.expect(1);
    broadcast.subscribe(AWID, roomId, PLAYER, emitter);
    awaitLatch("the initial snapshot never arrived", snapshot);
    emitter.clearReceived();
  }

  private Player enginePlayer(String roomId, String playerId) {
    GameEngine engine = fixture.registry().find(AWID, roomId);
    assertNotNull("the room has no runtime", engine);
    for (Player player : engine.getPlayers()) {
      if (playerId.equals(player.getPlayerId())) {
        return player;
      }
    }
    throw new AssertionError("player " + playerId + " is not in the room");
  }

  // -------------------------------------------------------------------------------------------
  // Fan-out
  // -------------------------------------------------------------------------------------------

  @Test
  public void aSubscriberOfOneRoomNeverReceivesAnotherRoomsEvents() throws Exception {
    RecordingEmitter emitterA = new RecordingEmitter();
    RecordingEmitter emitterB = new RecordingEmitter();
    subscribeQuietly(roomA, emitterA);
    subscribeQuietly(roomB, emitterB);

    CountDownLatch bReceived = emitterB.expect(1);
    publish(roomB, "for-b");
    awaitLatch("room B never got its own event", bReceived);

    // Publish to A afterwards and wait for it, so that by the time we look at A's inbox anything
    // mis-routed from B would already have arrived.
    CountDownLatch aReceived = emitterA.expect(1);
    publish(roomA, "for-a");
    awaitLatch("room A never got its own event", aReceived);

    // Subscriptions used to be one server-wide collection, so every client received every game.
    assertEquals(1, emitterA.getReceived().size());
    assertEquals("for-a", payloadOf(emitterA.getReceived().get(0)));
  }

  @Test
  public void everySubscriberOfARoomIsHandedTheOneRenderedFrame() throws Exception {
    RecordingEmitter first = new RecordingEmitter();
    RecordingEmitter second = new RecordingEmitter();
    subscribeQuietly(roomA, first);
    subscribeQuietly(roomA, second);

    CountDownLatch firstGot = first.expect(1);
    CountDownLatch secondGot = second.expect(1);
    publish(roomA, "one-frame");
    awaitLatch("the first subscriber never got the event", firstGot);
    awaitLatch("the second subscriber never got the event", secondGot);

    // SseEmitter.send serialises on the emitter, so handing the same object to a room serialised
    // the identical state once per person watching, twenty times a second. Same instance, not
    // merely equal: equal strings would still mean it was rendered twice.
    assertSame("the state was serialised once per subscriber rather than once per room",
      first.getReceived().get(0), second.getReceived().get(0));
    assertEquals("one-frame", payloadOf(first.getReceived().get(0)));
  }

  @Test
  public void aSubscriberWhoseSendThrowsIsDroppedWithoutStarvingTheOthers() throws Exception {
    RecordingEmitter healthy = new RecordingEmitter();
    subscribeQuietly(roomA, healthy);

    RecordingEmitter broken = new RecordingEmitter();
    broken.failEverySend();
    broadcast.subscribe(AWID, roomA, PLAYER, broken);
    assertEquals(2, broadcast.subscriberCount(AWID, roomA));

    CountDownLatch first = healthy.expect(1);
    publish(roomA, "one");
    awaitLatch("the healthy subscriber was starved by the broken one", first);
    awaitLatch("the broken subscriber was never closed", broken.getCompletedWithError());

    CountDownLatch second = healthy.expect(1);
    publish(roomA, "two");
    assertEquals("the dead subscriber must be dropped", 1, broadcast.subscriberCount(AWID, roomA));
    awaitLatch("the healthy subscriber stopped receiving", second);

    assertEquals(2, healthy.getReceived().size());
  }

  @Test
  public void aSlowSubscriberDoesNotBlockThePublishingThread() throws Exception {
    RecordingEmitter slow = new RecordingEmitter();
    RecordingEmitter fast = new RecordingEmitter();
    subscribeQuietly(roomA, slow);
    subscribeQuietly(roomA, fast);

    releaseSlowSend = new CountDownLatch(1);
    CountDownLatch sendStarted = new CountDownLatch(1);
    slow.blockSendUntil(releaseSlowSend, sendStarted);

    CountDownLatch firstDelivered = fast.expect(1);
    publish(roomA, "one");
    awaitLatch("the slow client never started writing", sendStarted);
    awaitLatch("the fast client did not get the first event", firstDelivered);

    // The slow client is now parked inside send(). Publishing must not wait for it.
    CountDownLatch secondDelivered = fast.expect(1);
    long startedAt = System.nanoTime();
    publish(roomA, "two");
    long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

    // Dispatch used to run the network writes inside one global synchronized block, so a single
    // stalled client held up every room's simulation.
    assertTrue("publishing blocked for " + elapsedMs + " ms", elapsedMs < 1_000);
    awaitLatch("the fast client did not get the second event", secondDelivered);
  }

  @Test
  public void aLongPollIsWokenOnlyByItsOwnRoom() throws Exception {
    final AtomicReference<PollDtoOut> result = new AtomicReference<>();
    Thread poller = new Thread(() -> result.set(broadcast.poll(AWID, PLAYER, new PollDtoIn(roomA))), "poller");
    poller.start();
    awaitPollHolderRegistered(roomA);

    publish(roomB, "for-b");
    publish(roomA, "for-a");

    poller.join(WAIT_MS);
    assertNotNull("the long-poll never returned", result.get());
    // With the old server-wide dispatch the poll would have come back holding room B's payload.
    assertEquals("for-a", result.get().getOutput());
  }

  @Test
  public void unsubscribingRemovesTheSubscriberFromItsRoom() throws Exception {
    RecordingEmitter emitter = new RecordingEmitter();
    subscribeQuietly(roomA, emitter);
    assertEquals(1, broadcast.subscriberCount(AWID, roomA));

    // The container signals a finished response through the completion callback.
    emitter.fireCompletion();

    assertEquals(0, broadcast.subscriberCount(AWID, roomA));
  }

  // -------------------------------------------------------------------------------------------
  // Access control
  // -------------------------------------------------------------------------------------------

  @Test
  public void someoneWhoNeverJoinedTheRoomCannotSubscribeToIt() {
    RecordingEmitter emitter = new RecordingEmitter();
    try {
      broadcast.subscribe(AWID, roomA, STRANGER, emitter);
      fail("a room id must not be enough to watch a match");
    } catch (RoomAblRuntimeException expected) {
      assertEquals(RoomAblRuntimeException.Error.NOT_ROOM_MEMBER.getCode().getErrorCode(),
        expected.getCode().getErrorCode());
    }
    // Rejected before anything is allocated, so an outsider cannot even cost us a connection.
    assertEquals(0, broadcast.subscriberCount(AWID, roomA));
  }

  @Test
  public void someoneWhoNeverJoinedTheRoomCannotLongPollItEither() {
    try {
      broadcast.poll(AWID, STRANGER, new PollDtoIn(roomA));
      fail("long-polling carries the same state as the stream and must be gated the same way");
    } catch (RoomAblRuntimeException expected) {
      assertEquals(RoomAblRuntimeException.Error.NOT_ROOM_MEMBER.getCode().getErrorCode(),
        expected.getCode().getErrorCode());
    }
  }

  // -------------------------------------------------------------------------------------------
  // Initial state
  // -------------------------------------------------------------------------------------------

  @Test
  public void aClientThatConnectsMidMatchIsSentTheFullStateWithoutWaitingForATick() throws Exception {
    fixture.startGame(roomA, OWNER);

    RecordingEmitter emitter = new RecordingEmitter();
    CountDownLatch snapshot = emitter.expect(1);
    broadcast.subscribe(AWID, roomA, PLAYER, emitter);
    awaitLatch("nothing was sent on connect", snapshot);

    Object payload = ((PollDtoOut) emitter.getReceived().get(0)).getOutput();
    assertTrue("expected a game state, got " + payload, payload instanceof GameState);
    // Per-tick payloads carry only what changed, so a client handed one of those on connect would
    // never receive the arena and would stay blank for the rest of the match.
    assertTrue("the first frame must be a full state", ((GameState<?>) payload).isFull());
  }

  // -------------------------------------------------------------------------------------------
  // Presence
  // -------------------------------------------------------------------------------------------

  @Test
  public void aClosedStreamCostsThePlayerTheirPlaceOnceTheGracePeriodHasRunOut() throws Exception {
    fixture.startGame(roomA, OWNER);
    RecordingEmitter emitter = new RecordingEmitter();
    subscribeQuietly(roomA, emitter);
    long now = System.currentTimeMillis();

    emitter.fireCompletion();

    fixture.applyPendingDisconnects(now);
    assertFalse("a dropped connection must not remove a player on the spot",
      PlayerStateEnum.DONE == enginePlayer(roomA, PLAYER).getState());

    fixture.applyPendingDisconnects(now + GRACE_MILLIS + 1);
    // Nothing used to call removePlayer at all, so a closed tab stayed on the board as a motionless
    // target for the rest of the match.
    assertEquals(PlayerStateEnum.DONE, enginePlayer(roomA, PLAYER).getState());
  }

  @Test
  public void aSecondOpenStreamKeepsThePlayerInTheMatch() throws Exception {
    fixture.startGame(roomA, OWNER);
    RecordingEmitter firstTab = new RecordingEmitter();
    RecordingEmitter secondTab = new RecordingEmitter();
    subscribeQuietly(roomA, firstTab);
    subscribeQuietly(roomA, secondTab);

    firstTab.fireCompletion();

    fixture.applyPendingDisconnects(System.currentTimeMillis() + GRACE_MILLIS + 1);
    // Closing one of two tabs - or a reconnect that overlapped the old connection - is not leaving.
    assertFalse("the player was removed although they still had a stream open",
      PlayerStateEnum.DONE == enginePlayer(roomA, PLAYER).getState());
  }

  @Test
  public void reconnectingWithinTheGracePeriodCallsOffTheRemoval() throws Exception {
    fixture.startGame(roomA, OWNER);
    RecordingEmitter first = new RecordingEmitter();
    subscribeQuietly(roomA, first);

    first.fireCompletion();
    RecordingEmitter reconnected = new RecordingEmitter();
    subscribeQuietly(roomA, reconnected);

    fixture.applyPendingDisconnects(System.currentTimeMillis() + GRACE_MILLIS + 1);
    // A refresh closes and reopens the stream in well under a second; removing on the first
    // onCompletion would kill players who are still playing.
    assertFalse("a refresh cost the player their match",
      PlayerStateEnum.DONE == enginePlayer(roomA, PLAYER).getState());
  }

  @SuppressWarnings("unchecked")
  private void awaitPollHolderRegistered(String roomId) throws Exception {
    Field field = BroadcastAbl.class.getDeclaredField("pollHolders");
    field.setAccessible(true);
    Map<String, Queue<?>> holders = (Map<String, Queue<?>>) field.get(broadcast);

    long deadline = System.currentTimeMillis() + WAIT_MS;
    while (System.currentTimeMillis() < deadline) {
      Queue<?> queue = holders.get(AWID + "/" + roomId);
      if (queue != null && !queue.isEmpty()) {
        return;
      }
      Thread.sleep(5);
    }
    fail("the long-poll never registered itself");
  }
}
