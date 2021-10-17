package uu.game.main.api.dto;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import uu.app.validation.ValidationType;

@ValidationType("scoreGetByUuIdentityDtoInType")
public final class ScoreGetByUuIdentityDtoIn {

  private String uuIdentity;

  public String getUuIdentity() {
    return uuIdentity;
  }

  public void setUuIdentity(String uuIdentity) {
    this.uuIdentity = uuIdentity;
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
