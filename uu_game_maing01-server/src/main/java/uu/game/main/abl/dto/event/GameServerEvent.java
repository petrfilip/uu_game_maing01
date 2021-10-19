package uu.game.main.abl.dto.event;

import org.springframework.context.ApplicationEvent;

public abstract class GameServerEvent<T> extends ApplicationEvent {

  private final String awid;
  private final String roomId;

  private final T output;

  public GameServerEvent(Object source, String awid, String roomId, T output) {
    super(source);
    this.awid = awid;
    this.roomId = roomId;
    this.output = output;
  }

  public T getOutput() {
    return output;
  }

  public String getAwid() {
    return awid;
  }

  public String getRoomId() {
    return roomId;
  }
}

