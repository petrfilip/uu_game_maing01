package uu.game.main.game.soldat.specialability;

import java.util.List;
import uu.game.main.game.soldat.SoldatPlayer;

public abstract class SpecialAbility {

  private boolean callbackUsed = false;

  public abstract void applyAbility(SoldatPlayer soldatPlayer, List<SoldatPlayer> opponents);

  public void applyAbilityFinishedOnce() {
    if (!callbackUsed) {
      callbackUsed = true;
      applyAbilityFinished();
    }
  }

  public abstract void applyAbilityFinished();

  public abstract boolean isDone();
}
