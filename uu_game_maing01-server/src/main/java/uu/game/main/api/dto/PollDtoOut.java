package uu.game.main.api.dto;

import uu.app.dto.AbstractDtoOut;

public class PollDtoOut extends AbstractDtoOut {

  private Object output;
  private String eventType;

  public PollDtoOut() {
  }

  public PollDtoOut(Object output, String eventType) {
    this.output = output;
    this.eventType = eventType;
  }

  public Object getOutput() {
    return output;
  }

  public void setOutput(Object output) {
    this.output = output;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }
}
