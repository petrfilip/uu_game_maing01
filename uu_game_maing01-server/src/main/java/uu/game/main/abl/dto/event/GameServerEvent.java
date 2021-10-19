package uu.game.main.abl.dto.event;

import org.springframework.context.ApplicationEvent;

public abstract class GameServerEvent<T> extends ApplicationEvent {

  private final T output;

  public GameServerEvent(Object source, T output) {
    super(source);
    this.output = output;
  }

  public T getOutput() {
    return output;
  }
}

