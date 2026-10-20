package uu.game.main.engine;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.PlayerMove;
import uu.game.main.game.common.ammo.Ammo;

/**
 * A move type that belongs to no real game, so the engine tests can prove the engine deserialises
 * with whatever class the rule declares rather than with a hardcoded one. {@code firedAngle} doubles
 * as an opaque tag so a test can tell submissions apart - unlike {@code seq}, the engine attaches no
 * meaning to it, which is what makes it usable as a label.
 */
public class FakeMove implements PlayerMove {

  private Direction move;
  private boolean sprint;
  private double firedAngle;
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
  @JsonIgnore
  public Ammo getFired() {
    return null;
  }

  @Override
  @JsonIgnore
  public void setFired(Ammo fired) {
    // This game has no ammo.
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
