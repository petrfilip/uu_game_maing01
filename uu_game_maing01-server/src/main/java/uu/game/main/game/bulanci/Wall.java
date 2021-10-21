package uu.game.main.game.bulanci;

import uu.game.main.game.common.GameRectangle;

public class Wall extends GameRectangle {

  private String type;

  public Wall(String type, Integer x, Integer y, Integer width, Integer height) {
    super(x, y, width, height);
    this.type = type;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

}
