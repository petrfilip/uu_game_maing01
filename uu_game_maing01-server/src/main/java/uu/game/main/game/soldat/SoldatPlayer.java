package uu.game.main.game.soldat;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.Player2D;
import uu.game.main.game.common.ammo.AmmoDamagable;

public class SoldatPlayer extends GameRectangle implements AmmoDamagable, Player2D<SoldatPlayer, SoldatMove, SoldatBoard> {

  public static final Integer JUMP_DURATION = 60;
  public static final Integer JUMP_SPEED = 2;

  private Integer lives = 1;
  private Direction direction;
  private Integer speed = 1;
  private Direction jumping;
  private Integer jumpUpStarted = 0;
  private boolean sprint;


  public SoldatPlayer(Direction direction, Integer x, Integer y, Integer width, Integer height) {
    super(x, y, width, height);
    this.direction = direction;
  }

  @Override
  public List<GameRuleEvent> applyAmmoDamage(Integer damage) {
    this.lives = this.lives - damage;
    if (lives > 0) {
      return new GameRuleEvent("liveDecreased", this.getClass().getSimpleName(), this).asList();
    } else {
      return new GameRuleEvent("death", this.getClass().getSimpleName(), this).asList();
    }
  }

  @Override
  public boolean intersects(Collection<? extends GameRectangle> gameRectangles) {
    return super.intersects(gameRectangles);
  }

  @Override
  public SoldatPlayer movePlayer(SoldatMove move, SoldatBoard soldatBoard) {

    if (move.getMove() != null && !move.getMove().equals(getDirection())) {
      setDirection(move.getMove());
      //return this;
    }
    boolean isCollisionWithObstacle = checkCollisionWithObstacle(soldatBoard);

    // move to left
    if (Direction.LEFT.equals(direction) && !isCollisionWithObstacle) {
      setX(getX() - getSpeed());
    }

    // move to right
    if (Direction.RIGHT.equals(direction) && !isCollisionWithObstacle) {
      setX(getX() + getSpeed());
    }

    // start jump
    if (Direction.UP.equals(direction) && jumping == null) {
      jumpUpStarted = 0;
      jumping = Direction.UP;
      setY(getY() + JUMP_SPEED);
      return this;
    }

    // continue in jump
    if (Direction.UP.equals(jumping) && jumpUpStarted < JUMP_DURATION) {
      jumpUpStarted = jumpUpStarted + JUMP_SPEED;
      setY(getY() + JUMP_SPEED);
      return this;
    }

    // stop jump when jump duration is over or collision with obstacle detected
    if (Direction.UP.equals(jumping) && (jumpUpStarted > JUMP_DURATION || isCollisionWithObstacle)) {
      jumping = Direction.DOWN;
      jumpUpStarted = 0;
      setY(getY() - JUMP_SPEED);
      return this;

    }

    // stop falling when
    if (Direction.DOWN.equals(jumping) && isCollisionWithObstacle) {
      jumping = null;
      return this;
    }

    // start falling when no obstacle below
    GameRectangle bellowPlayer = new GameRectangle(getX(), getY() - 3, getWidth(), getHeight());
    boolean noObstacleBelowPlayer = soldatBoard.getObstacles().stream().anyMatch(obstacle -> obstacle.intersects(bellowPlayer));
    if (jumping == null && noObstacleBelowPlayer) {
      jumping = Direction.DOWN;
      return this;
    }

    return this;
  }

  private boolean checkCollisionWithObstacle(SoldatBoard soldatBoard) {
    return soldatBoard.getObstacles().stream().anyMatch(obstacle -> obstacle.intersects(this));
  }

  @Override
  public Direction getDirection() {
    return direction;
  }

  @Override
  public void setDirection(Direction direction) {
    this.direction = direction;
  }

  public Integer getLives() {
    return lives;
  }

  public void setLives(Integer lives) {
    this.lives = lives;
  }

  public Integer getSpeed() {
    return speed;
  }

  public void setSpeed(Integer speed) {
    this.speed = speed;
  }

  public Direction getJumping() {
    return jumping;
  }

  public void setJumping(Direction jumping) {
    this.jumping = jumping;
  }

  public Integer getJumpUpStarted() {
    return jumpUpStarted;
  }

  public void setJumpUpStarted(Integer jumpUpStarted) {
    this.jumpUpStarted = jumpUpStarted;
  }

  public boolean isSprint() {
    return sprint;
  }

  public void setSprint(boolean sprint) {
    this.sprint = sprint;
  }
}