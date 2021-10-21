package uu.game.main.game.bulanci;

import java.util.ArrayList;
import java.util.List;
import uu.game.main.game.bulanci.ammo.Ammo;
import uu.game.main.game.common.GameRectangle;

public class BulanciPlayer extends GameRectangle {

  private Integer speed = 10;
  private List<Ammo> ammo = new ArrayList<>();
  private Integer lives = 1;

  public BulanciPlayer(Integer x, Integer y, Integer width, Integer height) {
    super(x, y, width, height);
  }

  public Integer getSpeed() {
    return speed;
  }

  public void setSpeed(Integer speed) {
    this.speed = speed;
  }

  public List<Ammo> getAmmo() {
    return ammo;
  }

  public void setAmmo(List<Ammo> ammo) {
    this.ammo = ammo;
  }

  public Integer getLives() {
    return lives;
  }

  public void setLives(Integer lives) {
    this.lives = lives;
  }
}
