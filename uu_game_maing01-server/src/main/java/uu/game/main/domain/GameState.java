package uu.game.main.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import uu.game.main.abl.dto.Player;
import uu.game.main.game.common.GameRuleEvent;

public class GameState<STATE> {

  private Integer tick = 0;

  private GameStateEnum state = GameStateEnum.WAITING;

  private Map<String, Object> params = new HashMap<>();

  private Collection<Player> players = new HashSet<>();

  private STATE game;

  private List<GameRuleEvent> gameEvents = new ArrayList<>();

  public Integer getTick() {
    return tick;
  }

  public void setTick(Integer tick) {
    this.tick = tick;
  }

  public GameStateEnum getState() {
    return state;
  }

  public void setState(GameStateEnum state) {
    this.state = state;
  }

  public Map<String, Object> getParams() {
    return params;
  }

  public void setParams(Map<String, Object> params) {
    this.params = params;
  }

  public Collection<Player> getPlayers() {
    return players;
  }

  public void setPlayers(Collection<Player> players) {
    this.players = players;
  }

  public STATE getGame() {
    return game;
  }

  public void setGame(STATE game) {
    this.game = game;
  }

  public List<GameRuleEvent> getGameEvents() {
    return gameEvents;
  }

  public void setGameEvents(List<GameRuleEvent> gameEvents) {
    this.gameEvents = gameEvents;
  }

  @Override
  public String toString() {
    return "GameState{" +
      "tick=" + tick +
      ", state=" + state +
      ", params=" + params +
      ", players=" + players +
      ", game=" + game +
      '}';
  }
}
