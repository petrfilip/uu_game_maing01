package uu.game.main.game.draw;

import java.util.ArrayList;
import java.util.List;

public class DrawGamePallet {

  private List<DrawGamePoint> points = new ArrayList<>();

  public List<DrawGamePoint> getPoints() {
    return points;
  }

  public void setPoints(List<DrawGamePoint> points) {
    this.points = points;
  }
}
