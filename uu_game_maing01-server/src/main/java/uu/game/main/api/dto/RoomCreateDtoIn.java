package uu.game.main.api.dto;

import uu.app.validation.ValidationType;

@ValidationType("roomCreateDtoInType")
public class RoomCreateDtoIn {

  private String roomName;
  private String roomOwner;

  /**
   * Which game the room runs. Optional - falls back to the {@code game.defaultGame} property.
   * Rooms were previously hardwired to one game by editing {@code RoomAbl}, which is the whole
   * point the rule registry was supposed to solve.
   */
  private String gameName;

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

  public String getGameName() {
    return gameName;
  }

  public void setGameName(String gameName) {
    this.gameName = gameName;
  }
}
