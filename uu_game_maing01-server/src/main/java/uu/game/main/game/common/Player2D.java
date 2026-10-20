package uu.game.main.game.common;

/**
 * A player entity that is simulated on a 2D board.
 *
 * <p>Input and simulation are deliberately separate calls. They used to be one
 * {@code movePlayer(move, board)} that a rule invoked once per queued move, which tied everything
 * derived from it - gravity, jump progress, respawn - to how many input packets happened to arrive.
 * A player holding a key on a fast machine moved further per second than one on a slow machine, and
 * a player sending nothing at all was not simulated at all.
 *
 * <p>The contract now is: {@link #applyInput} may be called zero or more times per tick and only
 * records intent; {@link #step} is called exactly once per tick and advances physics.
 */
public interface Player2D<PLAYER extends GameRectangle, MOVE extends PlayerMove, BOARD extends GameBoard> {

  /**
   * Records intent from one client move. Must not move the player.
   */
  void applyInput(MOVE move);

  /**
   * Advances the player by exactly one tick and clears the intent recorded by {@link #applyInput}.
   */
  PLAYER step(BOARD board, int tick);

  /**
   * Facing, which is what the client renders the sprite and the muzzle from.
   */
  Direction getDirection();

  void setDirection(Direction direction);
}
