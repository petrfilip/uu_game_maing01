package uu.game.main.game.bulanci;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import uu.game.main.abl.dto.Player;
import uu.game.main.abl.dto.PlayerStateEnum;
import uu.game.main.domain.GameState;
import uu.game.main.domain.GameStateEnum;

public class BulanciRuleTest {

  @Test
  public void aDisconnectedPlayerCannotBeFarmedOrKeepTheMatchRunning() {
    Player departed = new Player("p1");
    Player remaining = new Player("p2");
    GameState<BulanciBoard> state = new GameState<>();
    BulanciRule rule = new BulanciRule();
    rule.init(state, Arrays.asList(departed, remaining));
    departed.setState(PlayerStateEnum.DONE);

    rule.getNextGameState(state, Collections.emptyMap());

    BulanciPlayer body = state.getGame().getPlayers().get(departed);
    assertTrue(body.isDead());
    assertFalse(body.applyAmmoDamage(100).isLethal());
    assertEquals(GameStateEnum.FINISHED, state.getState());
    assertTrue(state.getGame().getPlayers().containsKey(departed));
  }
}
