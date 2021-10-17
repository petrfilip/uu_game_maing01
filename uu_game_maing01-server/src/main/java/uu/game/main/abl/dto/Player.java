package uu.game.main.abl.dto;

public class Player {

  private String playerId;

  private String playerName;

  private PlayerStateEnum state = PlayerStateEnum.WAITING;

  private Integer score = 0;


  public Player() {
  }

  public Player(String playerId) {
    this.playerId = playerId;
  }

  public String getPlayerId() {
    return playerId;
  }

  public void setPlayerId(String playerId) {
    this.playerId = playerId;
  }

  public String getPlayerName() {
    return playerName;
  }

  public void setPlayerName(String playerName) {
    this.playerName = playerName;
  }

  public PlayerStateEnum getState() {
    return state;
  }

  public void setState(PlayerStateEnum state) {
    this.state = state;
  }

  public Integer getScore() {
    return score;
  }

  public void setScore(Integer score) {
    this.score = score;
  }
}
