package uu.game.main.game.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.awt.Rectangle;
import uu.game.main.game.common.ammo.Intersectable;

public class GameRectangle implements Intersectable {

  private double x;
  private double y;
  private Integer width;
  private Integer height;

  public GameRectangle(double x, double y, Integer width, Integer height) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
  }

  @JsonIgnore
  public Rectangle getRectangle() {
    return new Rectangle(((int) x), ((int) y), width, height);
  }

  public double getX() {
    return x;
  }

  public void setX(double x) {
    this.x = x;
  }

  public double getY() {
    return y;
  }

  public void setY(double y) {
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

  @Override
  public boolean intersects(GameRectangle hitArea) {
    return this.getRectangle().intersects(hitArea.getRectangle());
  }
}
