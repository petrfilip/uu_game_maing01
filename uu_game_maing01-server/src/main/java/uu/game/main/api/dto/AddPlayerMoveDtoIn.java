package uu.game.main.api.dto;

import java.util.List;

public class AddPlayerMoveDtoIn {

  private Integer tick;
  private String roomId;
  private String playerId;
  private List<Object> playerMoves;

  public Integer getTick() {
    return tick;
  }

  public void setTick(Integer tick) {
    this.tick = tick;
  }

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

  public List<Object> getPlayerMoves() {
    return playerMoves;
  }

  public void setPlayerMoves(List<Object> playerMoves) {
    this.playerMoves = playerMoves;
  }
}
