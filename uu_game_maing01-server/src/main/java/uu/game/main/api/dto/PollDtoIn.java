package uu.game.main.api.dto;

public class PollDtoIn {

  private String roomId;

  public PollDtoIn() {
  }

  public PollDtoIn(String roomId) {
    this.roomId = roomId;
  }

  public String getRoomId() {
    return roomId;
  }

  public void setRoomId(String roomId) {
    this.roomId = roomId;
  }
}
