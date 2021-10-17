package uu.game.main.game.draw;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import uu.game.main.abl.dto.Player;
import uu.game.main.domain.GameState;
import uu.game.main.domain.IRule;

@Service("draw")
@Scope(scopeName = SCOPE_PROTOTYPE)
public class DrawGameRule implements IRule<List<DrawGamePoint>, DrawGamePoint> {

  @Override
  public Class<DrawGamePoint> getMoveClass() {
    return DrawGamePoint.class;
  }

  @Override
  public GameState<List<DrawGamePoint>> init(GameState<List<DrawGamePoint>> currentState, Collection<Player> players) {
    currentState.setGame(currentState.getGame() != null ? currentState.getGame() : new ArrayList<>());
    currentState.setPlayers(players);
    return currentState;
  }

  @Override
  public GameState<List<DrawGamePoint>> getNextGameState(GameState<List<DrawGamePoint>> newGameState, Map<Player, DrawGamePoint> currentMoves) {
    newGameState.getGame().addAll(currentMoves.values());
    return newGameState;
  }
}
