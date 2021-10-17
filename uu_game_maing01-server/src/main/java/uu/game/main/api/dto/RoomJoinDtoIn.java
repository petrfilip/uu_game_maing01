package uu.game.main.api.dto;

import uu.app.validation.ValidationType;

@ValidationType("roomJoinDtoInType")
public class RoomJoinDtoIn {

  private String roomId;
  private String playerId;

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
}
