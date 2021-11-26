package uu.game.main.game.soldat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.Player2D;
import uu.game.main.game.common.ammo.AmmoDamagable;
import uu.game.main.game.soldat.specialability.SpecialAbility;
import uu.game.main.helper.Utils;

public class SoldatPlayer extends GameRectangle implements AmmoDamagable, Player2D<SoldatPlayer, SoldatMove, SoldatBoard> {

  public static final Integer JUMP_DURATION = 600;
  public static final double JUMP_SPEED = 5;
  public static final Integer INIT_LIVES = 5;
  public static final Integer INIT_SPEED = 3;

  private Integer lives = INIT_LIVES;
  private Direction direction;
  private Integer speed = INIT_SPEED;
  private Direction jumping;
  private Instant jumpUpStarted = Instant.now();
  private boolean sprint;
  private ZonedDateTime respawnTime;

  private List<SpecialAbility> specialAbilityList = new ArrayList<>();


  public SoldatPlayer(Direction direction, Integer x, Integer y, Integer width, Integer height) {
    super(x, y, width, height);
    this.direction = direction;
  }

  public List<GameRuleEvent> applySpecialAbility(Collection<SoldatPlayer> players, SpecialAbility specialAbility) {
    List<SoldatPlayer> opponents = players.stream().filter(p -> p != this).collect(Collectors.toList());

    specialAbility.applyAbility(this, opponents);
    this.specialAbilityList.add(specialAbility);
    return new ArrayList<>();
  }

  @Override
  public List<GameRuleEvent> applyAmmoDamage(Integer damage) {
    this.lives = this.lives - damage;
    if (lives > 0) {
      return new GameRuleEvent("liveDecreased", this.getClass().getSimpleName(), this).asList();
    } else {
      if (respawnTime == null) {
        this.respawnTime = ZonedDateTime.now().plusSeconds(6);
        return new GameRuleEvent("death", this.getClass().getSimpleName(), this).asList();
      }
    }

    return new ArrayList<>();
  }

  @Override
  public boolean intersects(Collection<? extends GameRectangle> gameRectangles) {
    return super.intersects(gameRectangles);
  }

  @Override
  public SoldatPlayer movePlayer(SoldatMove move, SoldatBoard soldatBoard) {

    if (checkOutOfBound()) {
      applyAmmoDamage(9999);
    }

    for (SpecialAbility specialAbility : specialAbilityList) {
      if (specialAbility.isDone()) {
        specialAbility.applyAbilityFinishedOnce();
      }
    }

    if (respawnTime != null && respawnTime.isBefore(ZonedDateTime.now())) {
      respawnTime = null;
      lives = INIT_LIVES;
      setX(Utils.getRandomNumber(0, 500));
      setY(Utils.getRandomNumber(0, 500));
    }

    if (move.getMove() != null && !move.getMove().equals(getDirection())) {
      setDirection(move.getMove());
    }
    boolean isCollisionWithObstacle = checkCollisionWithObstacle(soldatBoard);
    GameRectangle bellowPlayer = new GameRectangle(getX(), getY() + 1, getWidth(), getHeight());
    boolean obstacleBellow = soldatBoard.getObstacles().stream().anyMatch(obstacle -> obstacle.intersects(bellowPlayer));


    // move to left
    if (move.getMove() != null && Direction.LEFT.equals(direction) && !isCollisionWithObstacle) {
      setX(getX() - getSpeed());
    }

    // move to right
    if (move.getMove() != null && Direction.RIGHT.equals(direction) && !isCollisionWithObstacle) {
      setX(getX() + getSpeed());
    }

    // start jump
    if (Direction.UP.equals(move.getMove()) && jumping == null) {
      jumpUpStarted = Instant.now();
      jumping = Direction.UP;
      setY(getY() - JUMP_SPEED);
      return this;
    }

    Duration timeElapsed = Duration.between(jumpUpStarted, Instant.now());
    // continue in jump
    if (Direction.UP.equals(jumping) && timeElapsed.toMillis() < JUMP_DURATION) {
      setY(getY() - JUMP_SPEED);
      return this;
    }

    // stop jump when jump duration is over or collision with obstacle detected
    if (Direction.UP.equals(jumping) && (timeElapsed.toMillis() >= JUMP_DURATION || isCollisionWithObstacle)) {
      jumping = Direction.DOWN;
      jumpUpStarted = Instant.now();
      setY(getY() + JUMP_SPEED);
      return this;

    }

    // stop falling when collision
    if (Direction.DOWN.equals(jumping) && isCollisionWithObstacle) {
      jumping = null;
      return this;
    }

    // start falling when no obstacle below or continue in falling down
    if ((jumping == null || Direction.DOWN.equals(jumping)) && !obstacleBellow) {
      jumping = Direction.DOWN;
      setY(getY() + JUMP_SPEED);
      return this;
    }

    // stop falling when reach the bottom
    if ((jumping == null || Direction.DOWN.equals(jumping)) && obstacleBellow) {
      jumping = null;
      GameRectangle obstacle = soldatBoard.getObstacles()
        .stream().map(o -> o.intersectsWith(bellowPlayer))
        .filter(Objects::nonNull)
        .findFirst().get();

      setY(obstacle.getY() - getHeight());
      return this;
    }

    return this;
  }

  public boolean checkOutOfBound() {
    return getX() < 0 || getX() > 1400 || getY() < 0 || getY() > 600;
  }

  private boolean checkCollisionWithObstacle(SoldatBoard soldatBoard) {
    return soldatBoard.getObstacles().stream().anyMatch(obstacle -> obstacle.intersects(new GameRectangle(this.getX(), this.getY() - 2, this.getWidth(), this.getHeight())));
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

  public Instant getJumpUpStarted() {
    return jumpUpStarted;
  }

  public void setJumpUpStarted(Instant jumpUpStarted) {
    this.jumpUpStarted = jumpUpStarted;
  }

  public boolean isSprint() {
    return sprint;
  }

  public void setSprint(boolean sprint) {
    this.sprint = sprint;
  }

  public ZonedDateTime getRespawnTime() {
    return respawnTime;
  }

  public void setRespawnTime(ZonedDateTime respawnTime) {
    this.respawnTime = respawnTime;
  }
}
