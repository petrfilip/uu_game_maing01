package uu.game.main.game.bulanci;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import uu.game.main.game.common.Player2D;
import uu.game.main.game.common.ammo.Ammo;
import uu.game.main.game.common.ammo.AmmoDamagable;
import uu.game.main.game.bulanci.helper.PlayerAmmoSerialization;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;

public class BulanciPlayer extends GameRectangle implements AmmoDamagable, Player2D<BulanciPlayer, BulanciMove, BulanciBoard> {

  private static final Integer SPRINT_KOEF = 1;

  private Integer speed = 3;
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
      return (new GameRuleEvent("liveDecreased", this.getClass().getSimpleName(), this)).asList();
    } else {
      return (new GameRuleEvent("death", this.getClass().getSimpleName(), this)).asList();
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

  @Override
  public BulanciPlayer movePlayer(BulanciMove move, BulanciBoard bulanciBoard) {
    //todo check for collision with wall or another players
    //todo add sprint

    if (move.getMove() == null) {
      return this;
    }

    if (!move.getMove().equals(getDirection())) {
      setDirection(move.getMove());
      return this;
    }

    switch (move.getMove()) {
      case RIGHT:
        setX(getX() + (getSpeed() * SPRINT_KOEF));
        break;
      case LEFT:
        setX(getX() - (getSpeed() * SPRINT_KOEF));
        break;
      case UP:
        setY(getY() + (getSpeed() * SPRINT_KOEF));
        break;
      case DOWN:
        setY(getY() - (getSpeed() * SPRINT_KOEF));
        break;
    }

    return this;
  }
}
