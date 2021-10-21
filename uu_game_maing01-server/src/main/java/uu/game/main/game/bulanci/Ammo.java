package uu.game.main.game.bulanci;

import java.awt.Rectangle;
import java.util.Collection;

public abstract class Ammo {


  private Integer x;
  private Integer y;
  private Integer width;
  private Integer height;
  private boolean isUsed = false;


  abstract void applyEffect(Collection<BulanciPlayer> value);

  public Rectangle getRectangle() {
    return new Rectangle(x, y, width, height);
  }

  public Integer getX() {
    return x;
  }

  public void setX(Integer x) {
    this.x = x;
  }

  public Integer getY() {
    return y;
  }

  public void setY(Integer y) {
    this.y = y;
  }

  public Integer getWidth() {
    return width;
  }

  public void setWidth(Integer width) {
    this.width = width;
  }

  public Integer getHeight() {
    return height;
  }

  public void setHeight(Integer height) {
    this.height = height;
  }

  public boolean isUsed() {
    return isUsed;
  }

  public void setUsed(boolean used) {
    isUsed = used;
  }
}
