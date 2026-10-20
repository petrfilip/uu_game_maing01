package uu.game.main.game.bulanci;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.ArrayList;
import java.util.List;
import uu.game.main.game.common.CollisionSpace;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.Player2D;
import uu.game.main.game.common.ammo.Ammo;
import uu.game.main.game.common.ammo.AmmoDamagable;
import uu.game.main.game.common.ammo.DamageResult;
import uu.game.main.game.common.ammo.PlayerAmmoSerialization;

/**
 * Top-down Bulanci player: no gravity, free movement in four directions.
 */
public class BulanciPlayer extends GameRectangle implements AmmoDamagable, Player2D<BulanciPlayer, BulanciMove, BulanciBoard> {

  public static final int SIZE = 30;

  private static final int SPRINT_KOEF = 2;

  /** Pixels per tick. */
  private int speed = 6;

  @JsonSerialize(using = PlayerAmmoSerialization.class)
  private List<Ammo> ammoList = new ArrayList<>();

  private int lives = 3;
  private Direction direction;

  /**
   * Id of the {@link uu.game.main.abl.dto.Player} at the controls, so an event can name who it is
   * about. Not serialised - the board's player map is already keyed by it.
   */
  @JsonIgnore
  private String playerId;

  @JsonIgnore
  private Direction pendingMove;
  @JsonIgnore
  private boolean sprinting;

  public BulanciPlayer(Direction direction, double x, double y, Integer width, Integer height) {
    super(x, y, width, height);
    this.direction = direction;
  }

  @Override
  public void applyInput(BulanciMove move) {
    if (move == null || move.getMove() == null || isDead()) {
      return;
    }
    pendingMove = move.getMove();
    direction = move.getMove();
    sprinting = move.isSprint();
  }

  @Override
  public BulanciPlayer step(BulanciBoard board, int tick) {
    if (pendingMove == null || isDead()) {
      sprinting = false;
      return this;
    }

    CollisionSpace space = board.collisionSpace(aliveOthers(board));
    double distance = speed * (sprinting ? SPRINT_KOEF : 1);

    // Was missing entirely ("todo check for collision with wall or another players"): players walked
    // through houses, trees and each other.
    switch (pendingMove) {
      case RIGHT:
        space.moveX(this, distance);
        break;
      case LEFT:
        space.moveX(this, -distance);
        break;
      case DOWN:
        space.moveY(this, distance);
        break;
      case UP:
        space.moveY(this, -distance);
        break;
      default:
        break;
    }

    setX(board.getArea().clampX(getX(), getWidth()));
    setY(Math.max(0, Math.min(getY(), board.getArea().getHeight() - (double) getHeight())));

    pendingMove = null;
    sprinting = false;
    return this;
  }

  private List<BulanciPlayer> aliveOthers(BulanciBoard board) {
    List<BulanciPlayer> others = new ArrayList<>();
    for (BulanciPlayer candidate : board.getPlayers().values()) {
      if (candidate != this && !candidate.isDead()) {
        others.add(candidate);
      }
    }
    return others;
  }

  @Override
  public DamageResult applyAmmoDamage(int damage) {
    if (isDead()) {
      return DamageResult.none();
    }

    lives = lives - damage;
    if (lives > 0) {
      return DamageResult.absorbed(event("liveDecreased").asList());
    }

    lives = 0;
    return DamageResult.lethal(event("death").asList());
  }

  /** An event about this player: who it is and where it stands. */
  private GameRuleEvent event(String action) {
    return new GameRuleEvent(action, getClass().getSimpleName(), playerId, this);
  }

  @JsonIgnore
  public String getPlayerId() {
    return playerId;
  }

  @JsonIgnore
  public void setPlayerId(String playerId) {
    this.playerId = playerId;
  }

  @JsonIgnore
  public boolean isDead() {
    return lives <= 0;
  }

  public int getSpeed() {
    return speed;
  }

  public void setSpeed(int speed) {
    this.speed = speed;
  }

  public List<Ammo> getAmmoList() {
    return ammoList;
  }

  public void setAmmoList(List<Ammo> ammoList) {
    this.ammoList = ammoList;
  }

  public int getLives() {
    return lives;
  }

  public void setLives(int lives) {
    this.lives = lives;
  }

  @Override
  public Direction getDirection() {
    return direction;
  }

  @Override
  public void setDirection(Direction direction) {
    this.direction = direction;
  }
}
