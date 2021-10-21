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
import uu.game.main.game.bulanci.ammo.Bullet;
import uu.game.main.game.bulanci.ammo.Mine;
import uu.game.main.helper.Utils;

@Service("bulanci")
@Scope(scopeName = SCOPE_PROTOTYPE)
public class BulanciRule implements IRule<BulanciBoard, BulanciMove> {

  private static final Integer SPRINT_KOEF = 2;

  @Override
  public Class<BulanciMove> getMoveClass() {
    return BulanciMove.class;
  }

  @Override
  public GameState<BulanciBoard> init(GameState<BulanciBoard> currentState, Collection<Player> players) {

    currentState.setState(GameStateEnum.RUNNING);
    currentState.setGame(currentState.getGame() != null ? currentState.getGame() : new BulanciBoard());

    for (Player player : players) {
      BulanciPlayer bulanciPlayer = new BulanciPlayer(Utils.getRandomNumber(0, 500), Utils.getRandomNumber(0, 500), 30, 30);
      bulanciPlayer.getAmmo().add(new Bullet());
      bulanciPlayer.getAmmo().add(new Bullet());
      bulanciPlayer.getAmmo().add(new Bullet());
      bulanciPlayer.getAmmo().add(new Bullet());
      bulanciPlayer.getAmmo().add(new Mine());
      currentState.getGame().getPlayers().put(player, bulanciPlayer);
    }

    List<Wall> walls = new ArrayList<>(); //todo if generate ; add support to load predefined maps
    for (int i = 0; i < 5; i++) {
      walls.add(new Wall("tree", Utils.getRandomNumber(0, 30), Utils.getRandomNumber(0, 30), 30, 30));
    }
    currentState.getGame().setWall(walls);

    return currentState;
  }

  @Override
  public GameState<BulanciBoard> getNextGameState(GameState<BulanciBoard> newGameState, Map<Player, BulanciMove> unprocessedMoves) {

    BulanciBoard game = newGameState.getGame();

    // player moves
    for (Player player : newGameState.getPlayers()) {
      BulanciMove bulanciMove = unprocessedMoves.getOrDefault(player, new BulanciMove());
      BulanciPlayer bulanciPlayer = game.getPlayers().get(player);
      calculateNextStep(bulanciPlayer, bulanciMove);

      if (bulanciMove.getFired() instanceof Bullet) {
        ((Bullet) bulanciMove.getFired()).setDirection(bulanciMove.getMove());
      }

      if (bulanciMove.getFired() != null) {
        game.getAmmo().add(bulanciMove.getFired()); //todo check if has this kind of amo
      }
      game.getPlayers().put(player, bulanciPlayer);
    }

    // affect players with ammo
    game.setAmmo(game.getAmmo().stream()
      .peek(ammo -> ammo.applyEffect(game.getPlayers().values(), newGameState.getTick()))
      .filter(ammo -> !ammo.isUsed()).collect(Collectors.toList()));

    return newGameState;
  }


  private BulanciPlayer calculateNextStep(BulanciPlayer player, BulanciMove move) {

    //todo check for collision with wall or another players
    //todo add sprint

    if (move.getMove() == null) {
      return player;
    }

    switch (move.getMove()) {
      case RIGHT:
        player.setX(player.getX() + (player.getSpeed() * SPRINT_KOEF));
        break;
      case LEFT:
        player.setX(player.getX() - (player.getSpeed() * SPRINT_KOEF));
        break;
      case UP:
        player.setY(player.getY() + (player.getSpeed() * SPRINT_KOEF));
        break;
      case DOWN:
        player.setY(player.getY() - (player.getSpeed() * SPRINT_KOEF));
        break;
    }
    return player;
  }
}
