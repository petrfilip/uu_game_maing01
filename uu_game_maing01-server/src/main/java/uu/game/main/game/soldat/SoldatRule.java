package uu.game.main.game.soldat;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import uu.game.main.abl.ScoreAbl;
import uu.game.main.abl.dto.Player;
import uu.game.main.abl.dto.PlayerStateEnum;
import uu.game.main.domain.GameState;
import uu.game.main.domain.GameStateEnum;
import uu.game.main.domain.IRule;
import uu.game.main.game.bulanci.Obstacle;
import uu.game.main.game.bulanci.ObstacleTypeEnum;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GamePlayMode;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.GameplayModeEnum;
import uu.game.main.game.common.ScoreLimitGameplayMode;
import uu.game.main.game.common.TimeLimitGameplayMode;
import uu.game.main.game.common.ammo.AmmoDamagable;
import uu.game.main.game.soldat.specialability.BonusItem;
import uu.game.main.game.soldat.specialability.BonusItemService;
import uu.game.main.game.soldat.specialability.SpecialAbility;
import uu.game.main.helper.Utils;

@Service("soldat")
@Scope(scopeName = SCOPE_PROTOTYPE)
public class SoldatRule implements IRule<SoldatBoard, SoldatMove> {

  private GamePlayMode gameplayMode;

  @Inject
  private ScoreAbl scoreAbl;

  @Inject
  private BonusItemService bonusItemService;

  private ZonedDateTime nextBonusItemDrop;

  @Override
  public Class<SoldatMove> getMoveClass() {
    return SoldatMove.class;
  }

  @Override
  public GameState<SoldatBoard> init(GameState<SoldatBoard> currentState, Collection<Player> players) {
    nextBonusItemDrop = ZonedDateTime.now().plusSeconds(15);
    if (players != null) {
      Player botPlayer = new Player();
      botPlayer.setPlayerId("0-0");
      botPlayer.setPlayerName("bot");
      botPlayer.setState(PlayerStateEnum.ACTIVE);
      players.add(botPlayer);
    }

    currentState.getParams().putIfAbsent("gameMode", GameplayModeEnum.TIME_LIMIT.name());

    GameplayModeEnum gameplayModeEnum = getGamePlayMode((String) currentState.getParams().get("gameMode"));
    if (gameplayModeEnum.equals(GameplayModeEnum.TIME_LIMIT)) {
      gameplayMode = new TimeLimitGameplayMode(ZonedDateTime.now().plusSeconds((Long) currentState.getParams().getOrDefault("gameModeSecondLimit", 300L)));
    } else {
      gameplayMode = new ScoreLimitGameplayMode((Integer) currentState.getParams().getOrDefault("gameModeStopAtScore", 5));
    }

    currentState.getParams().putIfAbsent("initialLives", SoldatPlayer.INIT_LIVES);
    currentState.getParams().putIfAbsent("areaWidth", 800);
    currentState.getParams().putIfAbsent("areaHeight", 600);

    currentState.setState(GameStateEnum.RUNNING);
    currentState.setGame(currentState.getGame() != null ? currentState.getGame() : new SoldatBoard());

    for (Player player : players) {
      SoldatPlayer bulanciPlayer = new SoldatPlayer(Direction.DOWN, Utils.getRandomNumber(0, 500), Utils.getRandomNumber(0, 500), 30, 30);
      currentState.getGame().getPlayers().put(player, bulanciPlayer);
    }

    currentState.getGame().setObstacles(generateObstacles(currentState.getGame().getPlayers().values()));

    return currentState;
  }

  private GameplayModeEnum getGamePlayMode(String gameMode) {
    return GameplayModeEnum.find(gameMode, GameplayModeEnum.TIME_LIMIT);
  }

  private List<Obstacle> generateObstacles(Collection<SoldatPlayer> players) {
    List<Obstacle> obstacles = new ArrayList<>(); //todo if generate ; add support to load predefined maps

    obstacles.add(new Obstacle(ObstacleTypeEnum.WOODEN_BOX, new GameRectangle(100, 200, 50, 50)));
    obstacles.add(new Obstacle(ObstacleTypeEnum.METAL_BOX, new GameRectangle(300, 300, 100, 100)));

    obstacles.add(new Obstacle(ObstacleTypeEnum.WOODEN_BOX, new GameRectangle(500, 300, 50, 50)));
    obstacles.add(new Obstacle(ObstacleTypeEnum.WOODEN_BOX, new GameRectangle(550, 300, 50, 50)));

    addWall(obstacles, 400, 500, 8);
    addWall(obstacles, 50, 350, 4);

    return obstacles;
  }

  private void addWall(List<Obstacle> obstacles, int x, int y, int count) {
    for (int i = 0; i < count; i++) {
      obstacles.add(new Obstacle(ObstacleTypeEnum.WALL, new GameRectangle(x + (i * 50), y, 50, 50)));
    }
  }

  @Override
  public GameState<SoldatBoard> getNextGameState(GameState<SoldatBoard> newGameState, Map<Player, List<SoldatMove>> unprocessedMoves) {

    SoldatBoard game = newGameState.getGame();

    final List<GameRuleEvent> events = new ArrayList<>(100);

    // player moves
    for (Player player : newGameState.getGame().getPlayers().keySet()) {
      List<SoldatMove> moveList = unprocessedMoves.getOrDefault(player, new ArrayList<>(0));

      for (int i = 0; i < 100; i++) {

        SoldatMove move = moveList.size() > i ? moveList.get(i) : new SoldatMove();

        SoldatPlayer soldatPlayer = game.getPlayers().get(player);
        soldatPlayer.movePlayer(move, game);

        if (move.getFired() != null) {
          events.addAll(move.getFired().init(player, soldatPlayer, move));
          game.getAmmo().add(move.getFired()); //todo check if has this kind of amo
        }
        game.getPlayers().put(player, soldatPlayer);
      }
    }

    // remove special ability from board
    game.setBonusItemList(game.getBonusItemList().stream()
      .peek(item -> item.checkIntersection(newGameState.getGame().getPlayers().values()))
      .peek(item -> item.moveBonusItem(game.getObstacles())).filter(item -> !item.isUsed()).collect(Collectors.toList()));
    if (nextBonusItemDrop.isBefore(ZonedDateTime.now())) {
      game.getBonusItemList().add(bonusItemService.generate());
      nextBonusItemDrop = ZonedDateTime.now().plusSeconds(20);
    }

    // affect damagable with ammo
    Collection<AmmoDamagable> damagable = new ArrayList<>();
    damagable.addAll(game.getPlayers().values());
    damagable.addAll(game.getObstacles());
    game.setAmmo(game.getAmmo().stream()
      .peek(ammo -> events.addAll(ammo.applyEffect(damagable, newGameState.getTick())))
      .filter(ammo -> !ammo.isUsed()).collect(Collectors.toList()));

    if (gameplayMode.isGameFinished(newGameState.getGame().getPlayers().keySet())) {
      scoreAbl.calculateEloAndPersist(((String) newGameState.getParams().get("awid")), newGameState.getGame().getPlayers().keySet());
      //todo scoreAbl.calculateEloAndPersist
      newGameState.setState(GameStateEnum.FINISHED);
      events.add(new GameRuleEvent("finished", "game", ZonedDateTime.now()));
    }

    newGameState.setGameEvents(events);
    return newGameState;
  }
}
