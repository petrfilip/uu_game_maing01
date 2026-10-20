package uu.game.main.game.common.ammo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import uu.game.main.abl.dto.Player;
import uu.game.main.game.common.GameArea;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.Obstacle;
import uu.game.main.game.common.PlayerMove;

/**
 * Proximity mine: arms itself a moment after being dropped, then detonates on contact.
 */
public class Mine extends Ammo {

  private static final int SIZE = 40;

  /**
   * Ticks between being dropped and becoming dangerous, so the player who placed it can step away.
   */
  private static final int ARMING_DELAY_TICKS = 20;

  private static final int DAMAGE = 10;

  private Integer armedAtTick;

  public Mine() {
    // Both dimensions are set here on purpose. init() used to call setWidth(40) twice, leaving
    // height null, and the first collision check then threw a NullPointerException on unboxing -
    // which the scheduler swallowed, so the game simply froze.
    super(0, 0, SIZE, SIZE);
  }

  @Override
  public List<GameRuleEvent> applyEffect(Collection<? extends AmmoDamagable> targets,
                                         Collection<Obstacle> obstacles,
                                         int tick,
                                         GameArea area) {
    // Was `armedAtTick - tick > 30`, which is never true because armedAtTick <= tick. The mine
    // could not detonate at all.
    if (armedAtTick == null || tick - armedAtTick < ARMING_DELAY_TICKS) {
      return new ArrayList<>();
    }

    for (AmmoDamagable target : targets) {
      if (!target.intersects(this)) {
        continue;
      }

      DamageResult result = target.applyAmmoDamage(DAMAGE);
      if (!result.isAbsorbed()) {
        continue;
      }

      List<GameRuleEvent> events = result.mutableEvents();
      events.add(new GameRuleEvent("used", getType(), null, this));
      if (result.isLethal()) {
        awardKill(target);
      }
      setUsed(true);
      return events;
    }

    return new ArrayList<>();
  }

  @Override
  public List<GameRuleEvent> init(Player owner, GameRectangle shooter, PlayerMove move, int tick) {
    setOwner(owner);
    setShooterEntity(shooter);
    setX(shooter.getCenterX() - SIZE / 2.0);
    setY(shooter.getBottom() - SIZE);
    armedAtTick = tick;

    return new GameRuleEvent("fired", getType(), ownerId(), this).asList();
  }

  public Integer getArmedAtTick() {
    return armedAtTick;
  }
}
