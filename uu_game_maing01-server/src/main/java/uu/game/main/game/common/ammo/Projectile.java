package uu.game.main.game.common.ammo;

public abstract class Projectile extends Ammo {

  private double angle;

  public Projectile(int x, int y, int width, int height, double angle) {
    super(x, y, width, height);

    this.angle = angle;
  }

  public abstract int getSpeed();

  public abstract void computeNextPosition();

  public boolean checkOutOfBound(){
    return getX() < 0 || getX() > 800 || getY() < 0 || getY() > 600;
  }

  public double getAngle() {
    return angle;
  }

  public void setAngle(double angle) {
    this.angle = angle;
  }
}
