package uu.game.main.game.common.ammo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uu.game.main.game.common.GameArea;
import uu.game.main.helper.MathUtils;
import uu.game.main.game.common.CompactDoubleSerializer;

/**
 * Ammo that travels along a straight line.
 */
public abstract class Projectile extends Ammo {

  private double angle;

  protected Projectile(double x, double y, Integer width, Integer height, double angle) {
    super(x, y, width, height);
    this.angle = angle;
  }

  /**
   * @return distance travelled per tick, in pixels
   */
  public abstract int getSpeed();

  /**
   * Movement is resolved in sub-steps of at most this many pixels so a fast projectile cannot
   * tunnel through a thin wall between two ticks.
   */
  protected int getStepSize() {
    return 5;
  }

  public void advance(double distance) {
    setX(getX() + distance * MathUtils.cos(angle));
    setY(getY() + distance * MathUtils.sin(angle));
  }

  public boolean isOutOfBounds(GameArea area) {
    return area.isOutOfBounds(this);
  }

  @JsonSerialize(using = CompactDoubleSerializer.class)
  public double getAngle() {
    return angle;
  }

  public void setAngle(double angle) {
    this.angle = angle;
  }
}
