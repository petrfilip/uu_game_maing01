package uu.game.main.game.bulanci;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import java.util.Collection;
import java.util.Map;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import uu.game.main.abl.dto.Player;
import uu.game.main.domain.GameState;
import uu.game.main.domain.IRule;

@Service("bulanci")
@Scope(scopeName = SCOPE_PROTOTYPE)
public class BulanciRule implements IRule<Board, BulanciMove> {


  @Override
  public Class<BulanciMove> getMoveClass() {
    return null;
  }

  @Override
  public GameState<Board> init(GameState<Board> currentState, Collection<Player> players) {
    return null;
  }

  @Override
  public GameState<Board> getNextGameState(GameState<Board> newGameState, Map<Player, BulanciMove> unprocessedMoves) {

    Board game = newGameState.getGame();

    // player moves
    for (Player player : newGameState.getPlayers()) {
      BulanciMove bulanciMove = unprocessedMoves.get(player);
      BulanciPlayer bulanciPlayer = game.getPlayer().get(player);
      bulanciPlayer = calculateNextBulanciStep(bulanciPlayer, bulanciMove);
      game.getPlayer().put(player, bulanciPlayer);
    }

    //todo apply ammo effect

    for (Ammo ammo : game.getAmmo()) {

      //affect players with amo
      ammo.applyEffect(game.getPlayer().values());

    }

    return newGameState;
  }


  private BulanciPlayer calculateNextBulanciStep(BulanciPlayer player, BulanciMove move) {

    switch (move.getMove()) { //todo check for collision with wall or another players
      case RIGHT:
        player.setX(player.getX() + player.getSpeed());
        break;
      case LEFT:
        player.setX(player.getX() - player.getSpeed());
        break;
      case UP:
        player.setY(player.getY() + player.getSpeed());
        break;
      case DOWN:
        player.setY(player.getY() - player.getSpeed());
        break;
    }
    return player;
  }
}
