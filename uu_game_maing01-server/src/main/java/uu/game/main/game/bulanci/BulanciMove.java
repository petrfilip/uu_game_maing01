package uu.game.main.game.bulanci;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import uu.game.main.game.common.ammo.Ammo;
import uu.game.main.game.bulanci.helper.AmmoDeserializer;
import uu.game.main.game.common.Direction;

public class BulanciMove {

  private Direction move;

  @JsonDeserialize(using = AmmoDeserializer.class)
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
