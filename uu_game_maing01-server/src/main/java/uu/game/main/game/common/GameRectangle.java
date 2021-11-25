package uu.game.main.game.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.awt.Rectangle;
import uu.game.main.game.common.ammo.Intersectable;

public class GameRectangle implements Intersectable {

  private Integer x;
  private Integer y;
  private Integer width;
  private Integer height;

  public GameRectangle(Integer x, Integer y, Integer width, Integer height) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
  }

  @JsonIgnore
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

  @Override
  public boolean intersects(GameRectangle hitArea) {
    return this.getRectangle().intersects(hitArea.getRectangle());
  }
}
