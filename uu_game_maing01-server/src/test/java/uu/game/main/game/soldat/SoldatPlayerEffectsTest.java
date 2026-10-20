package uu.game.main.game.soldat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import uu.game.main.game.common.Direction;
import uu.game.main.game.soldat.specialability.FreezeOpponentsSpecialAbility;
import uu.game.main.game.soldat.specialability.ImmortalitySpecialAbility;
import uu.game.main.game.soldat.specialability.IncreasedSpeedSpecialAbility;

/**
 * Timed effects are deadlines on the player and must lift on their own. They used to be switched
 * off by a callback that only ran while the player who applied them was still moving.
 */
public class SoldatPlayerEffectsTest {

  private static final double EPSILON = 1e-9;

  private static SoldatMove move(Direction direction) {
    SoldatMove move = new SoldatMove();
    move.setMove(direction);
    return move;
  }

  private SoldatArena flatArena() {
    return SoldatArena.of(1000, 600).withPlatform(0, 400, 900, 20);
  }

  @Test
  public void freezingZeroesEffectiveSpeedAndStopsMovement() {
    SoldatArena arena = flatArena();
    SoldatPlayer soldat = arena.addPlayer(100, 370 - SoldatPlayer.SIZE);
    int tick = arena.settle(soldat, 1);
    double startX = soldat.getX();

    soldat.setCurrentTick(tick);
    soldat.freeze(40);

    assertEquals(0, soldat.getSpeed());
    soldat.applyInput(move(Direction.RIGHT));
    arena.tick(tick);
    assertEquals(startX, soldat.getX(), EPSILON);
  }

  @Test
  public void freezeLiftsItselfEvenWhenThePlayerWhoAppliedItIsDead() {
    SoldatArena arena = flatArena();
    SoldatPlayer attacker = arena.addPlayer("attacker", 100, 370 - SoldatPlayer.SIZE);
    SoldatPlayer victim = arena.addPlayer("victim", 600, 370 - SoldatPlayer.SIZE);
    attacker.setCurrentTick(0);
    victim.setCurrentTick(0);

    attacker.applySpecialAbility(new ArrayList<>(Arrays.asList(attacker, victim)), new FreezeOpponentsSpecialAbility());
    assertTrue("victim should be frozen to begin with", victim.isFrozen());

    // Everybody used to stay frozen forever once the freezer died.
    attacker.applyAmmoDamage(attacker.getLives());
    assertTrue(attacker.isDead());

    for (int tick = 1; tick <= 200 && victim.isFrozen(); tick++) {
      victim.setCurrentTick(tick);
      victim.step(arena.board(), tick);
    }

    assertFalse("freeze must expire on its own", victim.isFrozen());
    assertEquals(SoldatPlayer.INIT_SPEED, victim.getSpeed());
  }

  @Test
  public void aSpeedBonusLapsesBackToTheBaseSpeed() {
    SoldatArena arena = flatArena();
    SoldatPlayer soldat = arena.addPlayer(100, 370 - SoldatPlayer.SIZE);
    soldat.setCurrentTick(0);

    soldat.applySpecialAbility(new ArrayList<>(Arrays.asList(soldat)), new IncreasedSpeedSpecialAbility());
    int boosted = soldat.getSpeed();
    assertTrue("the bonus must actually raise the speed", boosted > SoldatPlayer.INIT_SPEED);

    for (int tick = 1; tick <= 400 && soldat.getSpeed() != SoldatPlayer.INIT_SPEED; tick++) {
      soldat.setCurrentTick(tick);
      soldat.step(arena.board(), tick);
    }

    assertEquals(SoldatPlayer.INIT_SPEED, soldat.getSpeed());
  }

  @Test
  public void invulnerabilityAbsorbsAHitWithoutCostingALife() {
    SoldatArena arena = flatArena();
    SoldatPlayer soldat = arena.addPlayer(100, 370 - SoldatPlayer.SIZE);
    soldat.setCurrentTick(0);
    soldat.applySpecialAbility(new ArrayList<>(Arrays.asList(soldat)), new ImmortalitySpecialAbility());
    int livesBefore = soldat.getLives();

    assertTrue("the hit is still consumed by the target", soldat.applyAmmoDamage(1).isAbsorbed());

    assertEquals(livesBefore, soldat.getLives());
    assertFalse("an absorbed hit is never lethal", soldat.applyAmmoDamage(99).isLethal());
    assertEquals(livesBefore, soldat.getLives());
  }

  @Test
  public void invulnerabilityLapsesAndTheNextHitCounts() {
    SoldatArena arena = flatArena();
    SoldatPlayer soldat = arena.addPlayer(100, 370 - SoldatPlayer.SIZE);
    soldat.setCurrentTick(0);
    soldat.applySpecialAbility(new ArrayList<>(Arrays.asList(soldat)), new ImmortalitySpecialAbility());

    for (int tick = 1; tick <= 400 && soldat.isInvulnerable(); tick++) {
      soldat.setCurrentTick(tick);
      soldat.step(arena.board(), tick);
    }

    assertFalse(soldat.isInvulnerable());
    int livesBefore = soldat.getLives();
    soldat.applyAmmoDamage(1);
    assertEquals(livesBefore - 1, soldat.getLives());
  }

  @Test
  public void aKilledPlayerRespawnsAtTheExpectedTickWithFullLives() {
    SoldatArena arena = SoldatArena.of(1000, 600)
      .withPlatform(0, 400, 900, 20)
      .withPlatform(100, 200, 300, 20);
    SoldatPlayer soldat = arena.addPlayer(100, 100);
    soldat.setMaxLives(3);

    int deathTick = 10;
    soldat.setCurrentTick(deathTick);
    soldat.applyAmmoDamage(soldat.getLives());
    assertEquals(Integer.valueOf(deathTick + SoldatPlayer.RESPAWN_TICKS), soldat.getRespawnAtTick());

    for (int tick = deathTick + 1; tick < deathTick + SoldatPlayer.RESPAWN_TICKS; tick++) {
      soldat.setCurrentTick(tick);
      soldat.step(arena.board(), tick);
    }
    assertTrue("must still be down one tick before the respawn tick", soldat.isDead());

    soldat.setCurrentTick(deathTick + SoldatPlayer.RESPAWN_TICKS);
    soldat.step(arena.board(), deathTick + SoldatPlayer.RESPAWN_TICKS);

    assertFalse(soldat.isDead());
    assertEquals(3, soldat.getLives());
  }

  @Test
  public void respawningNeverPlacesThePlayerInsideAnObstacle() {
    // Respawn used to be a raw random(0,500) in both axes, which regularly dropped a player inside
    // a crate. The spawn point is randomised, so repeat it.
    for (int attempt = 0; attempt < 50; attempt++) {
      SoldatArena arena = SoldatArena.of(1000, 600)
        .withPlatform(0, 400, 900, 20)
        .withPlatform(100, 200, 300, 20)
        .withPlatform(600, 300, 200, 20);
      SoldatPlayer soldat = arena.addPlayer(100, 100);
      soldat.setCurrentTick(0);
      soldat.applyAmmoDamage(soldat.getLives());
      soldat.setCurrentTick(SoldatPlayer.RESPAWN_TICKS);
      soldat.step(arena.board(), SoldatPlayer.RESPAWN_TICKS);

      assertFalse("respawned inside the level geometry", arena.board().terrain().collides(soldat));
      assertFalse("respawned outside the arena", arena.board().getArea().isOutOfBounds(soldat));
    }
  }

  @Test
  public void aKilledPlayerDoesNotComeBackFrozen() {
    SoldatArena arena = flatArena();
    SoldatPlayer soldat = arena.addPlayer(100, 100);
    soldat.setCurrentTick(0);
    soldat.freeze(10_000);
    soldat.applyAmmoDamage(soldat.getLives());

    soldat.setCurrentTick(SoldatPlayer.RESPAWN_TICKS);
    soldat.step(arena.board(), SoldatPlayer.RESPAWN_TICKS);

    assertFalse(soldat.isDead());
    assertFalse(soldat.isFrozen());
    assertEquals(SoldatPlayer.INIT_SPEED, soldat.getSpeed());
  }

  @Test
  public void aPlayerCannotFireOnConsecutiveTicksWithinTheCooldown() {
    SoldatPlayer soldat = SoldatArena.of(1000, 600).addPlayer(100, 100);

    assertTrue("first shot must be allowed", soldat.tryFire(0));
    for (int tick = 1; tick < SoldatPlayer.FIRE_COOLDOWN_TICKS; tick++) {
      assertFalse("fired again at tick " + tick, soldat.tryFire(tick));
    }
    assertTrue("cooldown must expire", soldat.tryFire(SoldatPlayer.FIRE_COOLDOWN_TICKS));
  }

  @Test
  public void aFrozenPlayerCannotFire() {
    SoldatPlayer frozen = flatArena().addPlayer(100, 100);
    frozen.setCurrentTick(0);
    frozen.freeze(40);

    assertFalse(frozen.tryFire(0));
  }

  @Test
  public void aDeadPlayerCannotFire() {
    SoldatPlayer corpse = flatArena().addPlayer(100, 100);
    corpse.setCurrentTick(0);
    corpse.applyAmmoDamage(corpse.getLives());

    assertFalse(corpse.tryFire(0));
  }

  @Test
  public void hittingAnAlreadyDeadPlayerDoesNothingAtAll() {
    SoldatPlayer corpse = SoldatArena.of(1000, 600).addPlayer(100, 100);
    corpse.setCurrentTick(0);
    corpse.applyAmmoDamage(corpse.getLives());
    Integer respawnAt = corpse.getRespawnAtTick();
    assertNotNull(respawnAt);

    corpse.setCurrentTick(30);
    assertFalse("a corpse must not absorb the shot", corpse.applyAmmoDamage(1).isAbsorbed());
    assertFalse("and must not be killed a second time", corpse.applyAmmoDamage(1).isLethal());

    // Otherwise every extra hit would push the respawn further away.
    assertEquals(respawnAt, corpse.getRespawnAtTick());
  }
}
