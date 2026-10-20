package uu.game.main.game.common.ammo;

import uu.game.main.game.common.Direction;
import uu.game.main.game.common.PlayerMove;

/**
 * The bare minimum {@link PlayerMove} the ammo classes read during {@code init}, so the projectile
 * tests do not depend on any particular game's move type.
 */
class StubMove implements PlayerMove {

  private Direction move;
  private Ammo fired;
  private boolean sprint;
  private double firedAngle;
  private Integer seq;

  static StubMove aimedAt(double degrees) {
    StubMove stub = new StubMove();
    stub.setFiredAngle(degrees);
    return stub;
  }

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
