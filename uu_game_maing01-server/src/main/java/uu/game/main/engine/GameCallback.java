package uu.game.main.engine;

import uu.game.main.domain.GameState;

/**
 * Notified by {@link GameEngine} from the tick thread. Implementations must not block - anything
 * slow (network fan-out in particular) has to be handed off, otherwise it stalls the simulation.
 */
public interface GameCallback {

  void onGameEnd(GameState gameState);

  void onNextRound(GameState gameState);
}
