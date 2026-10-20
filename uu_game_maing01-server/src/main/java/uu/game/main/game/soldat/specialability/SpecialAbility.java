package uu.game.main.game.soldat.specialability;

import java.util.List;
import uu.game.main.game.soldat.SoldatPlayer;

/**
 * A pickup effect.
 *
 * <p>Abilities are now stateless: they hand a deadline to the affected player and are done. The
 * previous design kept {@code endTime} and a reference to the affected players inside the ability
 * and relied on the owner calling back to switch the effect off, which broke as soon as the owner
 * died and also meant two overlapping pickups could restore each other's stale values.
 */
public abstract class SpecialAbility {

  /**
   * @param player    the player who picked the item up
   * @param opponents everyone else, for abilities that target others
   * @param tick      current tick, the origin for any duration
   */
  public abstract void applyAbility(SoldatPlayer player, List<SoldatPlayer> opponents, int tick);

  /**
   * Identifier the client maps to a sprite.
   */
  public String getName() {
    return getClass().getSimpleName();
  }
}
