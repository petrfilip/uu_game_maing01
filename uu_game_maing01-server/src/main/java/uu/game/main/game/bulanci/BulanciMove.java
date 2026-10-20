package uu.game.main.game.bulanci;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.PlayerMove;
import uu.game.main.game.common.ammo.Ammo;
import uu.game.main.game.common.ammo.AmmoDeserializer;

public class BulanciMove implements PlayerMove {

  private Direction move;

  @JsonDeserialize(using = AmmoDeserializer.class)
  private Ammo fired;

  private boolean sprint;
  private double firedAngle;

  /** See {@link PlayerMove#getSeq()}. Set by the client, never by the server. */
  private Integer seq;

  @Override
  public Direction getMove() {
    return move;
  }

  @Override
  public void setMove(Direction move) {
    this.move = move;
  }

  @Override
  public Ammo getFired() {
    return fired;
  }

  @Override
  public void setFired(Ammo fired) {
    this.fired = fired;
  }

  @Override
  public boolean isSprint() {
    return sprint;
  }

  @Override
  public void setSprint(boolean sprint) {
    this.sprint = sprint;
  }

  @Override
  public double getFiredAngle() {
    return firedAngle;
  }

  @Override
  public void setFiredAngle(double firedAngle) {
    this.firedAngle = firedAngle;
  }

  @Override
  public Integer getSeq() {
    return seq;
  }

  @Override
  public void setSeq(Integer seq) {
    this.seq = seq;
  }
}
