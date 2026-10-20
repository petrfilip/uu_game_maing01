package uu.game.main.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import uu.game.main.engine.TickObserver;

/**
 * The server's tick measurements, one set of counters per game.
 *
 * <p>Per game rather than per room on purpose. {@link TickObserver} is handed a game name, which is
 * a Spring bean name and therefore drawn from a closed set - whereas room ids are created by
 * callers, so keying on them would let anyone who can open rooms grow this map without bound. The
 * per-room view a human actually wants ("which match is stuttering") is a different tool; what this
 * answers is "is the simulation keeping up at all", which is the question nothing could answer
 * before.
 *
 * <h2>Hot path</h2>
 * {@link #observerFor} resolves the game's counters and the overrun threshold <em>once</em>, when a
 * room's runtime is created, and returns an observer bound to them. A tick therefore costs no map
 * lookup, no string hashing and no allocation - see {@link GameTickMetrics#record}.
 */
@Component
public class GameMetrics {

  private final ConcurrentMap<String, GameTickMetrics> byGame = new ConcurrentHashMap<>();

  /** When these counters started, so a reader can tell "3 overruns" this hour from this month. */
  private final long sinceMillis;

  public GameMetrics() {
    this(System.currentTimeMillis());
  }

  /** Takes the clock reading rather than calling one, so a test never has to wait for time. */
  public GameMetrics(long sinceMillis) {
    this.sinceMillis = sinceMillis;
  }

  /**
   * The observer to hand to one game's runtime.
   *
   * @param gameName the game whose counters the ticks belong to
   * @param tickMillis the engine's configured period; a tick longer than this is an overrun
   */
  public TickObserver observerFor(String gameName, long tickMillis) {
    GameTickMetrics metrics = metricsFor(gameName);
    return new BoundObserver(metrics, TimeUnit.MILLISECONDS.toNanos(tickMillis));
  }

  /** The counters of one game, created on first use. */
  private GameTickMetrics metricsFor(String gameName) {
    GameTickMetrics existing = byGame.get(gameName);
    if (existing != null) {
      return existing;
    }
    GameTickMetrics created = new GameTickMetrics();
    GameTickMetrics raced = byGame.putIfAbsent(gameName, created);
    return raced == null ? created : raced;
  }

  /** One reading per game that has ever ticked, ordered by name so the output is stable. */
  public List<GameTickSnapshot> snapshot() {
    Map<String, GameTickMetrics> ordered = new TreeMap<>(byGame);
    List<GameTickSnapshot> snapshots = new ArrayList<>(ordered.size());
    for (Map.Entry<String, GameTickMetrics> entry : ordered.entrySet()) {
      snapshots.add(entry.getValue().snapshot(entry.getKey()));
    }
    return snapshots;
  }

  public long getSinceMillis() {
    return sinceMillis;
  }

  /**
   * An observer that already knows which counters it feeds and what "too slow" means, so the tick
   * thread does nothing but arithmetic and atomic increments.
   */
  private static final class BoundObserver implements TickObserver {

    private final GameTickMetrics metrics;
    private final long periodNanos;

    private BoundObserver(GameTickMetrics metrics, long periodNanos) {
      this.metrics = metrics;
      this.periodNanos = periodNanos;
    }

    @Override
    public void onTick(String gameName, long durationNanos, boolean failed) {
      metrics.record(durationNanos, periodNanos, failed);
    }
  }
}
