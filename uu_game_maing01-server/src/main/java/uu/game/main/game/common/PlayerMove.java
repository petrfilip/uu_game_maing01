package uu.game.main.game.common;

import uu.game.main.game.common.ammo.Ammo;

/**
 * One piece of client input. The engine deserialises a list of these per request; a rule decides
 * how many it honours per tick.
 */
public interface PlayerMove {

  Direction getMove();

  void setMove(Direction move);

  Ammo getFired();

  void setFired(Ammo fired);

  boolean isSprint();

  void setSprint(boolean sprint);

  /**
   * Aim, in degrees, measured the same way the canvas measures them: 0 is right, 90 is down.
   */
  double getFiredAngle();

  void setFiredAngle(double firedAngle);

  /**
   * The client's own number for this input, monotonically increasing per player within one match.
   *
   * <p>It exists so the server can tell a client <em>which</em> of its inputs the simulation has
   * already dealt with - see {@link uu.game.main.domain.GameState#getLastProcessedSeq()}. A client
   * that predicts its own movement locally needs that answer to know which of its keystrokes it
   * still has to replay on top of the authoritative position it was just sent; without it the only
   * safe thing it can do is throw its prediction away on every state, which is the rubber-banding
   * every player saw before.
   *
   * <p>Nothing on the server requires it: the engine ignores it for anything but the acknowledgement
   * watermark, so a client that does not number its input (or a bot, which never travels over the
   * wire at all) keeps working exactly as before.
   *
   * @return the sequence number the client tagged this move with, or {@code null} when it did not
   */
  Integer getSeq();

  void setSeq(Integer seq);
}
