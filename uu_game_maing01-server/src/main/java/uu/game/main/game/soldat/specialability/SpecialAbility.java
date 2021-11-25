package uu.game.main.game.soldat.specialability;

import java.util.List;
import uu.game.main.game.soldat.SoldatPlayer;

public interface SpecialAbility {

  void applyAbility(SoldatPlayer soldatPlayer, List<SoldatPlayer> opponents);

  void applyAbilityFinished();

  boolean isDone();
}
