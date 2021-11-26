package uu.game.main.abl;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import uu.game.main.abl.dto.GameCallback;
import uu.game.main.abl.dto.Player;
import uu.game.main.abl.dto.PlayerStateEnum;
import uu.game.main.domain.GameState;
import uu.game.main.domain.GameStateEnum;
import uu.game.main.domain.IRule;
import uu.game.main.game.soldat.SoldatMove;

@Component
@Scope(scopeName = SCOPE_PROTOTYPE)
public class GameInstanceAbl {

  private static final Logger LOGGER = LogManager.getLogger(GameInstanceAbl.class);


  @Inject
  private ObjectMapper objectMapper;
  private GameCallback gameCallback;


  private IRule rule;
  private GameState currentState = null;
  private Map<Player, Object> unprocessedMoves = new HashMap<>();
  private ScheduledFuture<?> scheduledFuture = null;


  private final Map<String, Player> players = new HashMap<>();


  public void setGame(IRule rule) {
    if (this.rule != null) {
      throw new IllegalStateException("Game is already set");
    }
    this.rule = rule;
  }


  public GameState addPlayer(Player player) {

    players.put(player.getPlayerId(), player);
    return currentState;
  }

  public GameState removePlayer(Integer playerId) {
    if (players.containsKey(playerId)) {
      players.get(playerId).setState(PlayerStateEnum.DONE);
    }
    return currentState;
  }

  public void addPlayerMove(String playerId, List<Object> playerMove) {
    if (currentState == null) {
      return;
    }

    if (currentState.getState().equals(GameStateEnum.WAITING) || currentState.getState().equals(GameStateEnum.FINISHED)) {
      LOGGER.warn("Game is not running!");
      return;
    }

    // check if player can do something new
    Player player = this.players.get(playerId);
    if (player.getState().equals(PlayerStateEnum.DONE)) {
      return;
    }

    Object move = objectMapper.convertValue(playerMove, new TypeReference<List<SoldatMove>>() {
    });

    unprocessedMoves.put(player, move);
    try {
      System.out.println(new StringBuilder().append("New player move in tick ").append(currentState.getTick()).append(":").append(objectMapper.writeValueAsString(playerMove)));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }

  public void calculateNextState() {
    GameState gameState = rule.getNextGameState(currentState, unprocessedMoves);
    gameState.setTick(gameState.getTick() + 1);

    // save state
    currentState = gameState;
    try {
      LOGGER.info("Current tick: {} :: {}", gameState.getTick(), objectMapper.writeValueAsString(currentState));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }

    if (gameState.getState().equals(GameStateEnum.FINISHED)) {
      scheduledFuture.cancel(false);
      gameCallback.onGameEnd(gameState);
    }

    unprocessedMoves = new HashMap<>();
    gameCallback.onNextRound(gameState);
  }

  public GameState startGame(GameCallback gameCallback, Map<String, Object> gameParameters) {
    this.gameCallback = gameCallback;
    if (scheduledFuture != null) {
      scheduledFuture.cancel(true);
    }

    unprocessedMoves = new HashMap<>();
    currentState = new GameState();
    currentState.setParams(gameParameters);
    rule.init(currentState, new ArrayList<>(players.values()));
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    scheduledFuture = executor.scheduleAtFixedRate(() -> {
      try {
        calculateNextState();
      } catch (Exception e) {
        LOGGER.error("Failed to calculate next state", e);
      }
    }, 0, 100, TimeUnit.MILLISECONDS);
    return currentState;
  }

}
