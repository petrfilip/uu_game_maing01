package uu.game.main.game.bulanci;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import uu.game.main.abl.dto.Player;
import uu.game.main.domain.GameState;
import uu.game.main.game.common.GameRandom;

/**
 * Bulanci draws its whole level - three randomly placed trees - and every spawn point at
 * {@code init}, and nothing after that, so its reproducibility is entirely decided there.
 *
 * <p>The mistake this catches is a rule that uses a seeded generator but makes a fresh one per
 * match instead of taking the seed from the parameters: the bytecode scan would still pass, and
 * every match would still be unreplayable.
 */
public class BulanciDeterminismTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  public void theSameSeedLaysOutTheSameLevelAndTheSameSpawnPoints() {
    Map<String, Object> firstParams = new HashMap<>();
    String first = startMatch(firstParams);

    Map<String, Object> replayParams = new HashMap<>();
    replayParams.put(GameRandom.PARAM_SEED, firstParams.get(GameRandom.PARAM_SEED));

    assertEquals("a Bulanci match must replay from the seed it reported", first, startMatch(replayParams));
  }

  @Test
  public void aDifferentSeedGivesADifferentLevel() {
    Set<String> boards = new HashSet<>();
    for (long seed = 1; seed <= 6; seed++) {
      Map<String, Object> params = new HashMap<>();
      params.put(GameRandom.PARAM_SEED, seed);
      boards.add(startMatch(params));
    }
    assertTrue("the level and spawns must depend on the seed", boards.size() > 1);

    Map<String, Object> params = new HashMap<>();
    params.put(GameRandom.PARAM_SEED, 1L);
    Map<String, Object> other = new HashMap<>();
    other.put(GameRandom.PARAM_SEED, 2L);
    assertNotEquals(startMatch(params), startMatch(other));
  }

  /**
   * Starts a match on a fresh rule and returns its initial board as JSON.
   *
   * @param params game parameters, mutated with the seed that was actually used
   */
  private static String startMatch(Map<String, Object> params) {
    GameState<BulanciBoard> state = new GameState<>();
    state.setParams(params);

    List<Player> players = Arrays.asList(new Player("p1"), new Player("p2"), new Player("p3"));
    new BulanciRule().init(state, players);

    try {
      // The level is no longer part of the board's JSON - it is published once, in the match setup -
      // so it has to be compared explicitly, otherwise this test would only be comparing spawns.
      return MAPPER.writeValueAsString(state.getGame())
        + MAPPER.writeValueAsString(state.getGame().getObstacles());
    } catch (Exception e) {
      throw new IllegalStateException("board is not serialisable, so the client cannot render it either", e);
    }
  }
}
