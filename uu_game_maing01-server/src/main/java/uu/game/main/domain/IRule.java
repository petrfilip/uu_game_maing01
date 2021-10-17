package uu.game.main.domain;

import java.util.Collection;
import java.util.Map;
import uu.game.main.abl.dto.Player;

public interface IRule<STATE, MOVE> {

  Class<MOVE> getMoveClass();

  GameState<STATE> init(GameState<STATE> currentState, Collection<Player> players);

  GameState<STATE> getNextGameState(GameState<STATE> newGameState, Map<Player, MOVE> unprocessedMoves);
}
