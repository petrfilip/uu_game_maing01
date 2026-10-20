package uu.game.main.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static uu.game.main.abl.RoomAblFixture.AWID;
import static uu.game.main.abl.RoomAblFixture.GAME;

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
import uu.game.main.api.dto.MetricsGameDtoOut;
import uu.game.main.api.dto.MetricsGetDtoOut;
import uu.game.main.engine.TickObserver;
import uu.game.main.metrics.GameMetrics;
import uu.game.main.metrics.MetricsAbl;

/**
 * The reading a human gets from {@code metrics/get}: the tick counters, the engine registry and the
 * broadcast fan-out, in one answer.
 *
 * <p>The mapping is the whole risk here. Every number on the way out is a {@code long} or an
 * {@code int} with a plausible neighbour - subscribers next to rooms-with-subscribers, live rooms
 * next to running matches, p90 next to p99, dropped frames next to failed streams - so a transposed
 * pair compiles, ships, and quietly reports the wrong thing forever. Every pair is therefore given
 * a different value before it is asserted on.
 */
public class MetricsAblTest {

  private static final long WAIT_MS = 5_000;
  private static final long SINCE_MILLIS = 1_700_000_000_000L;
  private static final long PERIOD_MILLIS = 50;
  private static final String OWNER = "alice";
  private static final String PLAYER = "bob";

  private RoomAblFixture fixture;
  private BroadcastAbl broadcast;
  private GameMetrics gameMetrics;
  private MetricsAbl metricsAbl;
  private CountDownLatch releaseSlowSend;

  private String roomA;

  private static final class TestEvent extends GameServerEvent<String> {

    private TestEvent(String awid, String roomId, String payload) {
      super("test", awid, roomId, payload);
    }
  }

  @Before
  public void setUp() throws Exception {
    fixture = new RoomAblFixture(60_000, 100, 15_000);
    broadcast = new BroadcastAbl();
    set(broadcast, "roomAbl", fixture.roomAbl());

    gameMetrics = new GameMetrics(SINCE_MILLIS);

    metricsAbl = new MetricsAbl();
    set(metricsAbl, "gameMetrics", gameMetrics);
    set(metricsAbl, "gameEngineRegistry", fixture.registry());
    set(metricsAbl, "broadcastAbl", broadcast);

    Room a = fixture.createRoom(OWNER);
    Room b = fixture.createRoom(OWNER);
    roomA = a.getId();
    fixture.join(roomA, PLAYER);
    fixture.join(b.getId(), PLAYER);
  }

  @After
  public void tearDown() {
    if (releaseSlowSend != null) {
      releaseSlowSend.countDown();
    }
    broadcast.shutdown();
    fixture.shutdown();
  }

  private static void set(Object target, String field, Object value) throws Exception {
    Field declared = target.getClass().getDeclaredField(field);
    declared.setAccessible(true);
    declared.set(target, value);
  }

  private void subscribeQuietly(String roomId, RecordingEmitter emitter) throws Exception {
    CountDownLatch snapshot = emitter.expect(1);
    broadcast.subscribe(AWID, roomId, PLAYER, emitter);
    assertTrue("the initial snapshot never arrived", snapshot.await(WAIT_MS, TimeUnit.MILLISECONDS));
    emitter.clearReceived();
  }

  private void publish(String payload) {
    broadcast.onApplicationEvent(new TestEvent(AWID, roomA, payload));
  }

  private static int maxQueuedEvents() throws Exception {
    Field field = Class.forName("uu.game.main.abl.SseSubscriber").getDeclaredField("MAX_QUEUED_EVENTS");
    field.setAccessible(true);
    return field.getInt(null);
  }

  /** A spread of tick durations, so that the three percentiles cannot be confused for each other. */
  private void recordTicks() {
    TickObserver observer = gameMetrics.observerFor(GAME, PERIOD_MILLIS);
    for (int i = 0; i < 50; i++) {
      observer.onTick(GAME, TimeUnit.MILLISECONDS.toNanos(1), false);
    }
    for (int i = 0; i < 40; i++) {
      observer.onTick(GAME, TimeUnit.MILLISECONDS.toNanos(10), false);
    }
    for (int i = 0; i < 7; i++) {
      observer.onTick(GAME, TimeUnit.MILLISECONDS.toNanos(200), false);
    }
    for (int i = 0; i < 3; i++) {
      observer.onTick(GAME, TimeUnit.MILLISECONDS.toNanos(200), true);
    }
  }

  @Test
  public void theReadingReportsWhatEachLayerKnows() throws Exception {
    fixture.startGame(roomA, OWNER);

    RecordingEmitter healthy = new RecordingEmitter();
    RecordingEmitter stalled = new RecordingEmitter();
    subscribeQuietly(roomA, healthy);
    subscribeQuietly(roomA, stalled);

    releaseSlowSend = new CountDownLatch(1);
    CountDownLatch sendStarted = new CountDownLatch(1);
    stalled.blockSendUntil(releaseSlowSend, sendStarted);
    publish("parks-the-writer");
    assertTrue("the client never started writing", sendStarted.await(WAIT_MS, TimeUnit.MILLISECONDS));

    int overflow = 8;
    for (int i = 0; i < maxQueuedEvents() + overflow; i++) {
      publish("frame-" + i);
    }

    recordTicks();

    MetricsGetDtoOut dtoOut = metricsAbl.get();

    assertEquals(SINCE_MILLIS, dtoOut.getSinceMillis());
    // The period is what "overrun" is measured against, so a reading is unreadable without it.
    assertEquals(PERIOD_MILLIS, dtoOut.getTickMillis());
    assertEquals(2, dtoOut.getLiveRooms());
    assertEquals(1, dtoOut.getRunningMatches());
    assertEquals(2, dtoOut.getSubscribers());
    assertEquals(1, dtoOut.getRoomsWithSubscribers());
    assertEquals(overflow, dtoOut.getDroppedFrames());
    assertEquals(0, dtoOut.getFailedStreams());

    assertEquals(1, dtoOut.getGames().size());
    MetricsGameDtoOut game = dtoOut.getGames().get(0);
    assertEquals(GAME, game.getGameName());
    assertEquals(100, game.getTicks());
    assertEquals(10, game.getOverruns());
    assertEquals(3, game.getFailed());
    assertEquals(1_024, game.getP50Micros());
    assertEquals(10_240, game.getP90Micros());
    assertEquals(200_000, game.getP99Micros());
    assertEquals(200_000, game.getMaxMicros());
  }

  @Test
  public void anIdleServerReportsAnEmptyReadingRatherThanFailing() {
    MetricsGetDtoOut dtoOut = metricsAbl.get();

    // No match has ever run, so there is nothing to say about ticks - but a monitoring call that
    // only works once the server is busy is no use at all.
    assertTrue(dtoOut.getGames().isEmpty());
    assertEquals(0, dtoOut.getRunningMatches());
    assertEquals(0, dtoOut.getSubscribers());
    assertEquals(2, dtoOut.getLiveRooms());
  }

  @Test
  public void aReclaimedRoomStopsBeingCounted() {
    assertEquals(2, metricsAbl.get().getLiveRooms());

    fixture.registry().reclaimIdle(System.currentTimeMillis() + 120_000);

    // This is the number that used to grow forever; if it does not fall when rooms are reclaimed
    // it cannot be used to spot the leak coming back.
    assertEquals(0, metricsAbl.get().getLiveRooms());
  }
}
