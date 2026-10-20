package uu.game.main.game.common.ammo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import uu.game.main.abl.dto.Player;
import uu.game.main.game.common.GameArea;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.Obstacle;

public class MineTest {

  private static final int ARMING_DELAY_TICKS = 20;

  private final GameArea area = new GameArea(1000, 600);
  private final Collection<Obstacle> noObstacles = Collections.emptyList();

  private Player owner;
  private GameRectangle layer;
  private Mine mine;

  @Before
  public void setUp() {
    owner = new Player("layer-1");
    owner.setScore(0);
    layer = new GameRectangle(100, 100, 30, 30);
    mine = new Mine();
    mine.init(owner, layer, StubMove.aimedAt(0), 0);
  }

  private TargetBox victimStandingOnTheMine(int lives) {
    return new TargetBox(mine.getX(), mine.getY(), 30, lives);
  }

  @Test
  public void mineDoesNotDetonateBeforeItsArmingDelay() {
    TargetBox victim = victimStandingOnTheMine(3);

    mine.applyEffect(Collections.singletonList(victim), noObstacles, ARMING_DELAY_TICKS - 1, area);

    assertEquals(0, victim.getHits());
    assertFalse(mine.isUsed());
  }

  @Test
  public void mineDetonatesOnceTheArmingDelayHasPassed() {
    TargetBox victim = victimStandingOnTheMine(3);

    // Two regressions at once: the arming condition used to be inverted so a mine could never
    // explode, and the mine's height was left null so the collision check could not see the victim.
    mine.applyEffect(Collections.singletonList(victim), noObstacles, ARMING_DELAY_TICKS, area);

    assertEquals(1, victim.getHits());
    assertTrue(mine.isUsed());
  }

  @Test
  public void mineCreditsTheOwnerWhenItKillsSomebodyElse() {
    TargetBox victim = victimStandingOnTheMine(1);

    mine.applyEffect(Collections.singletonList(victim), noObstacles, ARMING_DELAY_TICKS, area);

    assertEquals(Integer.valueOf(1), owner.getScore());
  }

  @Test
  public void mineDoesNotCreditTheOwnerForBlowingThemselvesUp() {
    // The realistic self-kill: the mine is dropped at the owner's feet and they are still standing
    // there when it arms.
    TargetBox self = new TargetBox(layer.getX(), layer.getY(), 30, 1);
    Mine ownMine = new Mine();
    ownMine.init(owner, self, StubMove.aimedAt(0), 0);

    ownMine.applyEffect(Collections.singletonList(self), noObstacles, ARMING_DELAY_TICKS, area);

    assertEquals(0, self.getLives());
    assertEquals(Integer.valueOf(0), owner.getScore());
  }

  @Test
  public void mineIgnoresAnAlreadyDeadPlayerAndStaysArmed() {
    TargetBox corpse = victimStandingOnTheMine(0);

    mine.applyEffect(Collections.singletonList(corpse), noObstacles, ARMING_DELAY_TICKS, area);

    assertFalse("a mine must not be spent on a corpse", mine.isUsed());
    assertEquals(Integer.valueOf(0), owner.getScore());
  }
}
