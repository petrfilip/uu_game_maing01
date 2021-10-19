package uu.game.main.api.dto;

import java.util.HashMap;
import java.util.Map;

public class StartGameDtoIn {

  private String roomId;
  private String playerId;
  private Map<String, Object> gameParameters = new HashMap<>();

  public String getRoomId() {
    return roomId;
  }

  public void setRoomId(String roomId) {
    this.roomId = roomId;
  }

  public String getPlayerId() {
    return playerId;
  }

  public void setPlayerId(String playerId) {
    this.playerId = playerId;
  }

  public Map<String, Object> getGameParameters() {
    return gameParameters;
  }

  public void setGameParameters(Map<String, Object> gameParameters) {
    this.gameParameters = gameParameters;
  }
}
