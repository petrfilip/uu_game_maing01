package uu.game.main.api.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import uu.app.validation.ValidationType;

@ValidationType("gameMainInitDtoInType")
public final class GameMainInitDtoIn {

  private String uuAppProfileAuthorities;

  public String getUuAppProfileAuthorities() {
    return uuAppProfileAuthorities;
   }

  public void setUuAppProfileAuthorities(String uuAppProfileAuthorities) {
    this.uuAppProfileAuthorities = uuAppProfileAuthorities;
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
