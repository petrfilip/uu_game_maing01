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
import uu.game.main.helper.MathUtils;

public class Bullet extends Projectile {

  /**
   * Pixels travelled per tick. At a 50 ms tick this is 300 px/s, which crosses the 1400 px map in
   * a little under five seconds.
   */
  private static final int SPEED = 15;

  private static final int SIZE = 10;

  public Bullet() {
    super(0, 0, SIZE, SIZE, 0);
  }

  @Override
  public List<GameRuleEvent> applyEffect(Collection<? extends AmmoDamagable> targets,
                                         Collection<Obstacle> obstacles,
                                         int tick,
                                         GameArea area) {
    int remaining = getSpeed();

    while (remaining > 0) {
      int step = Math.min(getStepSize(), remaining);
      remaining -= step;
      advance(step);

      if (isOutOfBounds(area)) {
        // Previously missing: a bullet that hit nothing flew forever and stayed in the board's ammo
        // list, so the list - and the state pushed to every client each tick - grew without bound.
        setUsed(true);
        return new ArrayList<>();
      }

      for (AmmoDamagable target : targets) {
        if (!target.intersects(this)) {
          continue;
        }

        DamageResult result = target.applyAmmoDamage(1);
        if (!result.isAbsorbed()) {
          continue;
        }

        // Where the bullet died is where the client draws the impact. The victim publishes its own
        // event, with its own id and position, so repeating the target here would be a second copy
        // of something already in the payload.
        List<GameRuleEvent> events = result.mutableEvents();
        events.add(new GameRuleEvent("used", getType(), null, this));

        if (result.isLethal()) {
          awardKill(target);
        }
        setUsed(true);
        return events;
      }

      for (Obstacle obstacle : obstacles) {
        if (obstacle.intersects(this)) {
          setUsed(true);
          return new GameRuleEvent("used", getType(), null, this).asList();
        }
      }
    }

    return new ArrayList<>();
  }

  @Override
  public List<GameRuleEvent> init(Player owner, GameRectangle shooter, PlayerMove move, int tick) {
    setOwner(owner);
    setShooterEntity(shooter);
    setAngle(move.getFiredAngle());

    // Spawn on the muzzle rather than inside the shooter, so the shot does not immediately collide
    // with the player who fired it.
    double offset = Math.hypot(shooter.getWidth(), shooter.getHeight()) / 2 + SIZE;
    setX(shooter.getCenterX() - SIZE / 2.0 + offset * MathUtils.cos(getAngle()));
    setY(shooter.getCenterY() - SIZE / 2.0 + offset * MathUtils.sin(getAngle()));

    return new GameRuleEvent("fired", getType(), ownerId(), this).asList();
  }

  @Override
  public int getSpeed() {
    return SPEED;
  }
}
