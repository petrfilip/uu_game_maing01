package uu.game.main.api.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import uu.app.dto.AbstractDtoOut;
import uu.app.exception.AppErrorMap;
import uu.app.server.dto.PagedResultDtoOut;
import uu.game.main.abl.entity.Score;

public class ScoreListDtoOut extends PagedResultDtoOut<Score> {

  public ScoreListDtoOut() {
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
