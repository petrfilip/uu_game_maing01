package uu.game.main.game.soldat.specialability;

import java.util.List;
import org.springframework.stereotype.Service;
import uu.game.main.game.soldat.SoldatPlayer;

@Service
public class FreezeOpponentsSpecialAbility extends SpecialAbility {

  /** 8 seconds at a 50 ms tick. Fifteen was long enough to decide a whole round. */
  private static final int DURATION_TICKS = 160;

  @Override
  public void applyAbility(SoldatPlayer player, List<SoldatPlayer> opponents, int tick) {
    for (SoldatPlayer opponent : opponents) {
      opponent.freeze(DURATION_TICKS);
    }
  }
}
