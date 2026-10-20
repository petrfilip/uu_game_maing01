package uu.game.main.game.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything about a match that is decided in {@code init} and cannot change afterwards: the arena,
 * the level geometry and the settings the rule resolved.
 *
 * <p>All of this used to be serialised into every single tick - around seventy obstacle walls,
 * twenty times a second, to every subscriber of the room - although not one byte of it differs from
 * the tick before. It is published once instead: in the full state a client receives when it
 * connects and when a match starts. See {@link uu.game.main.domain.GameState#isFull()}.
 *
 * <p>The parameter map is copied rather than referenced: it is a snapshot of the settings as they
 * stood when the match started, and a client that is told them once must not have to wonder whether
 * a later write changed them behind its back.
 */
public class GameSetup {

  private final GameArea area;

  private final List<Obstacle> obstacles;

  private final Map<String, Object> params;

  public GameSetup(GameArea area, List<Obstacle> obstacles, Map<String, Object> params) {
    this.area = area;
    this.obstacles = obstacles == null ? Collections.<Obstacle>emptyList() : new ArrayList<>(obstacles);
    this.params = params == null ? Collections.<String, Object>emptyMap() : new LinkedHashMap<>(params);
  }

  /**
   * Builds the setup of a board that has just been initialised.
   *
   * @param params the match parameters as the rule resolved them; anything that must not reach a
   *     browser has to be out of this map by now
   */
  public static GameSetup of(GameBoard board, Map<String, Object> params) {
    return new GameSetup(board.getArea(), board.getObstacles(), params);
  }

  public GameArea getArea() {
    return area;
  }

  public List<Obstacle> getObstacles() {
    return obstacles;
  }

  public Map<String, Object> getParams() {
    return params;
  }
}
