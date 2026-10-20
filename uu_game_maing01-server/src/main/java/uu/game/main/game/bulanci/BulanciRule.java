package uu.game.main.game.bulanci;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import uu.game.main.abl.dto.Player;
import uu.game.main.abl.dto.PlayerStateEnum;
import uu.game.main.domain.GameState;
import uu.game.main.domain.GameStateEnum;
import uu.game.main.domain.IRule;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GameArea;
import uu.game.main.game.common.GameRandom;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.GameSetup;
import uu.game.main.game.common.Obstacle;
import uu.game.main.game.common.ObstacleTypeEnum;
import uu.game.main.game.common.ammo.Ammo;
import uu.game.main.game.common.ammo.AmmoDamagable;
import uu.game.main.game.common.ammo.Bullet;
import uu.game.main.game.common.ammo.Mine;

@Service("bulanci")
@Scope(scopeName = SCOPE_PROTOTYPE)
public class BulanciRule implements IRule<BulanciBoard, BulanciMove> {

  private static final int SPAWN_ATTEMPTS = 40;

  /** This match's randomness; see {@link GameRandom}. Created per match in {@link #init}. */
  private GameRandom random;

  /** Static parts of the current match; see {@link IRule#getSetup()}. */
  private GameSetup setup;

  @Override
  public Class<BulanciMove> getMoveClass() {
    return BulanciMove.class;
  }

  @Override
  public GameSetup getSetup() {
    return setup;
  }

  @Override
  public GameState<BulanciBoard> init(GameState<BulanciBoard> currentState, Collection<Player> players) {
    // Before the map is generated: both the trees and the spawn points are drawn from it, and a
    // replay has to get the same level.
    random = GameRandom.fromParams(currentState.getParams());

    GameArea area = GameArea.fromParams(currentState.getParams(), 800, 600);
    currentState.getParams().put(GameArea.PARAM_WIDTH, area.getWidth());
    currentState.getParams().put(GameArea.PARAM_HEIGHT, area.getHeight());

    BulanciBoard board = new BulanciBoard();
    board.setArea(area);
    board.setObstacles(generateObstacles(area));

    for (Player player : players) {
      player.setScore(0);
      player.setState(PlayerStateEnum.ACTIVE);

      GameRectangle spawn = findSpawnPoint(board, area);
      BulanciPlayer bulanciPlayer = new BulanciPlayer(Direction.DOWN, spawn.getX(), spawn.getY(), BulanciPlayer.SIZE, BulanciPlayer.SIZE);
      for (int i = 0; i < 4; i++) {
        bulanciPlayer.getAmmoList().add(new Bullet());
      }
      bulanciPlayer.getAmmoList().add(new Mine());
      bulanciPlayer.setPlayerId(player.getPlayerId());
      board.getPlayers().put(player, bulanciPlayer);
    }

    // Level and arena are drawn once, here, and never change - so they are published once too.
    setup = GameSetup.of(board, currentState.getParams());

    currentState.setGame(board);
    currentState.setState(GameStateEnum.RUNNING);
    currentState.setTick(0);
    return currentState;
  }

  @Override
  public GameState<BulanciBoard> getNextGameState(GameState<BulanciBoard> state, Map<Player, List<BulanciMove>> unprocessedMoves) {
    BulanciBoard board = state.getGame();
    int tick = state.getTick();
    List<GameRuleEvent> events = new ArrayList<>();

    for (Map.Entry<Player, BulanciPlayer> entry : board.getPlayers().entrySet()) {
      if (entry.getKey().getState() == PlayerStateEnum.DONE) {
        entry.getValue().setLives(0);
      }
    }

    for (Map.Entry<Player, BulanciPlayer> entry : board.getPlayers().entrySet()) {
      Player player = entry.getKey();
      BulanciPlayer bulanciPlayer = entry.getValue();
      if (player.getState() == PlayerStateEnum.DONE) {
        continue;
      }

      List<BulanciMove> moves = unprocessedMoves.getOrDefault(player, new ArrayList<>());
      for (BulanciMove move : moves) {
        bulanciPlayer.applyInput(move);

        Ammo fired = move.getFired();
        if (fired != null && !bulanciPlayer.isDead()) {
          events.addAll(fired.init(player, bulanciPlayer, move, tick));
          board.getAmmo().add(fired);
        }
      }

      bulanciPlayer.step(board, tick);
    }

    Collection<AmmoDamagable> targets = new ArrayList<>(board.getPlayers().values());
    Iterator<Ammo> iterator = board.getAmmo().iterator();
    while (iterator.hasNext()) {
      Ammo ammo = iterator.next();
      events.addAll(ammo.applyEffect(targets, board.getObstacles(), tick, board.getArea()));
      if (ammo.isUsed()) {
        iterator.remove();
      }
    }

    if (board.getPlayers().values().stream().filter(p -> !p.isDead()).count() <= 1
      && board.getPlayers().size() > 1) {
      // Last one standing. Previously the state never became FINISHED, so onGameEnd never fired and
      // the leaderboard was permanently empty.
      state.setState(GameStateEnum.FINISHED);
      events.add(new GameRuleEvent("finished", "game"));
    }

    state.setGameEvents(events);
    return state;
  }

  private List<Obstacle> generateObstacles(GameArea area) {
    List<Obstacle> obstacles = new ArrayList<>();

    obstacles.add(new Obstacle(ObstacleTypeEnum.MUSHROOM,
      new GameRectangle(0, 0, 100, 20), new GameRectangle(0, 100, 100, 20)));
    obstacles.add(new Obstacle(ObstacleTypeEnum.HOUSE, new GameRectangle(250, 250, 200, 200)));
    obstacles.add(new Obstacle(ObstacleTypeEnum.TAVERN, new GameRectangle(450, 50, 250, 250)));

    for (int i = 0; i < 3; i++) {
      Obstacle tree = null;
      for (int attempt = 0; attempt < SPAWN_ATTEMPTS && tree == null; attempt++) {
        Obstacle candidate = new Obstacle(ObstacleTypeEnum.TREE,
          random.getRandomNumber(0, area.getWidth() - 150), random.getRandomNumber(0, area.getHeight() - 150),
          random.getRandomNumber(100, 150), random.getRandomNumber(100, 150));
        if (obstacles.stream().noneMatch(existing -> candidate.getWalls().stream().anyMatch(existing::intersects))) {
          tree = candidate;
        }
      }
      // Bounded retries: the original do/while could spin forever once the map filled up, on the
      // request thread that started the game.
      if (tree != null) {
        obstacles.add(tree);
      }
    }

    return obstacles;
  }

  private GameRectangle findSpawnPoint(BulanciBoard board, GameArea area) {
    for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
      GameRectangle candidate = new GameRectangle(
        random.getRandomNumber(0, area.getWidth() - BulanciPlayer.SIZE),
        random.getRandomNumber(0, area.getHeight() - BulanciPlayer.SIZE),
        BulanciPlayer.SIZE, BulanciPlayer.SIZE);
      if (!board.terrain().collides(candidate)) {
        return candidate;
      }
    }
    return new GameRectangle(0, 0, BulanciPlayer.SIZE, BulanciPlayer.SIZE);
  }
}
