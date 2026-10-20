package uu.game.main.game.soldat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.Test;
import uu.game.main.abl.dto.Player;
import uu.game.main.domain.GameState;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GameRandom;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.GameplayModeEnum;
import uu.game.main.game.common.ammo.Bullet;
import uu.game.main.game.soldat.specialability.BonusItemService;
import uu.game.main.game.soldat.specialability.FreezeOpponentsSpecialAbility;
import uu.game.main.game.soldat.specialability.ImmortalitySpecialAbility;
import uu.game.main.game.soldat.specialability.IncreaseLiveSpecialAbility;
import uu.game.main.game.soldat.specialability.IncreasedSpeedSpecialAbility;
import uu.game.main.game.soldat.specialability.SpecialAbility;

/**
 * A match must be a pure function of (seed, players, input).
 *
 * <p>Two rules built independently, handed the same seed and driven with the same moves, are
 * simulated for long enough that bonus crates drop, players die and respawn, and the bot takes its
 * own random decisions - and must end up in byte-identical states. The comparison is the board
 * serialised with Jackson, which is exactly what the server pushes to clients, so any drift in a
 * position, a life, an ammo trajectory or the level layout shows up; scores live on the
 * {@link Player} keys rather than in the board JSON, so they are appended explicitly.
 */
public class SoldatDeterminismTest {

  /** Long enough for several bonus drops (one per second here) and a full death/respawn cycle. */
  private static final int TICKS = 300;

  /** Fixed input: both runs of a comparison must see the same moves, tick for tick. */
  private static final long INPUT_SEED = 20240101L;

  /**
   * Fixed so the scenario itself is reproducible. The assertions below check that this particular
   * match kills people and drops crates; on a generated seed that would be true most of the time,
   * which is not a property a test may rely on.
   */
  private static final long FIXED_SEED = 424242L;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  public void aMatchReplaysExactlyFromItsSeed() {
    MatchTrace recorded = play(FIXED_SEED);

    // A dead player cannot die again until it has respawned, so more deaths than there are players
    // is proof that the randomly chosen respawn points were exercised - and that comparing the two
    // runs is comparing a match in which something actually happened.
    assertTrue("this run no longer exercises death and respawn (deaths=" + recorded.deaths + ")",
      recorded.deaths > recorded.roster);
    assertTrue("this run no longer exercises bonus drops", recorded.bonusDrops > 0);
    assertTrue("this run no longer exercises firing, so the bot's aim is untested", recorded.shotsFired > 0);

    // This is how a replay is set up in practice: take the seed the match reported and hand it
    // straight back in the game parameters.
    MatchTrace replayed = play(recorded.seed);

    assertEquals("the level must be laid out identically", recorded.layout, replayed.layout);
    assertEquals("the whole match must replay tick for tick", recorded.finalState, replayed.finalState);
  }

  @Test
  public void aMatchStartedWithoutASeedReplaysFromTheSeedItPublishes() {
    MatchTrace recorded = play(null);
    assertTrue("a generated seed must stay inside the range JavaScript can represent exactly",
      recorded.seed >= 0 && recorded.seed < (1L << 48));

    // The seed is only useful if it is the one the match really ran on, whoever chose it.
    assertEquals(recorded.finalState, play(recorded.seed).finalState);
  }

  @Test
  public void aDifferentSeedProducesADifferentMatch() {
    MatchTrace first = play(1L);
    MatchTrace second = play(2L);

    // Otherwise the replay assertions above would also hold for a simulation that ignores the seed
    // entirely, or has simply stopped being random.
    assertNotEquals("two seeds must not produce the same match", first.finalState, second.finalState);

    Set<String> layouts = new HashSet<>();
    for (long seed : new long[] {1L, 2L, 3L, 4L, 5L, 6L}) {
      layouts.add(play(seed).layout);
    }
    assertTrue("the level layout must depend on the seed", layouts.size() > 1);
  }

  @Test
  public void theSeedIsAcceptedInEitherJsonEncoding() {
    // Params are a loosely typed JSON map, so the seed can come back as a number or as a string;
    // a client that echoes it as text must still get its match back rather than a fresh one.
    assertEquals(play(FIXED_SEED).finalState, play(String.valueOf(FIXED_SEED)).finalState);
  }

  /**
   * Every coordinate at full precision.
   *
   * <p>The board JSON rounds positions to a tenth of a pixel on its way out, which is the right
   * thing for a payload and the wrong thing for this test: two runs drifting apart by less than
   * that would serialise identically and the drift would go on compounding unseen. This compares
   * the numbers the simulation actually holds.
   */
  private static String precise(SoldatBoard board) {
    StringBuilder out = new StringBuilder();
    for (Map.Entry<Player, SoldatPlayer> entry : board.getPlayers().entrySet()) {
      SoldatPlayer soldat = entry.getValue();
      out.append(entry.getKey().getPlayerId()).append('@')
        .append(Double.doubleToLongBits(soldat.getX())).append(',')
        .append(Double.doubleToLongBits(soldat.getY())).append(',')
        .append(Double.doubleToLongBits(soldat.getVelocityY())).append(';');
    }
    for (uu.game.main.game.common.ammo.Ammo ammo : board.getAmmo()) {
      out.append("ammo@")
        .append(Double.doubleToLongBits(ammo.getX())).append(',')
        .append(Double.doubleToLongBits(ammo.getY())).append(';');
    }
    for (uu.game.main.game.soldat.specialability.BonusItem item : board.getBonusItemList()) {
      out.append("bonus@")
        .append(Double.doubleToLongBits(item.getX())).append(',')
        .append(Double.doubleToLongBits(item.getY())).append(';');
    }
    return out.toString();
  }

  // --- harness -------------------------------------------------------------------------------

  /** Everything about a run that has to be identical for a replay to be a replay. */
  private static final class MatchTrace {
    private long seed;
    private String layout;
    private String finalState;
    private int deaths;
    private int bonusDrops;
    private int shotsFired;
    private int roster;
  }

  /**
   * Plays a whole match.
   *
   * @param seed the {@code seed} game parameter, or {@code null} to let the rule generate one
   */
  private MatchTrace play(Object seed) {
    SoldatRule rule = newRule();

    Map<String, Object> params = new HashMap<>();
    params.put(SoldatRule.PARAM_BOT, true);
    params.put(SoldatRule.PARAM_INITIAL_LIVES, 1);
    params.put(SoldatRule.PARAM_BONUS_DROP_SECONDS, 1);
    params.put(SoldatRule.PARAM_GAME_MODE, GameplayModeEnum.TIME_LIMIT.name());
    // Comfortably longer than the run, so the match is still live at the last tick we compare.
    params.put(SoldatRule.PARAM_SECOND_LIMIT, 600);
    if (seed != null) {
      params.put(GameRandom.PARAM_SEED, seed);
    }

    List<Player> humans = Arrays.asList(player("p1"), player("p2"));

    GameState<SoldatBoard> state = new GameState<>();
    state.setParams(params);
    rule.init(state, humans);

    MatchTrace trace = new MatchTrace();
    trace.seed = ((Number) params.get(GameRandom.PARAM_SEED)).longValue();
    trace.layout = json(state.getGame().getObstacles());
    trace.roster = state.getGame().getPlayers().size();

    Random input = new Random(INPUT_SEED);
    for (int tick = 0; tick < TICKS; tick++) {
      state.setTick(tick);
      rule.getNextGameState(state, movesFor(humans, input));

      for (GameRuleEvent event : state.getGameEvents()) {
        if ("death".equals(event.getAction())) {
          trace.deaths++;
        } else if ("fired".equals(event.getAction())) {
          trace.shotsFired++;
        }
      }
      trace.bonusDrops = Math.max(trace.bonusDrops, state.getGame().getBonusItemList().size());
    }

    trace.finalState = json(state.getGame()) + "|scores=" + scores(state.getGame())
      + "|precise=" + precise(state.getGame());
    return trace;
  }

  private static SoldatRule newRule() {
    SoldatRule rule = new SoldatRule();
    // Four abilities rather than one, so which ability a crate carries is itself a random draw the
    // replay has to reproduce.
    List<SpecialAbility> abilities = Arrays.<SpecialAbility>asList(
      new IncreaseLiveSpecialAbility(), new ImmortalitySpecialAbility(),
      new IncreasedSpeedSpecialAbility(), new FreezeOpponentsSpecialAbility());
    try {
      Field field = SoldatRule.class.getDeclaredField("bonusItemService");
      field.setAccessible(true);
      field.set(rule, new BonusItemService(abilities));
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot inject the bonus item service", e);
    }
    return rule;
  }

  /**
   * One tick of input for every human: varied enough to move players around the level, into each
   * other's fire and off the edges, but drawn from a fixed seed so both runs see the same match.
   */
  private static Map<Player, List<SoldatMove>> movesFor(List<Player> humans, Random input) {
    Map<Player, List<SoldatMove>> moves = new LinkedHashMap<>();
    for (Player human : humans) {
      SoldatMove move = new SoldatMove();
      int direction = input.nextInt(10);
      if (direction < 4) {
        move.setMove(Direction.RIGHT);
      } else if (direction < 7) {
        move.setMove(Direction.LEFT);
      } else if (direction < 9) {
        move.setMove(Direction.UP);
      }
      if (input.nextInt(3) == 0) {
        move.setFired(new Bullet());
        move.setFiredAngle(input.nextInt(360) - 180);
      }
      moves.put(human, Collections.singletonList(move));
    }
    return moves;
  }

  private static String scores(SoldatBoard board) {
    List<String> rows = new ArrayList<>();
    for (Player player : board.getPlayers().keySet()) {
      rows.add(player.getPlayerId() + "=" + player.getScore() + "/" + player.getState());
    }
    return rows.toString();
  }

  private static String json(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("board is not serialisable, so the client cannot render it either", e);
    }
  }

  private static Player player(String id) {
    Player player = new Player(id);
    player.setPlayerName("name of " + id);
    return player;
  }
}
