package uu.game.main.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import uu.game.main.abl.dto.Player;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.GameSetup;

/**
 * One authoritative snapshot of a game, plus the events that happened while producing it.
 *
 * <h2>Two kinds of payload</h2>
 * A snapshot is published in one of two shapes, and {@link #isFull()} says which one a client is
 * holding:
 * <ul>
 *   <li><b>per tick</b> ({@code full: false}) - only what changes: entities, events, the tick
 *       counter, match progress and the roster.</li>
 *   <li><b>full</b> ({@code full: true}) - the same, plus {@link #getSetup()}: arena, level geometry
 *       and match settings. Sent when a client connects and when a match starts, and nowhere
 *       else.</li>
 * </ul>
 * Before the split, every tick carried the whole board - about seventy obstacle walls that cannot
 * change after {@code init} - to every subscriber, twenty times a second.
 *
 * @param <STATE> the game specific board
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GameState<STATE> {

  private Integer tick = 0;

  private GameStateEnum state = GameStateEnum.WAITING;

  /**
   * Match settings, as the rule resolved them.
   *
   * <p>Not serialised here: they are fixed for the whole match and go out once inside
   * {@link GameSetup}. They also used to be the way an internal {@code awid} reached the browser
   * twenty times a second.
   */
  @JsonIgnore
  private Map<String, Object> params = new HashMap<>();

  /**
   * Roster with names and scores.
   *
   * <p>Populated by the engine on every tick. It was declared from the start but never filled, and
   * the board's player map is keyed by id only, so the client had no way to render a scoreboard -
   * which is why the scoreboard markup in the client sat commented out.
   */
  private Collection<Player> players = new ArrayList<>();

  /**
   * Per player id, the highest input sequence number the simulation has already decided the fate of.
   *
   * <p>This is what lets a client predict its own movement instead of being snapped back by every
   * state that arrives. The position in {@link #getGame()} is the position <em>after</em> the moves
   * up to this number; anything the client sent above it has not been applied yet, so the client
   * replays exactly those on top of the authoritative position rather than discarding its whole
   * prediction. Before this existed the client had no way to tell the two apart and overwrote its
   * local player wholesale on every tick, which at any real latency is the classic rubber-band.
   *
   * <p>"Decided" rather than "applied" on purpose: a move the engine threw away - because the player
   * sent more than {@link uu.game.main.engine.GameEngine#MAX_MOVES_PER_TICK} of them for one tick -
   * is as final as one it applied, and has to be acknowledged too. See
   * {@code GameEngine.recordProcessedSeq}.
   *
   * <p>Populated by the engine on every tick, so it survives a rule that hands back a fresh state
   * object, and carried on a full state as well: a client that reconnects mid match has to learn the
   * watermark before it can send anything the server will accept.
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private Map<String, Integer> lastProcessedSeq = new HashMap<>();

  private STATE game;

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private List<GameRuleEvent> gameEvents = new ArrayList<>();

  /**
   * How far the match has run towards its end, 0..1, or {@code null} for a game that has no such
   * notion. Changes every tick, so unlike the rest of the settings it belongs in the tick payload.
   */
  private Double progress;

  /** See {@link #isFull()}; only ever set by {@link #asFullState(GameSetup)}. */
  private GameSetup setup;

  /**
   * Left out of the payload when false, which is every tick but two per match; a client reads its
   * absence as the delta it is.
   */
  @JsonInclude(JsonInclude.Include.NON_DEFAULT)
  private boolean full;

  public Integer getTick() {
    return tick;
  }

  public void setTick(Integer tick) {
    this.tick = tick;
  }

  public GameStateEnum getState() {
    return state;
  }

  public void setState(GameStateEnum state) {
    this.state = state;
  }

  public Map<String, Object> getParams() {
    return params;
  }

  public void setParams(Map<String, Object> params) {
    this.params = params;
  }

  public Collection<Player> getPlayers() {
    return players;
  }

  public void setPlayers(Collection<Player> players) {
    this.players = players;
  }

  /** See the field: the per player input acknowledgement watermark. Never {@code null}. */
  public Map<String, Integer> getLastProcessedSeq() {
    return lastProcessedSeq;
  }

  public void setLastProcessedSeq(Map<String, Integer> lastProcessedSeq) {
    this.lastProcessedSeq = lastProcessedSeq == null ? new HashMap<String, Integer>() : lastProcessedSeq;
  }

  public STATE getGame() {
    return game;
  }

  public void setGame(STATE game) {
    this.game = game;
  }

  public List<GameRuleEvent> getGameEvents() {
    return gameEvents;
  }

  public void setGameEvents(List<GameRuleEvent> gameEvents) {
    this.gameEvents = gameEvents;
  }

  public Double getProgress() {
    return progress;
  }

  public void setProgress(Double progress) {
    this.progress = progress;
  }

  /**
   * The static parts of the match, present only on a full state.
   *
   * @return arena, level geometry and match settings, or {@code null} on a per-tick payload - in
   *     which case the client keeps drawing from the last full state it received
   */
  public GameSetup getSetup() {
    return setup;
  }

  /**
   * Whether this payload carries {@link #getSetup()} as well as what changed this tick.
   *
   * <p>An explicit flag rather than "check whether setup happens to be there": a client that has
   * just connected has to be able to tell, without guessing, whether it is looking at a complete
   * picture or at a delta it cannot render on its own.
   */
  public boolean isFull() {
    return full;
  }

  /**
   * The same snapshot, published with the static parts attached - what a client gets on connect and
   * at match start.
   *
   * <p>A shallow copy: the board is shared with the live simulation rather than duplicated, exactly
   * as the per-tick payload already shares it. Events are dropped, because they belong to the tick
   * that produced them - a client connecting halfway through a match should not hear the last
   * explosion again.
   */
  public GameState<STATE> asFullState(GameSetup setup) {
    GameState<STATE> fullState = new GameState<>();
    fullState.tick = tick;
    fullState.state = state;
    fullState.params = params;
    fullState.players = players;
    // Carried, not dropped: a client that has just reconnected mid match needs to know how far the
    // server has got through its input before it can predict anything, and its very next submission
    // has to continue the sequence rather than start again from zero.
    fullState.lastProcessedSeq = lastProcessedSeq;
    fullState.game = game;
    fullState.gameEvents = Collections.emptyList();
    fullState.progress = progress;
    fullState.setup = setup;
    fullState.full = true;
    return fullState;
  }

  @Override
  public String toString() {
    return "GameState{tick=" + tick + ", state=" + state + ", players=" + players.size()
      + (full ? ", full" : "") + '}';
  }
}
