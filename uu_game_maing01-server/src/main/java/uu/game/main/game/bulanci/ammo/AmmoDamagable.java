package uu.game.main.game.bulanci.ammo;

import java.util.List;
import uu.game.main.game.common.GameRuleEvent;

public interface AmmoDamagable {

  List<GameRuleEvent> applyAmmoDamage(Integer damage);
}
