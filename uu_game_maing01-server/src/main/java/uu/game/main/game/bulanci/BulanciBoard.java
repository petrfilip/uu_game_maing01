package uu.game.main.game.bulanci;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import uu.game.main.abl.dto.Player;
import uu.game.main.game.common.CollisionSpace;
import uu.game.main.game.common.GameArea;
import uu.game.main.game.common.GameBoard;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.Obstacle;
import uu.game.main.game.common.ammo.Ammo;
import uu.game.main.helper.PlayerSerializer;

public class BulanciBoard implements GameBoard {

  @JsonSerialize(keyUsing = PlayerSerializer.class)
  private Map<Player, BulanciPlayer> players = new LinkedHashMap<>();
  private List<Ammo> ammo = new ArrayList<>();

  /**
   * The level. Fixed once the match has started, so it travels in
   * {@link uu.game.main.game.common.GameSetup} instead of in every tick.
   */
  @JsonIgnore
  private List<Obstacle> obstacles = new ArrayList<>();

  /** Static, see {@link #obstacles}. */
  @JsonIgnore
  private GameArea area = new GameArea(800, 600);

  /** Static, see {@link #obstacles}. */
  @JsonIgnore
  private boolean playerCollision = true;

  /** Lazily built, see {@link #terrain()}; dropped whenever the level is replaced. */
  @JsonIgnore
  private transient CollisionSpace terrain;

  @Override
  public CollisionSpace collisionSpace(Collection<? extends GameRectangle> movers) {
    if (!playerCollision || movers == null) {
      return terrain();
    }
    return terrain().withMovers(movers);
  }

  /**
   * The level on its own, built once and kept: it cannot change after {@code init}, and it was
   * being rebuilt for every entity on every tick - and once per candidate while looking for a spawn
   * point.
   */
  @JsonIgnore
  public CollisionSpace terrain() {
    CollisionSpace cached = terrain;
    if (cached == null) {
      cached = new CollisionSpace();
      for (Obstacle obstacle : obstacles) {
        cached.addAll(obstacle.getWalls());
      }
      terrain = cached;
    }
    return cached;
  }

  public Map<Player, BulanciPlayer> getPlayers() {
    return players;
  }

  public void setPlayers(Map<Player, BulanciPlayer> players) {
    this.players = players;
  }

  @Override
  public List<Obstacle> getObstacles() {
    return obstacles;
  }

  public void setObstacles(List<Obstacle> obstacles) {
    this.obstacles = obstacles;
    // The cached space describes the level that was just replaced.
    this.terrain = null;
  }

  public List<Ammo> getAmmo() {
    return ammo;
  }

  public void setAmmo(List<Ammo> ammo) {
    this.ammo = ammo;
  }

  @Override
  public GameArea getArea() {
    return area;
  }

  public void setArea(GameArea area) {
    this.area = area;
  }

  public boolean isPlayerCollision() {
    return playerCollision;
  }

  public void setPlayerCollision(boolean playerCollision) {
    this.playerCollision = playerCollision;
  }
}
