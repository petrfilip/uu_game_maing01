package uu.game.main.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import uu.game.main.domain.IRule;
import uu.game.main.metrics.GameMetrics;

/**
 * Holds the live {@link GameEngine} of every room and owns the one thread pool that ticks them all.
 *
 * <p>Separating the runtime from the {@code Room} entity is what makes the persistence layer honest
 * again: a room is now plain data that any DAO can store, and the engine lives here for exactly as
 * long as the room is in use.
 *
 * <p>The pool replaces the per-game {@code ScheduledThreadPoolExecutor} that the old code created on
 * every {@code startGame} and never shut down - each restart of a game leaked a thread for the
 * lifetime of the JVM.
 *
 * <h2>Reclamation</h2>
 * An engine used to live until the process died: a server that had run a hundred matches still held
 * a hundred rules, a hundred boards and a hundred rosters. Every entry now carries the time it was
 * last used, and a sweep drops the ones nobody has touched for {@code game.engine.idleRoomMillis}.
 * The sweep runs on the tick pool rather than on a scheduler of its own - it is a handful of map
 * reads once a minute, and a second pool would be a second thing to size, name and shut down.
 */
@Component
public class GameEngineRegistry {

  private static final Logger LOGGER = LogManager.getLogger(GameEngineRegistry.class);

  /**
   * Told when the registry reclaims an idle room, so the room itself can be cleaned up too.
   *
   * <p>A callback rather than a {@code RoomDao} injected here: the registry is the runtime layer and
   * has no business knowing how - or whether - a room is persisted.
   */
  public interface ReclaimListener {

    void onRoomReclaimed(String awid, String roomId);
  }

  @Inject
  private ApplicationContext applicationContext;

  @Inject
  private ObjectMapper objectMapper;

  /**
   * Where every engine reports its ticks.
   *
   * <p>Read into a local before use and allowed to be {@code null}: the registry is constructed
   * directly, field by field, by test fixtures that predate measurement and know nothing about it,
   * and a room that cannot be created is a far worse outcome than a room nobody is timing.
   */
  @Inject
  private GameMetrics gameMetrics;

  /**
   * Simulation period. 50 ms (20 ticks/s) is a round number that divides cleanly into the client's
   * render cadence and keeps per-tick movement small enough that collisions stay precise.
   */
  @Value("${game.engine.tickMillis:50}")
  private long tickMillis;

  @Value("${game.engine.schedulerThreads:4}")
  private int schedulerThreads;

  /**
   * How long a room may go untouched before it is reclaimed. Generous on purpose: a lobby where
   * people are waiting for a friend makes no server calls at all, and 15 minutes is far longer than
   * anyone waits before giving up.
   */
  @Value("${game.engine.idleRoomMillis:900000}")
  private long idleRoomMillis = 900_000;

  @Value("${game.engine.reclaimIntervalMillis:60000}")
  private long reclaimIntervalMillis = 60_000;

  private final Map<String, Entry> engines = new ConcurrentHashMap<>();
  private final List<ReclaimListener> reclaimListeners = new CopyOnWriteArrayList<>();

  private volatile ScheduledExecutorService scheduler;

  /** An engine plus when it was last used, which is all "idle" means here. */
  private static final class Entry {

    private final String awid;
    private final String roomId;
    private final GameEngine engine;

    /** Written from request threads, read by the sweep; volatile is enough, it is only a hint. */
    private volatile long lastUsedMillis;

    private Entry(String awid, String roomId, GameEngine engine, long nowMillis) {
      this.awid = awid;
      this.roomId = roomId;
      this.engine = engine;
      this.lastUsedMillis = nowMillis;
    }
  }

  private synchronized ScheduledExecutorService scheduler() {
    if (scheduler == null) {
      AtomicInteger counter = new AtomicInteger();
      ThreadFactory threadFactory = runnable -> {
        Thread thread = new Thread(runnable, "game-tick-" + counter.incrementAndGet());
        // Daemon: a forgotten running game must never keep the JVM alive.
        thread.setDaemon(true);
        return thread;
      };
      scheduler = Executors.newScheduledThreadPool(schedulerThreads, threadFactory);
      scheduler.scheduleWithFixedDelay(this::reclaimIdleQuietly,
        reclaimIntervalMillis, reclaimIntervalMillis, TimeUnit.MILLISECONDS);
    }
    return scheduler;
  }

  /**
   * Runs a one-off task on the tick pool. Exists so callers that need a short timer - the
   * disconnect grace period, for instance - do not each start a scheduler of their own.
   */
  public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
    return scheduler().schedule(task, delay, unit);
  }

  public void addReclaimListener(ReclaimListener listener) {
    reclaimListeners.add(listener);
  }

  /**
   * Creates the runtime for a room. The game name is resolved to an {@link IRule} bean, which is
   * what keeps games pluggable - a new game is a new {@code @Service("name")} and nothing else.
   */
  public GameEngine create(String awid, String roomId, String gameName) {
    IRule<?, ?> rule = resolveRule(gameName);
    GameEngine engine = new GameEngine(gameName, rule, objectMapper, scheduler(), tickMillis);

    // Here rather than inside the engine: the engine measures its ticks and hands the numbers to
    // whoever asked for them, and deciding who that is belongs to the layer that owns the runtime.
    // Resolved once, per engine, so a tick never pays for a lookup.
    GameMetrics metrics = this.gameMetrics;
    if (metrics != null) {
      engine.setTickObserver(metrics.observerFor(gameName, engine.getTickMillis()));
    }

    Entry replaced = engines.put(key(awid, roomId), new Entry(awid, roomId, engine, System.currentTimeMillis()));
    if (replaced != null) {
      // Recreating over a live engine used to orphan it: it kept ticking, kept publishing and could
      // never be reached again, so it could not even be stopped.
      replaced.engine.stop();
    }
    return engine;
  }

  /**
   * @throws IllegalArgumentException when no rule is registered under that name
   */
  public void requireGame(String gameName) {
    resolveRule(gameName);
  }

  public GameEngine get(String awid, String roomId) {
    GameEngine engine = find(awid, roomId);
    if (engine == null) {
      throw new IllegalStateException("No running game for room " + roomId);
    }
    return engine;
  }

  public GameEngine find(String awid, String roomId) {
    Entry entry = engines.get(key(awid, roomId));
    if (entry == null) {
      return null;
    }
    // Every lookup counts as use. Joining, starting and submitting input all go through here, so
    // the sweep does not need a separate notion of "activity" that callers could forget to feed.
    entry.lastUsedMillis = System.currentTimeMillis();
    return entry.engine;
  }

  /** The configured tick period, which is also the threshold above which a tick is an overrun. */
  public long getTickMillis() {
    return tickMillis;
  }

  /**
   * How many rooms still hold a runtime. This is the number that used to grow forever, so it is the
   * first thing to look at when the process is using more memory than it should.
   */
  public int liveRoomCount() {
    return engines.size();
  }

  /** The subset of {@link #liveRoomCount()} whose simulation is actually ticking. */
  public int runningMatchCount() {
    int running = 0;
    for (Entry entry : engines.values()) {
      if (entry.engine.isRunning()) {
        running++;
      }
    }
    return running;
  }

  public void remove(String awid, String roomId) {
    Entry entry = engines.remove(key(awid, roomId));
    if (entry != null) {
      entry.engine.stop();
    }
  }

  /**
   * Drops every room that is neither running a match nor has been touched recently, stopping its
   * engine and telling the listeners so the room itself can go too.
   *
   * <p>Public and taking the clock as an argument so it can be triggered - and tested - without
   * waiting out a sweep interval.
   *
   * @param nowMillis the clock to judge against
   * @return how many rooms were reclaimed
   */
  public int reclaimIdle(long nowMillis) {
    int reclaimed = 0;
    for (Map.Entry<String, Entry> mapEntry : engines.entrySet()) {
      Entry entry = mapEntry.getValue();
      // A match that is still ticking is never idle however long it has run: the players are there,
      // they are simply not making calls the registry sees.
      if (entry.engine.isRunning() || nowMillis - entry.lastUsedMillis < idleRoomMillis) {
        continue;
      }
      // Two-argument remove: if someone recreated the room between the check and here, the new
      // entry must survive.
      if (!engines.remove(mapEntry.getKey(), entry)) {
        continue;
      }
      entry.engine.stop();
      reclaimed++;
      LOGGER.info("Reclaimed idle room {}", entry.roomId);
      for (ReclaimListener listener : reclaimListeners) {
        try {
          listener.onRoomReclaimed(entry.awid, entry.roomId);
        } catch (RuntimeException e) {
          LOGGER.warn("Reclaim listener failed for room {}", entry.roomId, e);
        }
      }
    }
    return reclaimed;
  }

  private void reclaimIdleQuietly() {
    try {
      reclaimIdle(System.currentTimeMillis());
    } catch (Exception e) {
      // A scheduled task that throws is dropped by the executor, so cleanup would silently stop.
      LOGGER.error("Idle room sweep failed", e);
    }
  }

  private IRule<?, ?> resolveRule(String gameName) {
    try {
      // Prototype scoped: every room gets its own rule instance, so per-game state held by a rule
      // (gameplay mode, bonus drop timer) cannot bleed between rooms.
      return applicationContext.getBean(gameName, IRule.class);
    } catch (BeansException e) {
      throw new IllegalArgumentException("Game " + gameName + " not found!", e);
    }
  }

  private String key(String awid, String roomId) {
    return awid + "/" + roomId;
  }

  @PreDestroy
  public void shutdown() {
    engines.values().forEach(entry -> entry.engine.stop());
    engines.clear();

    ScheduledExecutorService executor = scheduler;
    if (executor == null) {
      return;
    }
    executor.shutdown();
    try {
      if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
    LOGGER.info("Game engine scheduler stopped");
  }
}
