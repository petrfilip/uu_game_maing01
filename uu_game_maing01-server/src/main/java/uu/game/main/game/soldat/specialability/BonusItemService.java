package uu.game.main.game.soldat.specialability;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.springframework.stereotype.Service;
import uu.game.main.game.common.GameArea;
import uu.game.main.game.common.GameRandom;

/**
 * Drops bonus crates.
 *
 * <p>Abilities are injected once instead of being looked up through
 * {@code ApplicationContext.getBeansOfType} on every drop - which, with prototype scoped beans,
 * instantiated every ability type just to keep one of them.
 */
@Service
public class BonusItemService {

  private final List<SpecialAbility> abilities;

  @Inject
  public BonusItemService(List<SpecialAbility> abilities) {
    this.abilities = new ArrayList<>(abilities);
  }

  /**
   * @param random the calling match's randomness. It is a parameter rather than a field because
   *     this service is a singleton shared by every room: a generator kept here would be shared
   *     state, and one room's drop would shift another room's sequence.
   */
  public BonusItem generate(GameArea area, GameRandom random) {
    SpecialAbility ability = abilities.get(random.getRandomNumber(0, abilities.size()));
    int x = random.getRandomNumber(BonusItem.SIZE, Math.max(BonusItem.SIZE + 1, area.getWidth() - BonusItem.SIZE));
    return new BonusItem(x, 0, ability);
  }
}
