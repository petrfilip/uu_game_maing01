package uu.game.main.game.soldat.specialability;

import java.util.List;
import org.springframework.stereotype.Service;
import uu.game.main.game.soldat.SoldatPlayer;

@Service
public class IncreasedSpeedSpecialAbility extends SpecialAbility {

  /** 15 seconds at a 50 ms tick. */
  private static final int DURATION_TICKS = 300;

  private static final int BONUS = 6;

  @Override
  public void applyAbility(SoldatPlayer player, List<SoldatPlayer> opponents, int tick) {
    player.grantSpeedBonus(BONUS, DURATION_TICKS);
  }
}
