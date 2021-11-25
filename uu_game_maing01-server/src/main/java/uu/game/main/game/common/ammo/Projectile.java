package uu.game.main.game.common.ammo;

public abstract class Projectile extends Ammo {

  private int angle;

  public Projectile(int x, int y, int width, int height, int angle) {
    super(x, y, width, height);

    this.angle = angle;
  }

  public abstract int getSpeed();

  public abstract void computeNextPosition();

  public int getAngle() {
    return angle;
  }

  public void setAngle(int angle) {
    this.angle = angle;
  }
}
