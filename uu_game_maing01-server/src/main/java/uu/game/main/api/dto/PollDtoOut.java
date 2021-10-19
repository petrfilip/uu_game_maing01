package uu.game.main.api.dto;

import uu.app.dto.AbstractDtoOut;

public class PollDtoOut extends AbstractDtoOut {

  private Object output;

  public PollDtoOut() {
  }

  public PollDtoOut(Object output) {
    this.output = output;
  }

  public Object getOutput() {
    return output;
  }

  public void setOutput(Object output) {
    this.output = output;
  }
}
