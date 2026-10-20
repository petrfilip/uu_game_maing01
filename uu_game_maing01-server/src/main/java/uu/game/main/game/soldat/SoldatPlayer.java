package uu.game.main.game.soldat;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import uu.game.main.game.common.CollisionSpace;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.Player2D;
import uu.game.main.game.common.ammo.AmmoDamagable;
import uu.game.main.game.common.ammo.DamageResult;
import uu.game.main.game.soldat.specialability.BonusItem;
import uu.game.main.game.soldat.specialability.SpecialAbility;
import uu.game.main.game.common.CompactDoubleSerializer;

/**
 * A soldier: a box with velocity, run and jump input, lives, timed effects and a respawn timer.
 *
 * <h2>Why everything is counted in ticks</h2>
 * Jump duration, respawn and ability durations used to be wall-clock ({@code Instant.now()},
 * {@code ZonedDateTime.now()}). Because the rule also invoked movement five times per tick, a jump's
 * shape depended on how quickly those five calls happened to execute, and the same input produced a
 * different arc on a loaded server. Ticks are the simulation's only clock now, which makes the
 * physics reproducible and lets it be unit tested without sleeping.
 *
 * <h2>Movement model</h2>
 * Vertical motion is velocity plus gravity rather than "go up for 300 ms, then down at a constant
 * rate", so jumps arc naturally, falls accelerate, and landing is resolved against geometry instead
 * of being snapped by a special case.
 *
 * <h2>Timed effects expire themselves</h2>
 * Freeze, speed boost and invulnerability are deadlines stored on the player. Previously an ability
 * had to be switched off by a callback that only ran while its <em>owner</em> was moving, so a
 * player who died holding "freeze opponents" left everyone else frozen for good.
 */
/**
 * Nulls are left out of the payload: an absent {@code respawnAtTick} says the same thing as a null
 * one to a client that tests it, and this is serialised for every player twenty times a second.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SoldatPlayer extends GameRectangle implements AmmoDamagable, Player2D<SoldatPlayer, SoldatMove, SoldatBoard> {

  public static final int INIT_LIVES = 5;

  /** Horizontal distance per tick. At 20 ticks/s this is 260 px/s. */
  public static final int INIT_SPEED = 13;

  /**
   * Upward velocity applied at take-off, in px per tick.
   *
   * <p>Chosen against the level's grid rather than for its own sake. The arena is built from 50 px
   * crates and its terraces step by 100 px, so "one crate stack, one terrace" has to be a single
   * jump or the level cannot be climbed at all - which is exactly what happened at the old value of
   * 21. That looked right on paper: {@code v²/2g} gives 100.2 px, just over a step. But the
   * simulation integrates discretely and applies gravity before the first movement, so the take-off
   * tick rises 18.8 px instead of 21 and the real apex came out at 90 - ten pixels short of every
   * terrace in the game.
   *
   * <p>23.5 clears a 100 px step by 14 px and still falls well short of 150, so a three-crate wall
   * stays a wall. {@code SoldatMapReachabilityTest} measures the apex by running the physics and
   * pins both ends of that band.
   */
  public static final double JUMP_VELOCITY = 23.5;

  /** Downward acceleration, in px per tick squared. */
  public static final double GRAVITY = 2.2;

  /** Falling is capped so a long drop cannot outrun collision resolution. */
  public static final double MAX_FALL_SPEED = 28;

  /** 3 seconds at a 50 ms tick. */
  public static final int RESPAWN_TICKS = 60;

  public static final int SIZE = 30;

  /**
   * Minimum gap between shots, in ticks. Without it a client could attach a shot to every move it
   * sends and fire as fast as it could produce packets.
   */
  public static final int FIRE_COOLDOWN_TICKS = 4;

  /**
   * Id of the {@link uu.game.main.abl.dto.Player} at the controls.
   *
   * <p>Held so an event can name who it is about with an id instead of a copy of the whole entity.
   * Not serialised: the board publishes its players in a map keyed by exactly this id, so putting it
   * on the entity as well would be the same string twice in every tick.
   */
  @JsonIgnore
  private String playerId;

  private int lives = INIT_LIVES;
  private int maxLives = INIT_LIVES;
  private Direction direction = Direction.RIGHT;
  private int baseSpeed = INIT_SPEED;
  private int speedBonus;
  private double velocityY;
  private boolean onGround;
  /** Whether the last horizontal step ran into something. Not serialised - the client never asks. */
  @JsonIgnore
  private boolean blockedHorizontally;

  private Integer respawnAtTick;
  private Integer nextFireAllowedTick;
  private Integer speedBonusUntilTick;
  private Integer frozenUntilTick;
  private Integer invulnerableUntilTick;

  /** Intent recorded by {@link #applyInput}, consumed and cleared by {@link #step}. */
  @JsonIgnore
  private int moveDirection;
  @JsonIgnore
  private boolean jumpRequested;
  @JsonIgnore
  private int currentTick;

  public SoldatPlayer(Direction direction, double x, double y, Integer width, Integer height) {
    super(x, y, width, height);
    this.direction = direction;
  }

  @Override
  public void applyInput(SoldatMove move) {
    if (move == null || move.getMove() == null || isDead()) {
      return;
    }

    switch (move.getMove()) {
      case LEFT:
        moveDirection = -1;
        direction = Direction.LEFT;
        break;
      case RIGHT:
        moveDirection = 1;
        direction = Direction.RIGHT;
        break;
      case UP:
        jumpRequested = true;
        break;
      case DOWN:
      default:
        break;
    }
  }

  @Override
  public SoldatPlayer step(SoldatBoard board, int tick) {
    this.currentTick = tick;
    expireEffects(tick);

    if (isDead()) {
      if (respawnAtTick != null && tick >= respawnAtTick) {
        respawn(board);
      }
      clearInput();
      return this;
    }

    CollisionSpace space = board.collisionSpace(aliveOpponents(board));

    stepHorizontal(space, board);
    stepVertical(space);

    if (board.getArea().isBelow(this)) {
      // Fell into the pit.
      applyAmmoDamage(lives);
    }

    clearInput();
    return this;
  }

  private void stepHorizontal(CollisionSpace space, SoldatBoard board) {
    int speed = getSpeed();
    if (moveDirection == 0 || speed == 0) {
      blockedHorizontally = false;
      return;
    }
    double intended = moveDirection * (double) speed;
    double travelled = space.moveX(this, intended);
    double beforeClamp = getX();
    // Keep the player inside the arena horizontally; falling out of the bottom is a death, but
    // walking off the side used to be one too, which read as a random death rather than a mistake.
    setX(board.getArea().clampX(getX(), getWidth()));
    // Walked into something, or into the edge of the arena. To anything trying to get past it the
    // two are the same, so they report the same way - this is what tells the bot to jump instead of
    // leaning on a wall until the round ends.
    blockedHorizontally = Math.abs(travelled - intended) > 1e-9 || getX() != beforeClamp;
  }

  private void stepVertical(CollisionSpace space) {
    if (jumpRequested && onGround) {
      velocityY = -JUMP_VELOCITY;
      onGround = false;
    }

    velocityY = Math.min(velocityY + GRAVITY, MAX_FALL_SPEED);

    double travelled = space.moveY(this, velocityY);
    boolean blocked = Math.abs(travelled - velocityY) > 1e-9;

    if (blocked) {
      // Landed when falling, bumped our head when rising; either way vertical motion stops.
      onGround = velocityY > 0;
      velocityY = 0;
    } else {
      onGround = false;
    }
  }

  private void clearInput() {
    moveDirection = 0;
    jumpRequested = false;
  }

  private Collection<SoldatPlayer> aliveOpponents(SoldatBoard board) {
    // A plain loop rather than a stream: this runs for every player on every tick, and the stream
    // pipeline allocated more per call than the list it produced.
    Collection<SoldatPlayer> all = board.getPlayers().values();
    List<SoldatPlayer> opponents = new ArrayList<>(all.size());
    for (SoldatPlayer player : all) {
      if (player != this && !player.isDead()) {
        opponents.add(player);
      }
    }
    return opponents;
  }

  private void respawn(SoldatBoard board) {
    respawnAtTick = null;
    lives = maxLives;
    velocityY = 0;
    onGround = false;
    frozenUntilTick = null;
    GameRectangle spawn = board.findSpawnPoint(getWidth(), getHeight());
    setX(spawn.getX());
    setY(spawn.getY());
  }

  private void expireEffects(int tick) {
    if (speedBonusUntilTick != null && tick >= speedBonusUntilTick) {
      speedBonus = 0;
      speedBonusUntilTick = null;
    }
    if (frozenUntilTick != null && tick >= frozenUntilTick) {
      frozenUntilTick = null;
    }
    if (invulnerableUntilTick != null && tick >= invulnerableUntilTick) {
      invulnerableUntilTick = null;
    }
  }

  @Override
  public DamageResult applyAmmoDamage(int damage) {
    if (isDead()) {
      // Already down: no events, and crucially no kill credited, so a corpse cannot be farmed.
      return DamageResult.none();
    }
    if (isInvulnerable()) {
      // The shot is stopped but does nothing.
      return DamageResult.absorbed(event("blocked").asList());
    }

    lives = lives - damage;

    if (lives > 0) {
      return DamageResult.absorbed(event("liveDecreased").asList());
    }

    lives = 0;
    respawnAtTick = currentTick + RESPAWN_TICKS;
    velocityY = 0;
    frozenUntilTick = null;
    return DamageResult.lethal(event("death").asList());
  }

  public GameRuleEvent applySpecialAbility(Collection<SoldatPlayer> players, SpecialAbility specialAbility) {
    List<SoldatPlayer> opponents = new ArrayList<>(players.size());
    for (SoldatPlayer player : players) {
      if (player != this) {
        opponents.add(player);
      }
    }

    specialAbility.applyAbility(this, opponents, currentTick);
    // The interesting thing about a pickup is which ability it was and where it was taken, which is
    // what the client draws; the ability name is the only identifier a crate has.
    return new GameRuleEvent("gathered", BonusItem.class.getSimpleName(), specialAbility.getName(), this);
  }

  /** An event about this soldier: who it is and where it stands. */
  private GameRuleEvent event(String action) {
    return new GameRuleEvent(action, getClass().getSimpleName(), playerId, this);
  }

  /**
   * @return {@code true} when the weapon is off cooldown; records the shot as a side effect.
   */
  public boolean tryFire(int tick) {
    if (isDead() || isFrozen()) {
      return false;
    }
    if (nextFireAllowedTick != null && tick < nextFireAllowedTick) {
      return false;
    }
    nextFireAllowedTick = tick + FIRE_COOLDOWN_TICKS;
    return true;
  }

  // --- effects -------------------------------------------------------------------------------

  public void grantSpeedBonus(int bonus, int durationTicks) {
    this.speedBonus = bonus;
    this.speedBonusUntilTick = currentTick + durationTicks;
  }

  public void freeze(int durationTicks) {
    this.frozenUntilTick = currentTick + durationTicks;
  }

  public void grantInvulnerability(int durationTicks) {
    this.invulnerableUntilTick = currentTick + durationTicks;
  }

  public void heal() {
    this.lives = maxLives;
  }

  // --- state ---------------------------------------------------------------------------------

  @JsonIgnore
  public boolean isDead() {
    return lives <= 0;
  }

  public boolean isFrozen() {
    return frozenUntilTick != null;
  }

  public boolean isInvulnerable() {
    return invulnerableUntilTick != null;
  }

  /**
   * Effective speed in px per tick, zero while frozen.
   */
  public int getSpeed() {
    return isFrozen() ? 0 : Math.max(0, baseSpeed + speedBonus);
  }

  @Override
  public Direction getDirection() {
    return direction;
  }

  @Override
  public void setDirection(Direction direction) {
    this.direction = direction;
  }

  @JsonIgnore
  public String getPlayerId() {
    return playerId;
  }

  @JsonIgnore
  public void setPlayerId(String playerId) {
    this.playerId = playerId;
  }

  public int getLives() {
    return lives;
  }

  public void setLives(int lives) {
    this.lives = lives;
  }

  public int getMaxLives() {
    return maxLives;
  }

  public void setMaxLives(int maxLives) {
    this.maxLives = maxLives;
    this.lives = maxLives;
  }

  @JsonSerialize(using = CompactDoubleSerializer.class)
  public double getVelocityY() {
    return velocityY;
  }

  public boolean isBlockedHorizontally() {
    return blockedHorizontally;
  }

  public boolean isOnGround() {
    return onGround;
  }

  public Integer getRespawnAtTick() {
    return respawnAtTick;
  }

  @JsonIgnore
  public int getCurrentTick() {
    return currentTick;
  }

  @JsonIgnore
  public void setCurrentTick(int currentTick) {
    this.currentTick = currentTick;
  }
}
