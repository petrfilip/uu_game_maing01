package uu.game.main.api.dto;

public class PollDtoIn {

  private String roomId;

  /**
   * Asks for the full state instead of waiting for the next change.
   *
   * <p>A stream is handed one when it connects; a polling client has no connect to hang that on, so
   * it asks explicitly - on its first poll, and again whenever it has lost track of what it draws.
   */
  private boolean full;

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

  public boolean isFull() {
    return full;
  }

  public void setFull(boolean full) {
    this.full = full;
  }
}
