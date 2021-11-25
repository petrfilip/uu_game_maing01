package uu.game.main.game.bulanci;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import uu.game.main.abl.dto.Player;
import uu.game.main.domain.GameState;
import uu.game.main.domain.GameStateEnum;
import uu.game.main.domain.IRule;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.ammo.AmmoDamagable;
import uu.game.main.game.common.ammo.Bullet;
import uu.game.main.game.common.ammo.Mine;
import uu.game.main.helper.Utils;

@Service("bulanci")
@Scope(scopeName = SCOPE_PROTOTYPE)
public class BulanciRule implements IRule<BulanciBoard, BulanciMove> {


  @Override
  public Class<BulanciMove> getMoveClass() {
    return BulanciMove.class;
  }

  @Override
  public GameState<BulanciBoard> init(GameState<BulanciBoard> currentState, Collection<Player> players) {

    currentState.setState(GameStateEnum.RUNNING);
    currentState.setGame(currentState.getGame() != null ? currentState.getGame() : new BulanciBoard());

    for (Player player : players) {
      BulanciPlayer bulanciPlayer = new BulanciPlayer(Direction.DOWN, Utils.getRandomNumber(0, 500), Utils.getRandomNumber(0, 500), 30, 30);
      bulanciPlayer.getAmmoList().add(new Bullet());
      bulanciPlayer.getAmmoList().add(new Bullet());
      bulanciPlayer.getAmmoList().add(new Bullet());
      bulanciPlayer.getAmmoList().add(new Bullet());
      bulanciPlayer.getAmmoList().add(new Mine());
      currentState.getGame().getPlayers().put(player, bulanciPlayer);
    }

    currentState.getGame().setObstacles(generateObstacles(currentState.getGame().getPlayers().values()));

    return currentState;
  }

  private List<Obstacle> generateObstacles(Collection<BulanciPlayer> players) {
    List<Obstacle> obstacles = new ArrayList<>(); //todo if generate ; add support to load predefined maps

    obstacles.add(new Obstacle(ObstacleTypeEnum.MUSHROOM, new GameRectangle(0, 0, 100, 20), new GameRectangle(0, 100, 100, 20)));
    obstacles.add(new Obstacle(ObstacleTypeEnum.HOUSE, new GameRectangle(250, 250, 200, 200)));
    obstacles.add(new Obstacle(ObstacleTypeEnum.TAVERN, new GameRectangle(450, 50, 250, 250)));

    for (int i = 0; i < 3; i++) {
      Obstacle obstacle;
      do {
        obstacle = new Obstacle(ObstacleTypeEnum.TREE, Utils.getRandomNumber(0, 500), Utils.getRandomNumber(0, 500), Utils.getRandomNumber(100, 150), Utils.getRandomNumber(100, 150));
      } while (obstacleIntersected(obstacle, obstacles) || obstacle.intersects(players));
      obstacles.add(obstacle);
    }

    return obstacles;
  }

  private boolean obstacleIntersected(Obstacle obstacle, List<Obstacle> obstacles) {
    return obstacles.stream().anyMatch((o -> o.intersects(obstacle.getWalls())));
  }

  @Override
  public GameState<BulanciBoard> getNextGameState(GameState<BulanciBoard> newGameState, Map<Player, List<BulanciMove>> unprocessedMoves) {

    BulanciBoard game = newGameState.getGame();

    final List<GameRuleEvent> events = new ArrayList<>();

    // player moves
    for (Player player : newGameState.getGame().getPlayers().keySet()) {
      List<BulanciMove> bulanciMoveList = unprocessedMoves.getOrDefault(player, new ArrayList<>());
      for (BulanciMove bulanciMove : bulanciMoveList) {

        BulanciPlayer bulanciPlayer = game.getPlayers().get(player);
        bulanciPlayer.movePlayer(bulanciMove, game);

        if (bulanciMove.getFired() != null) {
          events.addAll(bulanciMove.getFired().init(player, bulanciPlayer, bulanciMove));
          game.getAmmo().add(bulanciMove.getFired()); //todo check if has this kind of amo
        }
        game.getPlayers().put(player, bulanciPlayer);
      }
    }

    // affect damagable with ammo
    Collection<AmmoDamagable> damagable = new ArrayList<>();
    damagable.addAll(game.getPlayers().values());
    damagable.addAll(game.getObstacles());
    game.setAmmo(game.getAmmo().stream()
      .peek(ammo -> events.addAll(ammo.applyEffect(damagable, newGameState.getTick())))
      .filter(ammo -> !ammo.isUsed()).collect(Collectors.toList()));

    newGameState.setGameEvents(events);
    return newGameState;
  }


  private BulanciPlayer calculateNextStep(BulanciPlayer player, BulanciMove move) {


    return player;
  }
}
