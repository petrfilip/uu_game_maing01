package uu.game.main.game.bulanci;

import uu.game.main.game.bulanci.ammo.Ammo;
import uu.game.main.game.common.Direction;

public class BulanciMove {

  private Direction move;

  private Ammo fired; //todo only ammo name

  private boolean sprint;

  public Direction getMove() {
    return move;
  }

  public void setMove(Direction move) {
    this.move = move;
  }

  public Ammo getFired() {
    return fired;
  }

  public void setFired(Ammo fired) {
    this.fired = fired;
  }

  public boolean isSprint() {
    return sprint;
  }

  public void setSprint(boolean sprint) {
    this.sprint = sprint;
  }
}
