package uu.game.main.game.soldat.specialability;

import java.util.List;
import org.springframework.stereotype.Service;
import uu.game.main.game.soldat.SoldatPlayer;

@Service
public class IncreaseLiveSpecialAbility extends SpecialAbility {

  @Override
  public void applyAbility(SoldatPlayer player, List<SoldatPlayer> opponents, int tick) {
    player.heal();
  }
}
