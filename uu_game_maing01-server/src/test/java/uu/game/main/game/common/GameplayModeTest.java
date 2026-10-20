package uu.game.main.game.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;
import uu.game.main.abl.dto.Player;

public class GameplayModeTest {

  private static Player player(String id, int score) {
    Player player = new Player(id);
    player.setScore(score);
    return player;
  }

  private static Collection<Player> roster(int... scores) {
    Player[] players = new Player[scores.length];
    for (int i = 0; i < scores.length; i++) {
      players[i] = player("p" + i, scores[i]);
    }
    return Arrays.asList(players);
  }

  @Test
  public void scoreLimitEndsTheMatchAtTheLimitNotOnePast() {
    ScoreLimitGameplayMode mode = new ScoreLimitGameplayMode(5);

    assertFalse(mode.isGameFinished(roster(4, 3), 0));
    assertTrue(mode.isGameFinished(roster(5, 3), 0));
  }

  @Test
  public void scoreLimitLooksAtEveryPlayerNotJustTheFirst() {
    ScoreLimitGameplayMode mode = new ScoreLimitGameplayMode(3);

    assertTrue(mode.isGameFinished(roster(0, 0, 3), 0));
  }

  @Test
  public void scoreLimitProgressIsTheLeaderShareAndNeverExceedsOne() {
    ScoreLimitGameplayMode mode = new ScoreLimitGameplayMode(4);

    assertEquals(0.5, mode.getProgress(roster(2, 1), 0), 1e-9);
    assertEquals(1.0, mode.getProgress(roster(9), 0), 1e-9);
    // A zero limit would otherwise divide by zero and publish NaN to the client.
    assertEquals(1.0, new ScoreLimitGameplayMode(0).getProgress(roster(0), 0), 1e-9);
  }

  @Test
  public void timeLimitEndsOnTheLimitTickNotAfterIt() {
    TimeLimitGameplayMode mode = new TimeLimitGameplayMode(200);

    assertFalse(mode.isGameFinished(Collections.<Player>emptyList(), 199));
    assertTrue(mode.isGameFinished(Collections.<Player>emptyList(), 200));
  }

  @Test
  public void timeLimitProgressTracksTheTickAndIsClamped() {
    TimeLimitGameplayMode mode = new TimeLimitGameplayMode(100);

    assertEquals(0.25, mode.getProgress(Collections.<Player>emptyList(), 25), 1e-9);
    assertEquals(1.0, mode.getProgress(Collections.<Player>emptyList(), 400), 1e-9);
  }

  @Test
  public void unknownOrMissingModeNameFallsBackToTheSuppliedDefault() {
    // LAST_STANDING_MAN was a declared but unimplemented mode; asking for anything unknown must not
    // silently select a different game type.
    assertEquals(GameplayModeEnum.TIME_LIMIT, GameplayModeEnum.find("LAST_STANDING_MAN", GameplayModeEnum.TIME_LIMIT));
    assertEquals(GameplayModeEnum.TIME_LIMIT, GameplayModeEnum.find(null, GameplayModeEnum.TIME_LIMIT));
    assertEquals(GameplayModeEnum.SCORE_ACHIEVED, GameplayModeEnum.find("score_achieved", GameplayModeEnum.TIME_LIMIT));
  }
}
