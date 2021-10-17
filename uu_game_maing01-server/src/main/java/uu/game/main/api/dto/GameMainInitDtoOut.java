package uu.game.main.api.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import uu.app.exception.AppErrorMap;
import uu.app.dto.AbstractDtoOut;

public class GameMainInitDtoOut extends AbstractDtoOut {

  private AppErrorMap uuAppErrorMap;

  public AppErrorMap getUuAppErrorMap() {
    return uuAppErrorMap;
  }

  public void setUuAppErrorMap(AppErrorMap uuAppErrorMap) {
    this.uuAppErrorMap = uuAppErrorMap;
  }

  @Override
  public String toString() {
    ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
    try {
      return ow.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      return super.toString();
    }
  }

}
