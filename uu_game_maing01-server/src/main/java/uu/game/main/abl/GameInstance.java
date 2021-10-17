package uu.game.main.abl;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import uu.game.main.abl.dto.Player;
import uu.game.main.domain.GameState;
import uu.game.main.domain.IRule;

@Component
@Scope(scopeName = SCOPE_PROTOTYPE)
public class GameInstance {

  private IRule rule;
  private GameState currentState = null;

  private final Map<String, Player> players = new HashMap<>();


  public void setGame(IRule rule) {
    if (this.rule != null) {
      throw new IllegalStateException("Game is already set");
    }
    this.rule = rule;
  }


  public GameState addPlayer(Player player) {

    players.put(player.getPlayerId(), player);
    return currentState;
  }

}
