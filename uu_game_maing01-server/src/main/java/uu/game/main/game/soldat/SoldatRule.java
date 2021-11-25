package uu.game.main.game.soldat;

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
import uu.game.main.game.bulanci.BulanciBoard;
import uu.game.main.game.bulanci.BulanciMove;
import uu.game.main.game.bulanci.BulanciPlayer;
import uu.game.main.game.bulanci.Obstacle;
import uu.game.main.game.bulanci.ObstacleTypeEnum;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.ammo.AmmoDamagable;
import uu.game.main.game.common.ammo.Bullet;
import uu.game.main.game.common.ammo.Mine;
import uu.game.main.helper.Utils;

@Service("soldat")
@Scope(scopeName = SCOPE_PROTOTYPE)
public class SoldatRule implements IRule<SoldatBoard, SoldatMove> {

  private static final Integer SPRINT_KOEF = 1;

  @Override
  public Class<SoldatMove> getMoveClass() {
    return SoldatMove.class;
  }

  @Override
  public GameState<SoldatBoard> init(GameState<SoldatBoard> currentState, Collection<Player> players) {
    currentState.setState(GameStateEnum.RUNNING);
    currentState.setGame(currentState.getGame() != null ? currentState.getGame() : new SoldatBoard());

    for (Player player : players) {
      SoldatPlayer bulanciPlayer = new SoldatPlayer(Direction.DOWN, Utils.getRandomNumber(0, 500), Utils.getRandomNumber(0, 500), 30, 30);
      currentState.getGame().getPlayers().put(player, bulanciPlayer);
    }

    currentState.getGame().setObstacles(generateObstacles(currentState.getGame().getPlayers().values()));

    return currentState;
  }

  private List<Obstacle> generateObstacles(Collection<SoldatPlayer> players) {
    List<Obstacle> obstacles = new ArrayList<>(); //todo if generate ; add support to load predefined maps

    obstacles.add(new Obstacle(ObstacleTypeEnum.WOODEN_BOX, new GameRectangle(0, 500, 500, 5)));
    obstacles.add(new Obstacle(ObstacleTypeEnum.CONCRETE_BLOCK, new GameRectangle(200, 500, 500, 5)));
    obstacles.add(new Obstacle(ObstacleTypeEnum.CONCRETE_BLOCK, new GameRectangle(500, 500, 500, 50)));

    return obstacles;
  }

  @Override
  public GameState<SoldatBoard> getNextGameState(GameState<SoldatBoard> newGameState, Map<Player, List<SoldatMove>> unprocessedMoves) {

    SoldatBoard game = newGameState.getGame();

    final List<GameRuleEvent> events = new ArrayList<>();

    // player moves
    for (Player player : newGameState.getGame().getPlayers().keySet()) {
      List<SoldatMove> moveList = unprocessedMoves.getOrDefault(player, new ArrayList<>());
      for (SoldatMove move : moveList) {

        SoldatPlayer soldatPlayer = game.getPlayers().get(player);
        soldatPlayer.movePlayer(soldatPlayer, move);

        if (move.getFired() != null) {
          events.addAll(move.getFired().init(soldatPlayer));
          game.getAmmo().add(move.getFired()); //todo check if has this kind of amo
        }
        game.getPlayers().put(player, soldatPlayer);
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


}
