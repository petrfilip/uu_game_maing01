package uu.game.main.api.dto;

import uu.app.validation.ValidationType;

@ValidationType("roomCreateDtoInType")
public class RoomCreateDtoIn {

  private String roomName;
  private String roomOwner;

  public String getRoomName() {
    return roomName;
  }

  public void setRoomName(String roomName) {
    this.roomName = roomName;
  }

  public String getRoomOwner() {
    return roomOwner;
  }

  public void setRoomOwner(String roomOwner) {
    this.roomOwner = roomOwner;
  }
}
