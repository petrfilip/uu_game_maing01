package uu.game.main.engine;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uu.game.main.abl.dto.Player;
import uu.game.main.abl.dto.PlayerStateEnum;
import uu.game.main.domain.GameState;
import uu.game.main.domain.GameStateEnum;
import uu.game.main.domain.IRule;
import uu.game.main.game.common.GameSetup;
import uu.game.main.game.common.PlayerMove;

/**
 * Runtime of a single game: owns the authoritative state, buffers player input and drives the tick.
 *
 * <p>This is deliberately <em>not</em> a Spring bean and is not reachable from a persisted entity.
 * It used to be {@code GameInstanceAbl}, stored directly on the {@code Room} entity, which meant a
 * live object with a running scheduler was a field of something the DAO was supposed to save. See
 * {@link GameEngineRegistry} for where instances live now.
 *
 * <h2>Threading</h2>
 * Two kinds of thread touch an engine: HTTP request threads (join, leave, submit moves) and the
 * single tick thread taken from the registry's shared pool. The contract is:
 * <ul>
 *   <li>{@link #currentState} is written only by the tick thread and is {@code volatile}, so readers
 *       always observe a fully published state object.</li>
 *   <li>Input is handed over through {@link #inbox}, a concurrent queue. Request threads append and
 *       the tick thread drains it, so no lock is taken, no submission can be lost in the hand-over,
 *       and a request thread is never blocked by the simulation.</li>
 *   <li>{@link #players} is a concurrent map, so joins during a running game are safe.</li>
 * </ul>
 * The previous implementation shared a plain {@code HashMap} between request threads and the tick
 * thread with no synchronisation at all.
 */
public class GameEngine {

  private static final Logger LOGGER = LogManager.getLogger(GameEngine.class);

  /**
   * Upper bound on how many moves one player may have applied in a single tick.
   *
   * <p>Without it a client could submit an arbitrarily long move list and have every entry applied,
   * which is a straight teleport across the map. Input is a rate-limited stream, not a batch job.
   */
  public static final int MAX_MOVES_PER_TICK = 4;

  private final String gameName;
  private final IRule<Object, PlayerMove> rule;
  private final ObjectMapper objectMapper;
  private final JavaType moveListType;
  private final ScheduledExecutorService scheduler;
  private final long tickMillis;

  private volatile TickObserver tickObserver = TickObserver.NONE;

  private final Map<String, Player> players = new ConcurrentHashMap<>();

  /**
   * Input handed from request threads to the tick thread.
   *
   * <p>A queue rather than a swapped map: with a map, a request thread could read the current
   * buffer, be descheduled, and write into it after the tick thread had already swapped it out -
   * silently dropping that player's input. Producers only ever append here and the tick thread
   * drains until empty, so nothing can be lost in the hand-over.
   */
  private final Queue<PlayerInput> inbox = new ConcurrentLinkedQueue<>();

  /**
   * Per player id, the highest {@link PlayerMove#getSeq()} this simulation has already decided on.
   *
   * <p>Published on every state as {@link GameState#getLastProcessedSeq()} and used here to discard
   * a submission the simulation has already dealt with. Concurrent because request threads read it
   * in {@link #addPlayerMove} while the tick thread advances it.
   */
  private final Map<String, Integer> lastProcessedSeq = new ConcurrentHashMap<>();

  private final AtomicReference<ScheduledFuture<?>> tickHandle = new AtomicReference<>();

  private volatile GameState<Object> currentState;
  private volatile GameCallback gameCallback;

  /**
   * Static parts of the running match, taken from the rule when the match starts.
   *
   * <p>Held here rather than on the state: a rule may return a brand new state object on every tick
   * and the setup has to survive that, and it must be available to {@link #fullState()} at any
   * moment, not only on the tick that produced it.
   */
  private volatile GameSetup setup;

  @SuppressWarnings("unchecked")
  public GameEngine(String gameName, IRule<?, ?> rule, ObjectMapper objectMapper,
                    ScheduledExecutorService scheduler, long tickMillis) {
    this.gameName = gameName;
    this.rule = (IRule<Object, PlayerMove>) rule;
    this.objectMapper = objectMapper;
    this.scheduler = scheduler;
    this.tickMillis = tickMillis;
    // The engine no longer hardcodes a concrete move class. getMoveClass() existed on IRule from
    // the start for exactly this purpose but was never called, so adding a second game meant
    // editing the engine - and two games could not run side by side.
    this.moveListType = objectMapper.getTypeFactory()
      .constructCollectionType(List.class, rule.getMoveClass());
  }

  public String getGameName() {
    return gameName;
  }

  public GameState<Object> getCurrentState() {
    return currentState;
  }

  /**
   * The state a client needs in order to render a match it has not been following: the live snapshot
   * <em>plus</em> the static parts that per-tick payloads leave out - level geometry, arena and
   * match settings.
   *
   * <p>Called when a client connects, so that everything after it can be a small delta. Never
   * returns {@code null}: a room whose match has not started yet still has to answer a subscriber
   * with something it can render.
   */
  public GameState<Object> fullState() {
    GameState<Object> state = currentState;
    if (state != null) {
      return state.asFullState(setup);
    }

    GameState<Object> waiting = new GameState<>();
    waiting.setPlayers(new ArrayList<>(players.values()));
    return waiting.asFullState(null);
  }

  /**
   * Registers who is told how each tick went; see {@link TickObserver}. Set before the match
   * starts, by whoever owns the runtime.
   */
  public void setTickObserver(TickObserver tickObserver) {
    this.tickObserver = tickObserver == null ? TickObserver.NONE : tickObserver;
  }

  public long getTickMillis() {
    return tickMillis;
  }

  public Collection<Player> getPlayers() {
    return players.values();
  }

  public GameState<Object> addPlayer(Player player) {
    players.putIfAbsent(player.getPlayerId(), player);
    return currentState;
  }

  public GameState<Object> removePlayer(String playerId) {
    Player player = players.get(playerId);
    if (player != null) {
      // Kept in the map so the scoreboard still shows them; the rule sees they are done.
      player.setState(PlayerStateEnum.DONE);
    }
    return currentState;
  }

  /**
   * Buffers a player's input for the next tick. Called from HTTP request threads, never blocks.
   *
   * <p>Moves are appended rather than replacing the previous submission, so a client that manages
   * two submissions inside one tick no longer silently loses the first one. The total a player can
   * have applied in one tick is still capped at {@link #MAX_MOVES_PER_TICK}.
   *
   * <p>Anything at or below the player's acknowledgement watermark is dropped here: these are moves
   * over HTTP, so a submission can be retried or overtaken by a later one, and the client has
   * already been told those are settled. Applying a retry a second time would walk the player a step
   * further than the client counted on and put the two permanently out of step.
   */
  public void addPlayerMove(String playerId, List<Object> rawMoves) {
    GameState<Object> state = currentState;
    if (state == null || state.getState() != GameStateEnum.RUNNING) {
      LOGGER.debug("Ignoring move for {} - game is not running", playerId);
      return;
    }

    Player player = players.get(playerId);
    if (player == null || player.getState() == PlayerStateEnum.DONE) {
      return;
    }

    List<PlayerMove> moves = objectMapper.convertValue(rawMoves, moveListType);
    if (moves == null || moves.isEmpty()) {
      return;
    }

    moves = dropAlreadyProcessed(playerId, moves);
    if (moves.isEmpty()) {
      return;
    }

    inbox.add(new PlayerInput(player, moves));
  }

  /**
   * Acknowledges input that will never be simulated, so that a client stops offering it.
   *
   * <p>Called for submissions rejected before they ever reach the inbox - today, by the per-player
   * rate limiter. The watermark is a promise that nothing at or below it will be applied, and a
   * throttled submission satisfies that promise exactly as a move dropped by the per-tick cap does.
   * Without this the client would replay a batch the server had already decided to ignore on top of
   * every authoritative state it received, and go on doing so until its own buffer evicted it: the
   * player would be left out of step with the server for a second and a half for no visible reason.
   *
   * @param seq the highest sequence number in the rejected submission, or {@code null} for a client
   *     that does not number its input, which has nothing to be told
   */
  public void acknowledgeDiscarded(String playerId, Integer seq) {
    if (playerId == null || seq == null) {
      return;
    }
    lastProcessedSeq.merge(playerId, seq, Math::max);
  }

  /** The highest sequence number settled for a player; {@code null} when none has been. */
  public Integer lastProcessedSeq(String playerId) {
    return lastProcessedSeq.get(playerId);
  }

  /**
   * Filters out input the simulation has already settled - a retry, or a submission that lost a race
   * with a later one.
   *
   * <p>Unnumbered moves are always kept: a client is not obliged to number its input, and silently
   * swallowing those would stop such a player moving altogether.
   */
  private List<PlayerMove> dropAlreadyProcessed(String playerId, List<PlayerMove> moves) {
    Integer processed = lastProcessedSeq.get(playerId);
    if (processed == null) {
      return moves;
    }

    List<PlayerMove> fresh = new ArrayList<>(moves.size());
    for (PlayerMove move : moves) {
      Integer seq = move.getSeq();
      if (seq == null || seq > processed) {
        fresh.add(move);
      }
    }
    return fresh;
  }

  /**
   * Takes everything submitted since the last tick and groups it by player.
   *
   * <p>When a client sends more than {@link #MAX_MOVES_PER_TICK} moves for one tick the newest are
   * kept: they describe the player's most recent intent, and honouring all of them was a straight
   * teleport across the map.
   */
  private Map<Player, List<PlayerMove>> drainInbox() {
    Map<Player, List<PlayerMove>> byPlayer = new HashMap<>();

    PlayerInput input;
    while ((input = inbox.poll()) != null) {
      byPlayer.computeIfAbsent(input.player, player -> new ArrayList<>()).addAll(input.moves);
    }

    for (Map.Entry<Player, List<PlayerMove>> entry : byPlayer.entrySet()) {
      List<PlayerMove> moves = entry.getValue();
      // Before the cap, deliberately - see recordProcessedSeq.
      recordProcessedSeq(entry.getKey().getPlayerId(), moves);
      if (moves.size() > MAX_MOVES_PER_TICK) {
        entry.setValue(new ArrayList<>(moves.subList(moves.size() - MAX_MOVES_PER_TICK, moves.size())));
      }
    }

    return byPlayer;
  }

  /**
   * Advances a player's acknowledgement watermark to cover everything this tick took out of the
   * inbox for them - the moves about to be applied <em>and</em> the ones the cap is about to throw
   * away.
   *
   * <h2>Why dropped moves count</h2>
   * The watermark is a promise, not a receipt: "no move of yours at or below this number will ever
   * be applied". The client's half of the deal is to forget those and replay only what is above it.
   * A move the cap discards is just as finished as one that was applied - the queue does not keep it
   * for later, nothing will ever look at it again - so leaving it above the watermark means the
   * client replays it on top of the authoritative position on every single state from then on. The
   * server would never reach that position, nothing would ever correct the client, and the offset
   * would be permanent. Which is why this runs on the drained list before the cap is applied, and
   * not on what is left afterwards.
   *
   * <p>The two only differ when sequence numbers arrive out of order, because the cap keeps the
   * newest moves <em>by arrival</em>: submissions 6,5,4,3,2,1 leave 4,3,2,1 to be applied while 5
   * and 6 are gone for good, so the highest applied number (4) is not the promise the client needs
   * (6).
   *
   * <p>It never moves backwards. Two submissions can overtake each other on the way in, so the
   * highest number in one tick's batch can be lower than one already acknowledged; honouring that
   * would un-acknowledge input and have the client replay moves the simulation had already made.
   */
  private void recordProcessedSeq(String playerId, List<PlayerMove> moves) {
    Integer highest = null;
    for (PlayerMove move : moves) {
      Integer seq = move.getSeq();
      if (seq != null && (highest == null || seq > highest)) {
        highest = seq;
      }
    }
    if (highest == null) {
      // Input from a client that does not number its moves. There is nothing to acknowledge, and in
      // particular no reason to touch a watermark an earlier numbered submission established.
      return;
    }
    lastProcessedSeq.merge(playerId, highest, Math::max);
  }

  private static final class PlayerInput {

    private final Player player;
    private final List<PlayerMove> moves;

    private PlayerInput(Player player, List<PlayerMove> moves) {
      this.player = player;
      this.moves = moves;
    }
  }

  /**
   * Starts (or restarts) the simulation. Any previously scheduled tick is cancelled first, so a
   * restart cannot leave two loops driving the same game.
   */
  public synchronized GameState<Object> startGame(GameCallback gameCallback, Map<String, Object> gameParameters) {
    cancelTick();

    this.gameCallback = gameCallback;
    inbox.clear();
    // Sequence numbers are per match: a client starts numbering again for a new one, so a watermark
    // left over from the previous match would reject everything it sends.
    lastProcessedSeq.clear();

    GameState<Object> state = new GameState<>();
    state.setParams(gameParameters == null ? new HashMap<>() : new HashMap<>(gameParameters));
    rule.init(state, new ArrayList<>(players.values()));
    state.setPlayers(new ArrayList<>(players.values()));
    this.setup = rule.getSetup();
    this.currentState = state;

    // One shared pool instead of a fresh ScheduledThreadPoolExecutor per game that was never shut
    // down. scheduleWithFixedDelay (not AtFixedRate) means a tick that overruns delays the next one
    // instead of queueing a burst of catch-up ticks that would fast-forward the game.
    ScheduledFuture<?> handle = scheduler.scheduleWithFixedDelay(
      this::tick, tickMillis, tickMillis, TimeUnit.MILLISECONDS);
    tickHandle.set(handle);

    // The match-start broadcast is the one every client in the room builds its picture from, so it
    // is the full state; the ticks that follow it carry only what moved.
    return state.asFullState(setup);
  }

  public synchronized void stop() {
    cancelTick();
  }

  public boolean isRunning() {
    GameState<Object> state = currentState;
    return state != null && state.getState() == GameStateEnum.RUNNING;
  }

  private void cancelTick() {
    ScheduledFuture<?> previous = tickHandle.getAndSet(null);
    if (previous != null) {
      // false: let an in-flight tick finish rather than interrupting it mid-mutation.
      previous.cancel(false);
    }
  }

  private void tick() {
    long startedAt = System.nanoTime();
    boolean completed = false;
    try {
      calculateNextState();
      completed = true;
    } catch (Throwable t) {
      // Throwable, not Exception: a task that throws is dropped by the executor and the room then
      // freezes with no further sign of life, and an Error - a StackOverflowError out of a rule,
      // say - kills the match exactly as dead as a RuntimeException does. Catching it keeps the
      // loop alive and, just as importantly, keeps it from being recorded as a healthy tick.
      LOGGER.error("Tick failed for game {}", gameName, t);
    } finally {
      // Derived from whether the body ran to the end rather than from the catch, so that anything
      // that leaves calculateNextState abnormally counts as a failure.
      reportTick(System.nanoTime() - startedAt, !completed);
    }
  }

  /**
   * Never lets a broken observer cost the room its tick loop.
   *
   * <p>Without this the failure would be absurd: the tick itself is guarded, but an observer
   * throwing from the {@code finally} would escape {@link #tick()}, the executor would drop the
   * task, and a bug in something whose only job is to count ticks would silently freeze every match
   * on the server.
   */
  private void reportTick(long durationNanos, boolean failed) {
    try {
      tickObserver.onTick(gameName, durationNanos, failed);
    } catch (Throwable t) {
      LOGGER.error("Tick observer failed for game {}", gameName, t);
    }
  }

  // A restart or stop must wait until the rule finishes mutating the previous match.
  synchronized void calculateNextState() {
    GameState<Object> state = currentState;
    if (state == null || state.getState() != GameStateEnum.RUNNING) {
      return;
    }

    Map<Player, List<PlayerMove>> moves = drainInbox();

    GameState<Object> nextState = rule.getNextGameState(state, moves);
    nextState.setTick(nextState.getTick() + 1);
    // Published alongside the board so the client can render a scoreboard; the board's player map
    // is keyed by player id only and loses name and score during serialisation.
    nextState.setPlayers(new ArrayList<>(players.values()));
    // A copy, not the live map: this state is handed to the broadcaster and serialised on another
    // thread while the tick thread keeps advancing the watermark. Attached here rather than left to
    // survive on the state because a rule may return a brand new state object every tick.
    nextState.setLastProcessedSeq(new HashMap<>(lastProcessedSeq));
    currentState = nextState;

    if (LOGGER.isDebugEnabled()) {
      // Serialising the whole state at INFO on every tick was the single most expensive thing the
      // server did - 16 times a second at a 60 ms tick.
      LOGGER.debug("Tick {} of game {}", nextState.getTick(), gameName);
    }

    GameCallback callback = this.gameCallback;
    if (nextState.getState() == GameStateEnum.FINISHED) {
      cancelTick();
      if (callback != null) {
        callback.onGameEnd(nextState);
      }
      return;
    }

    if (callback != null) {
      callback.onNextRound(nextState);
    }
  }
}
