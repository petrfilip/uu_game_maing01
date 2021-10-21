package uu.game.main.game.bulanci;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.ArrayList;
import java.util.List;
import uu.game.main.game.bulanci.ammo.Ammo;
import uu.game.main.game.bulanci.helper.PlayerAmmoSerialization;
import uu.game.main.game.common.GameRectangle;

public class BulanciPlayer extends GameRectangle {

  private Integer speed = 10;
  @JsonSerialize(using = PlayerAmmoSerialization.class)
  private List<Ammo> ammoList = new ArrayList<>();
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

  public List<Ammo> getAmmoList() {
    return ammoList;
  }

  public void setAmmoList(List<Ammo> ammoList) {
    this.ammoList = ammoList;
  }

  public Integer getLives() {
    return lives;
  }

  public void setLives(Integer lives) {
    this.lives = lives;
  }
}
