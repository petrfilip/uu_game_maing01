package uu.game.main.game.soldat;

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
import uu.game.main.game.common.GameRandom;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.Obstacle;
import uu.game.main.game.common.ammo.Ammo;
import uu.game.main.game.soldat.specialability.BonusItem;
import uu.game.main.helper.PlayerSerializer;

public class SoldatBoard implements GameBoard {

  private static final int SPAWN_ATTEMPTS = 40;

  @JsonSerialize(keyUsing = PlayerSerializer.class)
  private Map<Player, SoldatPlayer> players = new LinkedHashMap<>();
  private List<Ammo> ammo = new ArrayList<>();
  private List<BonusItem> bonusItemList = new ArrayList<>();

  /**
   * The level. Fixed once the match has started, so it is published through
   * {@link uu.game.main.game.common.GameSetup} rather than in every tick - seventy odd walls that
   * cannot move were the bulk of every payload the server sent.
   */
  @JsonIgnore
  private List<Obstacle> obstacles = new ArrayList<>();

  /** Static, see {@link #obstacles}. */
  @JsonIgnore
  private GameArea area = new GameArea(1400, 600);

  /** Static, see {@link #obstacles}. */
  @JsonIgnore
  private int initialLives = SoldatPlayer.INIT_LIVES;

  /**
   * Whether soldiers block each other. Players used to pass straight through one another; it is a
   * game parameter rather than a constant because it genuinely changes how the game plays. Static,
   * see {@link #obstacles}.
   */
  @JsonIgnore
  private boolean playerCollision = true;

  /**
   * The match's randomness. It lives on the board because respawning happens deep inside
   * {@link SoldatPlayer#step}, which only has the board to hand; the rule replaces it in
   * {@code init} with the seeded instance for the match. The default keeps a board built outside a
   * rule - a test arena, say - able to spawn players.
   */
  @JsonIgnore
  private GameRandom random = GameRandom.withGeneratedSeed();

  /** Lazily built, see {@link #terrain()}; dropped whenever the level is replaced. */
  @JsonIgnore
  private transient CollisionSpace terrain;

  /** Lazily built, see {@link #navigation()}; dropped whenever the level is replaced. */
  @JsonIgnore
  private transient SoldatNavigation navigation;

  @Override
  public CollisionSpace collisionSpace(Collection<? extends GameRectangle> movers) {
    if (!playerCollision || movers == null) {
      return terrain();
    }
    return terrain().withMovers(movers);
  }

  /**
   * Collision space containing level geometry only - used by falling pickups, which should not be
   * caught mid-air by a player standing underneath.
   *
   * <p>Built once and kept: the level cannot change after {@code init}, and rebuilding it for every
   * entity on every tick was the bulk of what a match allocated.
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

  /**
   * Which platforms of the level lead to which, for anything that has to find its way across.
   *
   * <p>Built once and kept, for the same reason the collision space is: the level cannot change
   * after {@code init}, and a tick should be reading this, not rebuilding it.
   */
  @JsonIgnore
  public SoldatNavigation navigation() {
    SoldatNavigation cached = navigation;
    if (cached == null) {
      cached = SoldatNavigation.of(obstacles);
      navigation = cached;
    }
    return cached;
  }

  /**
   * Finds somewhere to (re)spawn: standing on a platform rather than hanging in mid-air.
   *
   * <p>Respawn used to be a raw {@code random(0,500)} in both axes, which regularly dropped players
   * inside an obstacle, or over a pit where they fell straight back out of the map.
   */
  public GameRectangle findSpawnPoint(int width, int height) {
    // Living players count as blockers too, otherwise two soldiers can spawn inside one another and
    // the first collision step shoves them apart in a visible jump.
    List<GameRectangle> alive = new ArrayList<>(players.size());
    for (SoldatPlayer player : players.values()) {
      if (!player.isDead()) {
        alive.add(player);
      }
    }
    // Layered on, not added: the terrain space is shared by every entity in the match, and adding
    // today's living players to it would make them permanent walls.
    CollisionSpace occupied = terrain().withMovers(alive);

    List<GameRectangle> platforms = new ArrayList<>();
    for (Obstacle obstacle : obstacles) {
      for (GameRectangle wall : obstacle.getWalls()) {
        if (wall.getY() > height && wall.getY() < area.getHeight()) {
          platforms.add(wall);
        }
      }
    }

    for (int attempt = 0; attempt < SPAWN_ATTEMPTS && !platforms.isEmpty(); attempt++) {
      GameRectangle platform = platforms.get(random.getRandomNumber(0, platforms.size()));
      // Spread along the platform rather than always its centre, so two players picking the same
      // platform do not land on the same pixel.
      double x = random.getRandomNumber((int) platform.getX(), (int) Math.max(platform.getX() + 1, platform.getRight() - width));
      double y = platform.getY() - height - 1;
      GameRectangle candidate = new GameRectangle(area.clampX(x, width), y, width, height);
      if (!occupied.collides(candidate)) {
        return candidate;
      }
    }

    for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
      double x = random.getRandomNumber(0, Math.max(1, area.getWidth() - width));
      double y = random.getRandomNumber(0, Math.max(1, area.getHeight() / 2));
      GameRectangle candidate = new GameRectangle(x, y, width, height);
      if (!occupied.collides(candidate)) {
        return candidate;
      }
    }

    // Fall back to the top centre; gravity will drop it onto whatever is below.
    return new GameRectangle(area.getWidth() / 2.0 - width / 2.0, 0, width, height);
  }

  void setRandom(GameRandom random) {
    this.random = random;
  }

  public Map<Player, SoldatPlayer> getPlayers() {
    return players;
  }

  public void setPlayers(Map<Player, SoldatPlayer> players) {
    this.players = players;
  }

  @Override
  public List<Obstacle> getObstacles() {
    return obstacles;
  }

  public void setObstacles(List<Obstacle> obstacles) {
    this.obstacles = obstacles;
    // The cached space and routes describe the level that was just replaced.
    this.terrain = null;
    this.navigation = null;
  }

  public List<Ammo> getAmmo() {
    return ammo;
  }

  public void setAmmo(List<Ammo> ammo) {
    this.ammo = ammo;
  }

  public List<BonusItem> getBonusItemList() {
    return bonusItemList;
  }

  public void setBonusItemList(List<BonusItem> bonusItemList) {
    this.bonusItemList = bonusItemList;
  }

  @Override
  public GameArea getArea() {
    return area;
  }

  public void setArea(GameArea area) {
    this.area = area;
  }

  public int getInitialLives() {
    return initialLives;
  }

  public void setInitialLives(int initialLives) {
    this.initialLives = initialLives;
  }

  public boolean isPlayerCollision() {
    return playerCollision;
  }

  public void setPlayerCollision(boolean playerCollision) {
    this.playerCollision = playerCollision;
  }
}
