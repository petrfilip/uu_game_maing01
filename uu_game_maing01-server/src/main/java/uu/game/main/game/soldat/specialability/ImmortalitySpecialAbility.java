package uu.game.main.game.soldat.specialability;

import java.util.List;
import org.springframework.stereotype.Service;
import uu.game.main.game.soldat.SoldatPlayer;

@Service
public class ImmortalitySpecialAbility extends SpecialAbility {

  /** 10 seconds at a 50 ms tick. */
  private static final int DURATION_TICKS = 200;

  @Override
  public void applyAbility(SoldatPlayer player, List<SoldatPlayer> opponents, int tick) {
    // Marks the player invulnerable instead of setting lives to 999 and restoring the old value
    // later - that trick lost any damage or healing that happened in between, and showed a
    // nonsensical health bar while it was active.
    player.grantInvulnerability(DURATION_TICKS);
  }
}
