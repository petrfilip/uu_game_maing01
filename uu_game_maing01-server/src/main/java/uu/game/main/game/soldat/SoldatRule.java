package uu.game.main.game.soldat;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import uu.game.main.abl.ScoreAbl;
import uu.game.main.abl.dto.Player;
import uu.game.main.abl.dto.PlayerStateEnum;
import uu.game.main.domain.GameState;
import uu.game.main.domain.GameStateEnum;
import uu.game.main.domain.IRule;
import uu.game.main.game.common.CollisionSpace;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GameArea;
import uu.game.main.game.common.GamePlayMode;
import uu.game.main.game.common.GameRandom;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.GameSetup;
import uu.game.main.game.common.GameplayModeEnum;
import uu.game.main.game.common.ScoreLimitGameplayMode;
import uu.game.main.game.common.TimeLimitGameplayMode;
import uu.game.main.game.common.ammo.Ammo;
import uu.game.main.game.common.ammo.AmmoDamagable;
import uu.game.main.game.soldat.specialability.BonusItem;
import uu.game.main.game.soldat.specialability.BonusItemService;

/**
 * Soldat: a side-on arena shooter.
 *
 * <p>A tick is: apply buffered input, step every player's physics exactly once, drop and settle
 * pickups, advance projectiles, then test the end condition. Previously input and physics were the
 * same call, invoked in a hardcoded {@code for (i = 0; i < 5; i++)} loop that took move {@code i}
 * from the queue or an empty one - so how far a player travelled in a second depended on how many
 * packets their browser happened to send.
 */
@Service("soldat")
@Scope(scopeName = SCOPE_PROTOTYPE)
public class SoldatRule implements IRule<SoldatBoard, SoldatMove> {

  private static final Logger LOGGER = LogManager.getLogger(SoldatRule.class);

  /** The engine ticks at 50 ms, so this many ticks make a second. */
  private static final int TICKS_PER_SECOND = 20;

  private static final String BOT_PLAYER_ID = "0-0";

  static final String PARAM_GAME_MODE = "gameMode";
  static final String PARAM_SECOND_LIMIT = "gameModeSecondLimit";
  static final String PARAM_STOP_AT_SCORE = "gameModeStopAtScore";
  static final String PARAM_INITIAL_LIVES = "initialLives";
  static final String PARAM_PLAYER_COLLISION = "playerCollision";
  static final String PARAM_BONUS_DROP_SECONDS = "bonusDropSeconds";
  static final String PARAM_BOT = "bot";
  static final String PARAM_AWID = "awid";

  @Inject
  private ScoreAbl scoreAbl;

  @Inject
  private BonusItemService bonusItemService;

  /** This match's randomness; see {@link GameRandom}. Created per match in {@link #init}. */
  private GameRandom random;

  private GamePlayMode gameplayMode;
  private int nextBonusDropTick;
  private int bonusDropIntervalTicks;
  private Player botPlayer;

  /** The bot's driver. Holds what it is currently trying, so it can tell a plan from a rut. */
  private final SoldatBot botAi = new SoldatBot();

  /** Static parts of the current match; see {@link IRule#getSetup()}. */
  private GameSetup setup;

  /**
   * Workspace this match belongs to, needed to write the leaderboard.
   *
   * <p>Kept here rather than in the game parameters because the parameters are published to every
   * browser: an internal workspace identifier has no business being broadcast twenty times a second.
   */
  private String awid;

  @Override
  public Class<SoldatMove> getMoveClass() {
    return SoldatMove.class;
  }

  @Override
  public GameSetup getSetup() {
    return setup;
  }

  @Override
  public GameState<SoldatBoard> init(GameState<SoldatBoard> currentState, Collection<Player> players) {
    Map<String, Object> params = currentState.getParams();

    // Taken out of the parameters before anything else touches them: what is left of this map is
    // what the client is told about the match.
    awid = (String) params.remove(PARAM_AWID);

    // Before anything that rolls a die. Reads the seed the caller asked to replay, or picks one,
    // and publishes back whichever was used.
    random = GameRandom.fromParams(params);

    GameArea area = GameArea.fromParams(params, 1400, 600);
    int initialLives = intParam(params, PARAM_INITIAL_LIVES, SoldatPlayer.INIT_LIVES);
    boolean playerCollision = boolParam(params, PARAM_PLAYER_COLLISION, true);
    bonusDropIntervalTicks = intParam(params, PARAM_BONUS_DROP_SECONDS, 20) * TICKS_PER_SECOND;

    params.put(GameArea.PARAM_WIDTH, area.getWidth());
    params.put(GameArea.PARAM_HEIGHT, area.getHeight());
    params.put(PARAM_INITIAL_LIVES, initialLives);
    params.put(PARAM_PLAYER_COLLISION, playerCollision);

    gameplayMode = createGameplayMode(params);

    List<Player> roster = new ArrayList<>(players);
    if (boolParam(params, PARAM_BOT, true)) {
      roster.add(botPlayer());
    }

    SoldatBoard board = new SoldatBoard();
    board.setArea(area);
    board.setInitialLives(initialLives);
    board.setPlayerCollision(playerCollision);
    board.setRandom(random);
    board.setObstacles(SoldatMap.build(area, random));

    for (Player player : roster) {
      // Score is per match. It used to carry over, because the same Player instances are reused
      // between games, so a restart began with the previous round's scores still on the board.
      player.setScore(0);
      player.setState(PlayerStateEnum.ACTIVE);

      GameRectangle spawn = board.findSpawnPoint(SoldatPlayer.SIZE, SoldatPlayer.SIZE);
      SoldatPlayer soldat = new SoldatPlayer(Direction.RIGHT, spawn.getX(), spawn.getY(), SoldatPlayer.SIZE, SoldatPlayer.SIZE);
      soldat.setMaxLives(initialLives);
      soldat.setPlayerId(player.getPlayerId());
      board.getPlayers().put(player, soldat);
    }

    // Snapshot of everything that cannot change from here on. It goes to a client once, on connect
    // and at match start, instead of riding along with every tick.
    setup = GameSetup.of(board, params);

    currentState.setGame(board);
    currentState.setState(GameStateEnum.RUNNING);
    currentState.setTick(0);
    nextBonusDropTick = bonusDropIntervalTicks;

    LOGGER.info("Soldat match started: {} players, mode {}, seed {}", roster.size(), params.get(PARAM_GAME_MODE), random.getSeed());
    return currentState;
  }

  @Override
  public GameState<SoldatBoard> getNextGameState(GameState<SoldatBoard> state, Map<Player, List<SoldatMove>> unprocessedMoves) {
    SoldatBoard board = state.getGame();
    int tick = state.getTick();
    List<GameRuleEvent> events = new ArrayList<>();

    // Retain the score, but retire the body before collisions, bonuses or bullets can use it.
    for (Map.Entry<Player, SoldatPlayer> entry : board.getPlayers().entrySet()) {
      if (entry.getKey().getState() == PlayerStateEnum.DONE) {
        entry.getValue().setLives(0);
      }
    }

    applyInputAndStep(board, unprocessedMoves, tick, events);
    updateBonusItems(board, tick, events);
    updateAmmo(board, tick, events);
    checkEndOfMatch(state, board, tick, events);

    state.setGameEvents(events);
    return state;
  }

  private void applyInputAndStep(SoldatBoard board, Map<Player, List<SoldatMove>> unprocessedMoves,
                                 int tick, List<GameRuleEvent> events) {
    Collection<SoldatPlayer> soldiers = board.getPlayers().values();

    for (Map.Entry<Player, SoldatPlayer> entry : board.getPlayers().entrySet()) {
      Player player = entry.getKey();
      SoldatPlayer soldat = entry.getValue();
      if (player.getState() == PlayerStateEnum.DONE) {
        continue;
      }
      soldat.setCurrentTick(tick);

      List<SoldatMove> moves = movesFor(board, player, soldat, soldiers, unprocessedMoves, tick);

      for (SoldatMove move : moves) {
        soldat.applyInput(move);
        fire(board, player, soldat, move, tick, events);
      }

      // Exactly once per tick, whether or not the player sent anything - gravity and respawn must
      // run for an idle player too.
      soldat.step(board, tick);
    }
  }

  private List<SoldatMove> movesFor(SoldatBoard board, Player player, SoldatPlayer soldat,
                                    Collection<SoldatPlayer> soldiers,
                                    Map<Player, List<SoldatMove>> unprocessedMoves, int tick) {
    if (isBot(player)) {
      return new ArrayList<>(botAi.decide(soldat, board, soldiers, tick, random));
    }
    List<SoldatMove> moves = unprocessedMoves.get(player);
    return moves == null ? new ArrayList<>() : moves;
  }

  private void fire(SoldatBoard board, Player player, SoldatPlayer soldat, SoldatMove move,
                    int tick, List<GameRuleEvent> events) {
    Ammo ammo = move.getFired();
    if (ammo == null || !soldat.tryFire(tick)) {
      return;
    }
    events.addAll(ammo.init(player, soldat, move, tick));
    board.getAmmo().add(ammo);
  }

  private void updateBonusItems(SoldatBoard board, int tick, List<GameRuleEvent> events) {
    CollisionSpace terrain = board.terrain();
    Collection<SoldatPlayer> soldiers = board.getPlayers().values();

    Iterator<BonusItem> iterator = board.getBonusItemList().iterator();
    while (iterator.hasNext()) {
      BonusItem item = iterator.next();
      item.fall(terrain, board.getArea());
      events.addAll(item.checkIntersection(soldiers));
      if (item.isUsed()) {
        iterator.remove();
      }
    }

    if (tick >= nextBonusDropTick) {
      board.getBonusItemList().add(bonusItemService.generate(board.getArea(), random));
      nextBonusDropTick = tick + bonusDropIntervalTicks;
    }
  }

  private void updateAmmo(SoldatBoard board, int tick, List<GameRuleEvent> events) {
    Collection<AmmoDamagable> targets = new ArrayList<>(board.getPlayers().values());

    Iterator<Ammo> iterator = board.getAmmo().iterator();
    while (iterator.hasNext()) {
      Ammo ammo = iterator.next();
      // Obstacles are passed separately rather than as damagables: they stop a projectile but must
      // not look like a valid victim, which is how shooting a crate used to score a point.
      events.addAll(ammo.applyEffect(targets, board.getObstacles(), tick, board.getArea()));
      if (ammo.isUsed()) {
        iterator.remove();
      }
    }
  }

  private void checkEndOfMatch(GameState<SoldatBoard> state, SoldatBoard board, int tick, List<GameRuleEvent> events) {
    Collection<Player> players = board.getPlayers().keySet();
    // Progress is the one match-wide number that changes every tick, so it travels with the tick
    // rather than with the settings.
    state.setProgress(gameplayMode.getProgress(players, tick));

    if (!gameplayMode.isGameFinished(players, tick)) {
      return;
    }

    state.setState(GameStateEnum.FINISHED);
    // Just the signal: names and scores are in the roster published alongside every state, so a
    // scoreboard in here would be the same numbers a second time.
    events.add(new GameRuleEvent("finished", "game"));

    if (awid == null) {
      return;
    }
    try {
      // Bots must not appear in the human leaderboard.
      List<Player> humans = new ArrayList<>();
      for (Player player : players) {
        if (!isBot(player)) {
          humans.add(player);
        }
      }
      if (humans.size() > 1) {
        scoreAbl.calculateEloAndPersist(awid, humans);
      }
    } catch (Exception e) {
      // Persisting the leaderboard must never take the match down with it.
      LOGGER.error("Failed to persist Elo for awid {}", awid, e);
    }
  }

  private GamePlayMode createGameplayMode(Map<String, Object> params) {
    GameplayModeEnum mode = GameplayModeEnum.find((String) params.get(PARAM_GAME_MODE), GameplayModeEnum.TIME_LIMIT);
    params.put(PARAM_GAME_MODE, mode.name());

    if (mode == GameplayModeEnum.TIME_LIMIT) {
      int seconds = intParam(params, PARAM_SECOND_LIMIT, 120);
      params.put(PARAM_SECOND_LIMIT, seconds);
      return new TimeLimitGameplayMode(seconds * TICKS_PER_SECOND);
    }

    int stopAtScore = intParam(params, PARAM_STOP_AT_SCORE, 5);
    params.put(PARAM_STOP_AT_SCORE, stopAtScore);
    return new ScoreLimitGameplayMode(stopAtScore);
  }

  private Player botPlayer() {
    if (botPlayer == null) {
      botPlayer = new Player(BOT_PLAYER_ID);
      botPlayer.setPlayerName("bot");
      botPlayer.setState(PlayerStateEnum.ACTIVE);
    }
    return botPlayer;
  }

  private boolean isBot(Player player) {
    return BOT_PLAYER_ID.equals(player.getPlayerId());
  }

  /**
   * Game parameters arrive as JSON, so an integer may turn up as any {@link Number} - reading it
   * with a bare cast to {@code Integer} or {@code Long} threw a ClassCastException depending on how
   * the client happened to encode it.
   */
  private static int intParam(Map<String, Object> params, String key, int defaultValue) {
    Object value = params.get(key);
    return value instanceof Number ? ((Number) value).intValue() : defaultValue;
  }

  private static boolean boolParam(Map<String, Object> params, String key, boolean defaultValue) {
    Object value = params.get(key);
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    return value instanceof String ? Boolean.parseBoolean((String) value) : defaultValue;
  }
}
