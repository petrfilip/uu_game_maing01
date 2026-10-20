package uu.game.main.game.common.ammo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import uu.game.main.abl.dto.Player;
import uu.game.main.game.common.GameArea;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.Obstacle;
import uu.game.main.game.common.ObstacleTypeEnum;
import uu.game.main.helper.MathUtils;

public class BulletTest {

  private static final int SPEED_PER_TICK = 15;

  private final GameArea area = new GameArea(1000, 600);
  private final Collection<Obstacle> noObstacles = Collections.emptyList();

  private Player owner;
  private GameRectangle shooter;

  @Before
  public void setUp() {
    owner = new Player("shooter-1");
    owner.setScore(0);
    shooter = new GameRectangle(100, 100, 30, 30);
  }

  private Bullet firedAt(double degrees) {
    Bullet bullet = new Bullet();
    bullet.init(owner, shooter, StubMove.aimedAt(degrees), 0);
    return bullet;
  }

  private List<GameRuleEvent> tick(Bullet bullet, Collection<? extends AmmoDamagable> targets,
                                   Collection<Obstacle> obstacles, int atTick) {
    return bullet.applyEffect(targets, obstacles, atTick, area);
  }

  @Test
  public void bulletTravelsOneTickWorthAlongItsAngle() {
    for (double degrees : new double[] {0, 90, 180, -90, 37}) {
      Bullet bullet = firedAt(degrees);
      double startX = bullet.getX();
      double startY = bullet.getY();

      tick(bullet, Collections.<AmmoDamagable>emptyList(), noObstacles, 1);

      assertEquals("dx at " + degrees, SPEED_PER_TICK * MathUtils.cos(degrees), bullet.getX() - startX, 1e-6);
      assertEquals("dy at " + degrees, SPEED_PER_TICK * MathUtils.sin(degrees), bullet.getY() - startY, 1e-6);
    }
  }

  @Test
  public void bulletIsConsumedWhenItLeavesTheArena() {
    Bullet bullet = firedAt(0);

    // It used to fly forever, and because it stayed in the board's ammo list the state pushed to
    // every client grew on every shot.
    int ticksToCrossTheArena = area.getWidth() / SPEED_PER_TICK + 2;
    for (int i = 0; i < ticksToCrossTheArena && !bullet.isUsed(); i++) {
      tick(bullet, Collections.<AmmoDamagable>emptyList(), noObstacles, i);
    }

    assertTrue("bullet still alive after crossing the whole arena", bullet.isUsed());
  }

  @Test
  public void bulletStopsOnAnObstacle() {
    Bullet bullet = firedAt(0);
    Obstacle wall = new Obstacle(ObstacleTypeEnum.WALL, bullet.getX() + 5, bullet.getY() - 20, 20, 60);

    List<GameRuleEvent> events = tick(bullet, Collections.<AmmoDamagable>emptyList(), Collections.singletonList(wall), 1);

    assertTrue(bullet.isUsed());
    assertEquals(1, events.size());
    assertEquals("used", events.get(0).getAction());
  }

  @Test
  public void bulletAwardsNoScoreWhenItHitsAnObstacle() {
    Bullet bullet = firedAt(0);
    Obstacle wall = new Obstacle(ObstacleTypeEnum.WOODEN_BOX, bullet.getX() + 5, bullet.getY() - 20, 20, 60);

    tick(bullet, Collections.<AmmoDamagable>emptyList(), Collections.singletonList(wall), 1);

    // Shooting a crate used to be worth a point per hit, which in SCORE_ACHIEVED mode was an
    // instant win.
    assertEquals(Integer.valueOf(0), owner.getScore());
  }

  @Test
  public void bulletDamagesAPlayerAndIsConsumedByTheHit() {
    Bullet bullet = firedAt(0);
    TargetBox victim = new TargetBox(bullet.getX() + 5, bullet.getY() - 5, 30, 3);

    tick(bullet, Collections.singletonList(victim), noObstacles, 1);

    assertEquals(2, victim.getLives());
    assertTrue(bullet.isUsed());
  }

  @Test
  public void bulletAwardsNoScoreForANonLethalHit() {
    Bullet bullet = firedAt(0);
    TargetBox victim = new TargetBox(bullet.getX() + 5, bullet.getY() - 5, 30, 3);

    tick(bullet, Collections.singletonList(victim), noObstacles, 1);

    assertEquals(Integer.valueOf(0), owner.getScore());
  }

  @Test
  public void bulletAwardsExactlyOneKillOnTheLethalHit() {
    Bullet bullet = firedAt(0);
    TargetBox victim = new TargetBox(bullet.getX() + 5, bullet.getY() - 5, 30, 1);

    tick(bullet, Collections.singletonList(victim), noObstacles, 1);
    assertEquals(Integer.valueOf(1), owner.getScore());

    // Re-running a spent bullet over the corpse must not add a second point.
    tick(bullet, Collections.singletonList(victim), noObstacles, 2);
    assertEquals(Integer.valueOf(1), owner.getScore());
  }

  @Test
  public void bulletPassesThroughAnAlreadyDeadPlayerWithoutScoring() {
    Bullet bullet = firedAt(0);
    TargetBox corpse = new TargetBox(bullet.getX() + 5, bullet.getY() - 5, 30, 0);
    double startX = bullet.getX();

    List<GameRuleEvent> events = tick(bullet, Collections.singletonList(corpse), noObstacles, 1);

    assertFalse("a corpse must not stop a bullet", bullet.isUsed());
    assertEquals(SPEED_PER_TICK, bullet.getX() - startX, 1e-6);
    assertEquals(0, corpse.getHits());
    assertEquals(Integer.valueOf(0), owner.getScore());
    assertTrue(events.isEmpty());
  }

  @Test
  public void bulletDoesNotAwardTheShooterAKillForHittingThemselves() {
    TargetBox self = new TargetBox(shooter.getX(), shooter.getY(), 30, 1);
    Bullet bullet = new Bullet();
    bullet.init(owner, self, StubMove.aimedAt(0), 0);

    // The muzzle always sits outside the shooter's box, so the only way to be hit by your own shot
    // is to end up where it already is. Move the shooter onto it.
    self.setX(bullet.getX() - 5);
    self.setY(bullet.getY() - 5);

    tick(bullet, Collections.singletonList(self), noObstacles, 1);

    assertEquals(0, self.getLives());
    assertEquals(Integer.valueOf(0), owner.getScore());
  }

  @Test
  public void bulletDamagesOnlyOneTargetEvenWhenSeveralOverlapIt() {
    Bullet bullet = firedAt(0);
    TargetBox first = new TargetBox(bullet.getX() + 2, bullet.getY() - 5, 20, 3);
    TargetBox second = new TargetBox(bullet.getX() + 8, bullet.getY() - 5, 20, 3);

    tick(bullet, new ArrayList<AmmoDamagable>(Arrays.asList(first, second)), noObstacles, 1);

    assertEquals(1, first.getHits() + second.getHits());
    assertTrue(bullet.isUsed());
  }
}
