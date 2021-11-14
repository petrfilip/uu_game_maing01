package uu.game.main.game.bulanci;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import uu.game.main.game.bulanci.ammo.Ammo;
import uu.game.main.game.bulanci.ammo.AmmoDamagable;
import uu.game.main.game.bulanci.helper.PlayerAmmoSerialization;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;

public class BulanciPlayer extends GameRectangle implements AmmoDamagable {

  private Integer speed = 10;
  @JsonSerialize(using = PlayerAmmoSerialization.class)
  private List<Ammo> ammoList = new ArrayList<>();
  private Integer lives = 1;
  private Direction direction;

  public BulanciPlayer(Direction direction, Integer x, Integer y, Integer width, Integer height) {
    super(x, y, width, height);
    this.direction = direction;
  }

  @Override
  public List<GameRuleEvent> applyAmmoDamage(Integer damage) {
    this.lives = this.lives - damage;
    if ( lives > 0) {
      return Collections.singletonList(new GameRuleEvent("liveDecreased", this.getClass().getSimpleName(), this));
    } else {
      return Collections.singletonList(new GameRuleEvent("death", this.getClass().getSimpleName(), this));
    }
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

  public Direction getDirection() {
    return direction;
  }

  public void setDirection(Direction direction) {
    this.direction = direction;
  }
}
