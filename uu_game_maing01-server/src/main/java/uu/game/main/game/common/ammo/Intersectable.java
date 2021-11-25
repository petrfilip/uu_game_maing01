package uu.game.main.game.common.ammo;

import java.util.Collection;
import uu.game.main.game.common.GameRectangle;

public interface Intersectable {

  default boolean intersects(Collection<? extends GameRectangle> gameRectangles) {
    for (GameRectangle intersectable : gameRectangles) {
      if (intersects(intersectable)) {
        return true;
      }
    }
    return false;
  }

  boolean intersects(GameRectangle hitArea);
}
