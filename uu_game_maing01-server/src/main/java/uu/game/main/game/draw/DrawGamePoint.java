package uu.game.main.game.draw;

import java.io.Serializable;

public class DrawGamePoint implements Serializable {

  private Integer x;
  private Integer y;
  private String color;

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

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }
}
