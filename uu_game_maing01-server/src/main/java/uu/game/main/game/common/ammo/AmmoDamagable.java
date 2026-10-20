package uu.game.main.game.common.ammo;

/**
 * Something a projectile can hit.
 */
public interface AmmoDamagable extends Intersectable {

  DamageResult applyAmmoDamage(int damage);
}
