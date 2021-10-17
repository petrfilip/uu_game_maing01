package uu.game.main.api.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import uu.app.dto.AbstractDtoOut;
import uu.app.server.dto.PagedResultDtoOut;
import uu.game.main.abl.entity.Score;

public class ScoreGetDtoOut extends AbstractDtoOut {

  private Score score;

  public ScoreGetDtoOut() {
  }

  public ScoreGetDtoOut(Score score) {
    this.score = score;
  }

  public Score getScore() {
    return score;
  }

  public void setScore(Score score) {
    this.score = score;
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
