package uu.game.main.abl.entity;

import uu.app.objectstore.mongodb.domain.AbstractUuObject;

public class Score extends AbstractUuObject {

  private String uuIdentity;
  private String playerName;
  private Integer score = 1000;

  public String getUuIdentity() {
    return uuIdentity;
  }

  public void setUuIdentity(String uuIdentity) {
    this.uuIdentity = uuIdentity;
  }

  public String getPlayerName() {
    return playerName;
  }

  public void setPlayerName(String playerName) {
    this.playerName = playerName;
  }

  public Integer getScore() {
    return score;
  }

  public void setScore(Integer score) {
    this.score = score;
  }
}
