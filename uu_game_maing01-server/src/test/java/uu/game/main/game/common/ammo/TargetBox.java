package uu.game.main.game.common.ammo;

import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;

/**
 * Minimal damagable used by the projectile tests, so they exercise the projectile rather than a
 * particular game's player rules. Lives behave the way {@code SoldatPlayer} does: a corpse absorbs
 * nothing and reports {@link DamageResult#none()}.
 */
class TargetBox extends GameRectangle implements AmmoDamagable {

  private int lives;
  private int hits;

  TargetBox(double x, double y, int size, int lives) {
    super(x, y, size, size);
    this.lives = lives;
  }

  @Override
  public DamageResult applyAmmoDamage(int damage) {
    if (lives <= 0) {
      return DamageResult.none();
    }
    hits++;
    lives -= damage;
    if (lives > 0) {
      return DamageResult.absorbed(new GameRuleEvent("liveDecreased", "TargetBox", null, this).asList());
    }
    lives = 0;
    return DamageResult.lethal(new GameRuleEvent("death", "TargetBox", null, this).asList());
  }

  int getLives() {
    return lives;
  }

  int getHits() {
    return hits;
  }
}
