package uu.game.main.game.common;

import java.util.Collection;
import java.util.List;

/**
 * Common shape of a game board: geometry plus the bounds entities are simulated in.
 */
public interface GameBoard {

  List<Obstacle> getObstacles();

  GameArea getArea();

  /**
   * Boxes that block movement this tick: level geometry plus, optionally, other players.
   */
  CollisionSpace collisionSpace(Collection<? extends GameRectangle> movers);
}
