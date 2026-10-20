package uu.game.main.game.common.ammo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collection;
import java.util.List;
import uu.game.main.abl.dto.Player;
import uu.game.main.game.common.GameArea;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.Obstacle;
import uu.game.main.game.common.PlayerMove;

public abstract class Ammo extends GameRectangle {

  private boolean isUsed = false;

  @JsonIgnore
  private Player owner;

  /**
   * The box of whoever fired this. Kept so a kill is never credited for the shooter's own death;
   * the owning {@link Player} and the entity it controls are different objects, so comparing the
   * two directly can never match.
   */
  @JsonIgnore
  private GameRectangle shooterEntity;

  protected Ammo(double x, double y, Integer width, Integer height) {
    super(x, y, width, height);
  }

  /**
   * Discriminator the client renders from.
   */
  public final String getType() {
    return this.getClass().getSimpleName();
  }

  /**
   * Advances the projectile by one tick and resolves anything it hits.
   *
   * @param targets everything damagable on the board
   * @param obstacles level geometry, so a projectile can be stopped by a wall
   * @param tick     current tick, for time based ammo
   * @param area     playfield bounds, so ammo that leaves the map is cleaned up
   */
  public abstract List<GameRuleEvent> applyEffect(Collection<? extends AmmoDamagable> targets,
                                                  Collection<Obstacle> obstacles,
                                                  int tick,
                                                  GameArea area);

  public boolean isUsed() {
    return isUsed;
  }

  public void setUsed(boolean used) {
    isUsed = used;
  }

  /**
   * Places the projectile in the world when it is fired.
   *
   * @param shooter the firing player's box; takes a {@link GameRectangle} rather than a
   *                {@code Player2D} so ammo stays independent of any game's player type
   */
  public abstract List<GameRuleEvent> init(Player owner, GameRectangle shooter, PlayerMove move, int tick);

  public Player getOwner() {
    return owner;
  }

  /**
   * @return the id of whoever fired this, for the {@code fired} event - the round itself has no
   *     identity worth publishing, but the player who sent it does
   */
  protected String ownerId() {
    return owner == null ? null : owner.getPlayerId();
  }

  public void setOwner(Player owner) {
    this.owner = owner;
  }

  public void setShooterEntity(GameRectangle shooterEntity) {
    this.shooterEntity = shooterEntity;
  }

  /**
   * Credits a kill to the shooter, never for self-inflicted damage.
   */
  protected void awardKill(AmmoDamagable target) {
    Player shooter = getOwner();
    if (shooter == null || target == shooterEntity) {
      return;
    }
    shooter.setScore(shooter.getScore() + 1);
  }
}
