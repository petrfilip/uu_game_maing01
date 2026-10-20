package uu.game.main.engine;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import uu.game.main.abl.dto.Player;
import uu.game.main.domain.GameState;
import uu.game.main.metrics.GameMetrics;
import uu.game.main.metrics.GameTickSnapshot;

public class GameEngineRegistryTest {

  private static final String GAME = "fake";
  private static final String AWID = "awid-1";
  private static final int SCHEDULER_THREADS = 2;
  private static final String TICK_THREAD_PREFIX = "game-tick-";
  private static final long IDLE_MILLIS = 60_000;

  private static final GameCallback IGNORING_CALLBACK = new GameCallback() {
    @Override
    public void onGameEnd(GameState gameState) {
      // no-op
    }

    @Override
    public void onNextRound(GameState gameState) {
      // no-op
    }
  };

  private GameEngineRegistry registry;
  private final List<String> reclaimedRooms = new ArrayList<>();

  /** The rule handed to the most recently created engine, so a test can steer that match. */
  private volatile FakeRule lastRule;

  @Before
  public void setUp() throws Exception {
    registry = new GameEngineRegistry();

    // Stands in for the Spring context: the registry resolves a game name to an IRule bean, and
    // nothing else about the context matters here.
    ApplicationContext context = (ApplicationContext) Proxy.newProxyInstance(
      getClass().getClassLoader(),
      new Class<?>[] {ApplicationContext.class},
      (proxy, method, args) -> {
        if ("getBean".equals(method.getName()) && args != null && args.length == 2) {
          if (GAME.equals(args[0])) {
            lastRule = new FakeRule();
            return lastRule;
          }
          throw new NoSuchBeanDefinitionException(String.valueOf(args[0]));
        }
        if ("hashCode".equals(method.getName())) {
          return System.identityHashCode(proxy);
        }
        if ("equals".equals(method.getName())) {
          return proxy == args[0];
        }
        if ("toString".equals(method.getName())) {
          return "fakeApplicationContext";
        }
        return null;
      });

    set("applicationContext", context);
    set("objectMapper", new ObjectMapper());
    set("tickMillis", 10L);
    set("schedulerThreads", SCHEDULER_THREADS);
    set("idleRoomMillis", IDLE_MILLIS);
    set("reclaimIntervalMillis", 60_000L);
    registry.addReclaimListener((awid, roomId) -> reclaimedRooms.add(roomId));
  }

  /**
   * Replaces the pool with one that records the tick task instead of running it, so a test can
   * drive a match by hand and inspect what was cancelled.
   */
  private CapturingScheduler captureTicks() throws Exception {
    CapturingScheduler captured = new CapturingScheduler();
    set("scheduler", captured);
    return captured;
  }

  @After
  public void tearDown() throws Exception {
    registry.shutdown();
    // shutdown() waits for the pool to terminate; give the worker threads a bounded moment to
    // actually leave so a later test never sees them.
    for (int i = 0; i < 40 && countTickThreads() > 0; i++) {
      Thread.sleep(25);
    }
  }

  private void set(String field, Object value) throws Exception {
    Field declared = GameEngineRegistry.class.getDeclaredField(field);
    declared.setAccessible(true);
    declared.set(registry, value);
  }

  private ExecutorService scheduler() throws Exception {
    Field declared = GameEngineRegistry.class.getDeclaredField("scheduler");
    declared.setAccessible(true);
    return (ExecutorService) declared.get(registry);
  }

  private static int countTickThreads() {
    int count = 0;
    for (Thread thread : Thread.getAllStackTraces().keySet()) {
      if (thread.getName().startsWith(TICK_THREAD_PREFIX) && thread.isAlive()) {
        count++;
      }
    }
    return count;
  }

  private GameEngine startRoom(String roomId) {
    GameEngine engine = registry.create(AWID, roomId, GAME);
    engine.addPlayer(new Player("p1"));
    engine.startGame(IGNORING_CALLBACK, new HashMap<String, Object>());
    return engine;
  }

  @Test
  public void repeatedCreateAndStopCyclesDoNotLeakTickThreads() {
    for (int i = 0; i < 12; i++) {
      startRoom("room-" + i);
      registry.remove(AWID, "room-" + i);
    }

    // The old code built a fresh ScheduledThreadPoolExecutor per game and never shut it down, so
    // every start leaked a thread for the lifetime of the JVM.
    assertTrue("leaked tick threads: " + countTickThreads(), countTickThreads() <= SCHEDULER_THREADS);
  }

  @Test
  public void everyGameSharesTheOneScheduler() throws Exception {
    startRoom("room-a");
    ExecutorService first = scheduler();
    startRoom("room-b");

    assertNotNull(first);
    assertSame(first, scheduler());
  }

  @Test
  public void shutdownStopsTheSharedPool() throws Exception {
    startRoom("room-a");
    ExecutorService pool = scheduler();

    registry.shutdown();

    assertTrue(pool.isShutdown());
    assertNull("engines must be dropped too", registry.find(AWID, "room-a"));
  }

  @Test
  public void createFailsFastForAnUnknownGame() {
    try {
      registry.create(AWID, "room-a", "tetris");
      fail("expected an unknown game to be rejected when the room is created");
    } catch (IllegalArgumentException expected) {
      assertNotNull(expected.getCause());
    }
  }

  @Test
  public void roomsAreKeyedByWorkspaceAsWellAsRoomId() {
    GameEngine first = startRoom("room-a");
    GameEngine second = registry.create("other-awid", "room-a", GAME);

    assertSame(first, registry.get(AWID, "room-a"));
    assertSame(second, registry.get("other-awid", "room-a"));
  }

  @Test(expected = IllegalStateException.class)
  public void getFailsLoudlyForARoomThatHasNoRuntime() {
    registry.get(AWID, "never-created");
  }

  @Test
  public void aRoomNobodyHasTouchedIsReclaimed() {
    registry.create(AWID, "room-a", GAME);

    assertEquals("a room must not be reclaimed before its timeout",
      0, registry.reclaimIdle(System.currentTimeMillis()));
    assertEquals(1, registry.reclaimIdle(System.currentTimeMillis() + IDLE_MILLIS + 1));

    // An engine used to live for the lifetime of the process, holding its rule, board and roster
    // long after the last player had gone.
    assertNull(registry.find(AWID, "room-a"));
    assertEquals(singletonList("room-a"), reclaimedRooms);
  }

  @Test
  public void aMatchThatIsStillRunningIsNeverReclaimed() {
    startRoom("room-a");

    // The players are there; they are simply not making calls the registry can see. Reclaiming on
    // elapsed time alone would end matches out from under them.
    assertEquals(0, registry.reclaimIdle(System.currentTimeMillis() + 100 * IDLE_MILLIS));
    assertNotNull(registry.find(AWID, "room-a"));
    assertTrue(reclaimedRooms.isEmpty());
  }

  @Test
  public void aFinishedMatchIsReclaimedOnceTheRoomGoesQuiet() throws Exception {
    captureTicks();
    GameEngine engine = startRoom("room-a");
    lastRule.finishAtTick(1);
    engine.calculateNextState();
    assertFalse("the match should be over", engine.isRunning());

    assertEquals(1, registry.reclaimIdle(System.currentTimeMillis() + IDLE_MILLIS + 1));
    assertNull(registry.find(AWID, "room-a"));
  }

  @Test
  public void removingARoomStopsItsTickLoop() throws Exception {
    CapturingScheduler captured = captureTicks();
    startRoom("room-a");

    registry.remove(AWID, "room-a");

    assertTrue("the tick loop outlived the room", captured.future(0).isCancelledByEngine());
    assertNull(registry.find(AWID, "room-a"));
  }

  @Test
  public void recreatingARoomStopsTheEngineItReplaces() throws Exception {
    CapturingScheduler captured = captureTicks();
    startRoom("room-a");

    registry.create(AWID, "room-a", GAME);

    // The replaced engine is unreachable, so if it is not stopped here it can never be stopped at
    // all - it just keeps ticking and publishing for a room that has moved on.
    assertTrue("the replaced engine is still ticking", captured.future(0).isCancelledByEngine());
  }

  @Test
  public void findReturnsNullForARoomThatHasNoRuntime() {
    assertNull(registry.find(AWID, "never-created"));
  }

  // -------------------------------------------------------------------------------------------
  // Measurement
  // -------------------------------------------------------------------------------------------

  @Test
  public void everyEngineTheRegistryCreatesReportsItsTicks() throws Exception {
    GameMetrics metrics = new GameMetrics(0L);
    set("gameMetrics", metrics);
    CapturingScheduler captured = captureTicks();
    startRoom("room-a");

    captured.lastTask().run();

    // Registering the observer is the registry's job - the engine measures every tick either way,
    // and without this line the numbers are collected and handed to nobody.
    List<GameTickSnapshot> snapshots = metrics.snapshot();
    assertEquals(1, snapshots.size());
    assertEquals(GAME, snapshots.get(0).getGameName());
    assertEquals(1, snapshots.get(0).getTicks());
  }

  @Test
  public void twoRoomsOfTheSameGameShareOneSetOfCounters() throws Exception {
    GameMetrics metrics = new GameMetrics(0L);
    set("gameMetrics", metrics);
    CapturingScheduler captured = captureTicks();
    startRoom("room-a");
    startRoom("room-b");

    captured.task(0).run();
    captured.task(1).run();

    // Keyed by game, not by room: room ids come from callers, so keying on them would let anyone
    // who can open rooms grow the metrics map without bound.
    assertEquals(1, metrics.snapshot().size());
    assertEquals(2, metrics.snapshot().get(0).getTicks());
  }

  @Test
  public void aRegistryThatWasNeverGivenMetricsStillTicks() throws Exception {
    CapturingScheduler captured = captureTicks();
    startRoom("room-a");

    // The registry is built field by field by fixtures that know nothing about measurement. A room
    // that cannot tick because nobody is counting would be a spectacular own goal.
    captured.lastTask().run();

    assertEquals(1, lastRule.getTicks());
  }

  @Test
  public void theRegistryCountsTheRoomsAndMatchesItIsHolding() throws Exception {
    captureTicks();
    registry.create(AWID, "waiting-room", GAME);
    startRoom("playing-room");

    assertEquals(2, registry.liveRoomCount());
    // A room that exists is not a room that is simulating; conflating the two hides both an idle
    // lobby leak and a server with nothing running on it.
    assertEquals(1, registry.runningMatchCount());

    registry.remove(AWID, "playing-room");
    assertEquals(1, registry.liveRoomCount());
    assertEquals(0, registry.runningMatchCount());
  }
}
