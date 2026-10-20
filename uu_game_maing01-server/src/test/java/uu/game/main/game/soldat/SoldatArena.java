package uu.game.main.game.soldat;

import java.util.ArrayList;
import java.util.List;
import uu.game.main.abl.dto.Player;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GameArea;
import uu.game.main.game.common.Obstacle;
import uu.game.main.game.common.ObstacleTypeEnum;

/**
 * Small builder for a hand-made Soldat board, so a test says what the level looks like in one line
 * instead of twenty lines of setup.
 */
final class SoldatArena {

  private final SoldatBoard board = new SoldatBoard();
  private final List<Obstacle> obstacles = new ArrayList<>();

  private int nextPlayer = 1;

  private SoldatArena(int width, int height) {
    board.setArea(new GameArea(width, height));
    board.setObstacles(obstacles);
  }

  static SoldatArena of(int width, int height) {
    return new SoldatArena(width, height);
  }

  /** An arena so tall that nothing falls out of it, for testing free fall. */
  static SoldatArena bottomless() {
    return new SoldatArena(1000, 1_000_000);
  }

  SoldatArena withPlatform(double x, double y, int width, int height) {
    obstacles.add(new Obstacle(ObstacleTypeEnum.WALL, x, y, width, height));
    board.setObstacles(new ArrayList<>(obstacles));
    return this;
  }

  SoldatArena withPlayerCollision(boolean enabled) {
    board.setPlayerCollision(enabled);
    return this;
  }

  SoldatPlayer addPlayer(double x, double y) {
    return addPlayer("player-" + nextPlayer++, x, y);
  }

  SoldatPlayer addPlayer(String playerId, double x, double y) {
    Player player = new Player(playerId);
    player.setPlayerName(playerId);
    SoldatPlayer soldat = new SoldatPlayer(Direction.RIGHT, x, y, SoldatPlayer.SIZE, SoldatPlayer.SIZE);
    // The rule does this for a real match; events name the player by id.
    soldat.setPlayerId(playerId);
    board.getPlayers().put(player, soldat);
    return soldat;
  }

  SoldatBoard board() {
    return board;
  }

  /** Steps every player on the board once, the way the rule does. */
  void tick(int tick) {
    for (SoldatPlayer soldat : board.getPlayers().values()) {
      soldat.setCurrentTick(tick);
      soldat.step(board, tick);
    }
  }

  /**
   * Steps {@code soldat} until it is standing on something, so a test can start from a known
   * grounded state.
   *
   * @return the tick after the last one simulated
   */
  int settle(SoldatPlayer soldat, int fromTick) {
    int tick = fromTick;
    for (int i = 0; i < 200 && !soldat.isOnGround(); i++) {
      soldat.setCurrentTick(tick);
      soldat.step(board, tick);
      tick++;
    }
    if (!soldat.isOnGround()) {
      throw new IllegalStateException("player never landed - check the arena has a platform below it");
    }
    return tick;
  }
}
