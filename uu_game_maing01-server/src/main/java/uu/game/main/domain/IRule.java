package uu.game.main.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import uu.game.main.abl.dto.Player;
import uu.game.main.game.common.GameSetup;
import uu.game.main.game.common.PlayerMove;

/**
 * A game, expressed as the pure-ish function the engine drives:
 * {@code nextState = rule(currentState, moves)}.
 *
 * <p>Implementations are registered as prototype-scoped Spring beans named after the game
 * ({@code @Service("soldat")}); {@link uu.game.main.engine.GameEngineRegistry} resolves them by
 * name, so adding a game touches no engine code.
 *
 * @param <STATE> the game specific board
 * @param <MOVE>  the game specific input; bounded so the engine can hand the rule a list of moves
 *                without knowing the concrete type
 */
public interface IRule<STATE, MOVE extends PlayerMove> {

  /**
   * The concrete move type, used by the engine to deserialise client input. Implementations must
   * return the real class - the engine has no other way to know it.
   */
  Class<MOVE> getMoveClass();

  GameState<STATE> init(GameState<STATE> currentState, Collection<Player> players);

  GameState<STATE> getNextGameState(GameState<STATE> newGameState, Map<Player, List<MOVE>> unprocessedMoves);

  /**
   * The parts of the match that {@link #init} fixed and nothing can change afterwards.
   *
   * <p>Asked of the rule rather than read off a state object on purpose: a rule is free to hand back
   * a brand new state on every tick, and the static parts must survive that. They are published once
   * per client instead of on every tick.
   *
   * @return the setup of the match {@code init} started, or {@code null} before there is one
   */
  GameSetup getSetup();
}
