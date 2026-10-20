package uu.game.main.game.soldat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import uu.game.main.abl.ScoreAbl;
import uu.game.main.abl.dto.Player;
import uu.game.main.abl.dto.PlayerStateEnum;
import uu.game.main.dao.ScoreDao;
import uu.game.main.domain.GameState;
import uu.game.main.domain.GameStateEnum;
import uu.game.main.game.common.GameArea;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.GameplayModeEnum;
import uu.game.main.game.common.ammo.Bullet;
import uu.game.main.game.soldat.specialability.BonusItemService;
import uu.game.main.game.soldat.specialability.IncreaseLiveSpecialAbility;
import uu.game.main.game.soldat.specialability.SpecialAbility;

/**
 * Drives the rule the way the engine does - one {@code getNextGameState} per tick - so a whole match
 * runs in milliseconds and without a Spring context.
 */
public class SoldatRuleTest {

  @Test
  public void respawningDoesNotLeaveLivingPlayersBehindInTheLevel() {
    SoldatArena arena = SoldatArena.of(1000, 600).withPlatform(0, 500, 1000, 20);
    arena.addPlayer(100, 400);
    SoldatBoard board = arena.board();

    board.findSpawnPoint(SoldatPlayer.SIZE, SoldatPlayer.SIZE);

    // Choosing a respawn point counts living players as blockers, so two soldiers cannot appear
    // inside one another. It used to do that by adding them to the level's own collision space -
    // harmless while that space was rebuilt for every entity on every tick, and a permanent wall
    // wherever somebody stood once the level is built once and shared.
    assertFalse("a living player was left behind as level geometry",
      board.terrain().collides(new GameRectangle(100, 400, SoldatPlayer.SIZE, SoldatPlayer.SIZE)));
  }

  private static final String AWID = "awid-1";
  private static final String BOT_ID = "0-0";

  private SoldatRule rule;
  private final List<String> eloLookups = new ArrayList<>();
  private final List<String> eloPersists = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    rule = new SoldatRule();
    inject(rule, "bonusItemService",
      new BonusItemService(Collections.<SpecialAbility>singletonList(new IncreaseLiveSpecialAbility())));
    inject(rule, "scoreAbl", recordingScoreAbl());
  }

  private static void inject(Object target, String field, Object value) throws Exception {
    Field declared = target.getClass().getDeclaredField(field);
    declared.setAccessible(true);
    declared.set(target, value);
  }

  /**
   * A real {@link ScoreAbl} over a recording DAO: the rule decides <em>who</em> is rated, and the
   * only observable trace of that decision is which identities reach the datastore.
   */
  private ScoreAbl recordingScoreAbl() throws Exception {
    ScoreDao dao = (ScoreDao) Proxy.newProxyInstance(
      getClass().getClassLoader(),
      new Class<?>[] {ScoreDao.class},
      (proxy, method, args) -> {
        if ("getByUuIdentity".equals(method.getName())) {
          eloLookups.add((String) args[1]);
          return null;
        }
        if ("create".equals(method.getName()) || "update".equals(method.getName())) {
          eloPersists.add(((uu.game.main.abl.entity.Score) args[0]).getUuIdentity());
          return args[0];
        }
        if ("hashCode".equals(method.getName())) {
          return System.identityHashCode(proxy);
        }
        if ("equals".equals(method.getName())) {
          return proxy == args[0];
        }
        if ("toString".equals(method.getName())) {
          return "recordingScoreDao";
        }
        return null;
      });

    ScoreAbl scoreAbl = new ScoreAbl();
    inject(scoreAbl, "scoreDao", dao);
    return scoreAbl;
  }

  private static Map<String, Object> params(Object... keysAndValues) {
    Map<String, Object> params = new HashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      params.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return params;
  }

  private static Player player(String id) {
    Player player = new Player(id);
    player.setPlayerName("name of " + id);
    return player;
  }

  private static GameState<SoldatBoard> newState(Map<String, Object> params) {
    GameState<SoldatBoard> state = new GameState<>();
    state.setParams(params);
    return state;
  }

  private static Map<Player, List<SoldatMove>> noMoves() {
    return new HashMap<>();
  }

  /** One tick, exactly as the engine drives it: run the rule, then advance the tick counter. */
  private void tick(GameState<SoldatBoard> state, Map<Player, List<SoldatMove>> moves) {
    rule.getNextGameState(state, moves);
    state.setTick(state.getTick() + 1);
  }

  private static GameRuleEvent eventNamed(GameState<SoldatBoard> state, String action) {
    for (GameRuleEvent event : state.getGameEvents()) {
      if (action.equals(event.getAction())) {
        return event;
      }
    }
    return null;
  }

  @Test
  public void aLivingDisconnectedPlayerStopsBeingADamageTarget() {
    Player player = player("p1");
    GameState<SoldatBoard> state = newState(params(SoldatRule.PARAM_BOT, false));
    rule.init(state, Collections.singletonList(player));
    SoldatPlayer soldier = state.getGame().getPlayers().get(player);
    player.setState(PlayerStateEnum.DONE);

    tick(state, noMoves());

    assertTrue(soldier.isDead());
    assertFalse(soldier.applyAmmoDamage(100).isLethal());
  }

  @Test
  public void disconnectedPlayersCannotBeFarmedOrRespawned() {
    Player player = player("p1");
    GameState<SoldatBoard> state = newState(params(SoldatRule.PARAM_BOT, false));
    rule.init(state, Collections.singletonList(player));
    SoldatPlayer soldier = state.getGame().getPlayers().get(player);
    soldier.applyAmmoDamage(soldier.getLives());
    player.setState(PlayerStateEnum.DONE);
    state.setTick(SoldatPlayer.RESPAWN_TICKS + 1);

    tick(state, noMoves());

    assertTrue("a disconnected player respawned", soldier.isDead());
    assertFalse("the abandoned body can still award a kill", soldier.applyAmmoDamage(100).isLethal());
    assertTrue("the final scoreboard lost the player", state.getGame().getPlayers().containsKey(player));
  }

  @Test
  public void playerScoresResetWhenANewMatchStartsOnTheSameRuleInstance() {
    List<Player> players = Arrays.asList(player("p1"), player("p2"));
    rule.init(newState(params(SoldatRule.PARAM_BOT, false)), players);

    players.get(0).setScore(4);
    players.get(1).setScore(2);

    rule.init(newState(params(SoldatRule.PARAM_BOT, false)), players);

    // The same Player objects are reused between matches, so a rematch used to start with the
    // previous round's scoreboard already on the screen.
    assertEquals(Integer.valueOf(0), players.get(0).getScore());
    assertEquals(Integer.valueOf(0), players.get(1).getScore());
  }

  @Test
  public void theMatchFinishesAtTheScoreLimitAndPublishesAScoreboard() {
    List<Player> players = Arrays.asList(player("p1"), player("p2"));
    GameState<SoldatBoard> state = newState(params(
      SoldatRule.PARAM_BOT, false,
      SoldatRule.PARAM_GAME_MODE, GameplayModeEnum.SCORE_ACHIEVED.name(),
      SoldatRule.PARAM_STOP_AT_SCORE, 3));
    rule.init(state, players);

    players.get(1).setScore(3);
    tick(state, noMoves());

    assertEquals(GameStateEnum.FINISHED, state.getState());
    GameRuleEvent finished = eventNamed(state, "finished");
    assertNotNull("the client is told the match is over through this event", finished);
    assertEquals("game", finished.getObjectName());

    // The final standings are not repeated in the event: the roster published with every state -
    // including this one - already carries every name and score.
    assertEquals(Integer.valueOf(3), players.get(1).getScore());
    assertEquals(Integer.valueOf(0), players.get(0).getScore());
  }

  @Test
  public void theBotIsExcludedFromEloPersistence() {
    List<Player> players = Arrays.asList(player("p1"), player("p2"));
    GameState<SoldatBoard> state = newState(params(
      SoldatRule.PARAM_BOT, true,
      SoldatRule.PARAM_AWID, AWID,
      SoldatRule.PARAM_GAME_MODE, GameplayModeEnum.SCORE_ACHIEVED.name(),
      SoldatRule.PARAM_STOP_AT_SCORE, 1));
    rule.init(state, players);
    assertEquals("the bot should be on the board", 3, state.getGame().getPlayers().size());

    players.get(0).setScore(1);
    tick(state, noMoves());

    assertEquals(GameStateEnum.FINISHED, state.getState());
    assertEquals(Arrays.asList("p1", "p2"), eloLookups);
    assertEquals(Arrays.asList("p1", "p2"), eloPersists);
    assertFalse("a bot must never appear in the human leaderboard", eloLookups.contains(BOT_ID));
  }

  @Test
  public void aSingleHumanPlayingTheBotIsNotRated() {
    List<Player> players = Collections.singletonList(player("p1"));
    GameState<SoldatBoard> state = newState(params(
      SoldatRule.PARAM_BOT, true,
      SoldatRule.PARAM_AWID, AWID,
      SoldatRule.PARAM_GAME_MODE, GameplayModeEnum.SCORE_ACHIEVED.name(),
      SoldatRule.PARAM_STOP_AT_SCORE, 1));
    rule.init(state, players);

    players.get(0).setScore(1);
    tick(state, noMoves());

    assertEquals(GameStateEnum.FINISHED, state.getState());
    assertTrue("beating a bot must not move anyone's rating", eloLookups.isEmpty());
  }

  @Test
  public void usedAmmoIsRemovedFromTheBoard() {
    Player shooter = player("p1");
    GameState<SoldatBoard> state = newState(params(SoldatRule.PARAM_BOT, false));
    rule.init(state, Collections.singletonList(shooter));

    SoldatMove shot = new SoldatMove();
    shot.setFired(new Bullet());
    shot.setFiredAngle(-90);
    Map<Player, List<SoldatMove>> moves = new HashMap<>();
    moves.put(shooter, Collections.singletonList(shot));

    tick(state, moves);
    assertNotNull("the shot never left the barrel", eventNamed(state, "fired"));

    // A bullet that hit nothing used to fly forever and stay in this list, so the board state
    // pushed to every client grew with every shot.
    for (int i = 0; i < 120 && !state.getGame().getAmmo().isEmpty(); i++) {
      tick(state, noMoves());
    }

    assertTrue("spent ammo must be dropped from the board", state.getGame().getAmmo().isEmpty());
  }

  @Test
  public void theFireCooldownIsEnforcedAcrossTicks() {
    Player shooter = player("p1");
    GameState<SoldatBoard> state = newState(params(SoldatRule.PARAM_BOT, false));
    rule.init(state, Collections.singletonList(shooter));

    int shotsThatLeftTheBarrel = 0;
    for (int i = 0; i < SoldatPlayer.FIRE_COOLDOWN_TICKS; i++) {
      shotsThatLeftTheBarrel += fireOnce(state, shooter);
    }
    // A shot attached to every packet the client sends must not become a machine gun.
    assertEquals(1, shotsThatLeftTheBarrel);

    shotsThatLeftTheBarrel += fireOnce(state, shooter);
    assertEquals("the cooldown must expire", 2, shotsThatLeftTheBarrel);
  }

  /** Submits a shot for one tick and reports whether the rule actually fired it. */
  private int fireOnce(GameState<SoldatBoard> state, Player shooter) {
    SoldatMove shot = new SoldatMove();
    shot.setFired(new Bullet());
    shot.setFiredAngle(-90);
    Map<Player, List<SoldatMove>> moves = new HashMap<>();
    moves.put(shooter, Collections.singletonList(shot));

    tick(state, moves);
    return eventNamed(state, "fired") == null ? 0 : 1;
  }

  @Test
  public void gameParametersAreCoercedAndPublishedBackToTheClient() {
    GameState<SoldatBoard> state = newState(params(
      SoldatRule.PARAM_BOT, false,
      GameArea.PARAM_WIDTH, 900.0d,
      GameArea.PARAM_HEIGHT, 500L,
      SoldatRule.PARAM_INITIAL_LIVES, 7L,
      SoldatRule.PARAM_GAME_MODE, "score_achieved"));
    rule.init(state, Collections.singletonList(player("p1")));

    assertEquals(900, state.getGame().getArea().getWidth());
    assertEquals(500, state.getGame().getArea().getHeight());
    assertEquals(7, state.getGame().getInitialLives());
    assertEquals(7, state.getGame().getPlayers().values().iterator().next().getMaxLives());
    // Echoed back so the client renders the arena the server is actually simulating.
    assertEquals(Integer.valueOf(900), state.getParams().get(GameArea.PARAM_WIDTH));
    assertEquals(Integer.valueOf(500), state.getParams().get(GameArea.PARAM_HEIGHT));
    assertEquals(GameplayModeEnum.SCORE_ACHIEVED.name(), state.getParams().get(SoldatRule.PARAM_GAME_MODE));
  }

  @Test
  public void timeLimitProgressIsPublishedEveryTick() {
    GameState<SoldatBoard> state = newState(params(
      SoldatRule.PARAM_BOT, false,
      SoldatRule.PARAM_GAME_MODE, GameplayModeEnum.TIME_LIMIT.name(),
      SoldatRule.PARAM_SECOND_LIMIT, 1));
    rule.init(state, Collections.singletonList(player("p1")));

    // One second is twenty ticks, so halfway is tick ten.
    state.setTick(10);
    rule.getNextGameState(state, noMoves());

    assertEquals(0.5, state.getProgress(), 1e-9);
    assertEquals(GameStateEnum.RUNNING, state.getState());
  }

  @Test
  public void theMatchEndsExactlyOnTheTimeLimitTick() {
    GameState<SoldatBoard> state = newState(params(
      SoldatRule.PARAM_BOT, false,
      SoldatRule.PARAM_GAME_MODE, GameplayModeEnum.TIME_LIMIT.name(),
      SoldatRule.PARAM_SECOND_LIMIT, 1));
    rule.init(state, Collections.singletonList(player("p1")));

    state.setTick(19);
    rule.getNextGameState(state, noMoves());
    assertEquals(GameStateEnum.RUNNING, state.getState());

    state.setTick(20);
    rule.getNextGameState(state, noMoves());
    assertEquals(GameStateEnum.FINISHED, state.getState());
  }

  @Test
  public void everyPlayerStartsOutsideTheLevelGeometry() {
    for (int attempt = 0; attempt < 20; attempt++) {
      SoldatRule freshRule = new SoldatRule();
      GameState<SoldatBoard> state = newState(params(SoldatRule.PARAM_BOT, false));
      freshRule.init(state, Arrays.asList(player("p1"), player("p2"), player("p3")));

      SoldatBoard board = state.getGame();
      for (SoldatPlayer soldat : board.getPlayers().values()) {
        assertFalse("spawned inside an obstacle", board.terrain().collides(soldat));
        assertFalse("spawned outside the arena", board.getArea().isOutOfBounds(soldat));
      }
    }
  }
}
