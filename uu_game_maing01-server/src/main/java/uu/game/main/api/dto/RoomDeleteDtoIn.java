package uu.game.main.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import uu.app.validation.ValidationType;

/**
 * Input of {@code room/delete}.
 *
 * <p>Unknown keys are tolerated because the client's list component sends its own bookkeeping
 * alongside the id ({@code forceDelete}), and a delete failing on a field nobody reads would be a
 * poor trade.
 */
@ValidationType("roomDeleteDtoInType")
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoomDeleteDtoIn {

  private String roomId;

  /** Set by the controller from the session; never trusted from the request. */
  private String playerId;

  public String getRoomId() {
    return roomId;
  }

  public void setRoomId(String roomId) {
    this.roomId = roomId;
  }

  /**
   * Alias for {@link #setRoomId(String)}. The uu5 data-list component deletes an item by posting the
   * item itself, whose identifier is {@code id}; every other room command calls the same value
   * {@code roomId}, and the server keeps that name.
   */
  public void setId(String id) {
    this.roomId = id;
  }

  public String getPlayerId() {
    return playerId;
  }

  public void setPlayerId(String playerId) {
    this.playerId = playerId;
  }
}
