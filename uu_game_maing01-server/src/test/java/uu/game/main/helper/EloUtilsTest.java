package uu.game.main.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class EloUtilsTest {

  private static int sum(List<Integer> ratings) {
    int total = 0;
    for (Integer rating : ratings) {
      total += rating;
    }
    return total;
  }

  @Test
  public void theExchangeIsZeroSumAcrossTheWholeMatch() {
    List<Integer> before = Arrays.asList(1200, 1000, 800, 1550);
    // Third and fourth place finished level, so the match contains a draw as well as decided pairings.
    List<Integer> matchScores = Arrays.asList(9, 4, 4, 11);

    List<Integer> after = EloUtils.calculateEloForResultList(before, matchScores);

    assertEquals(sum(before), sum(after));
  }

  @Test
  public void beatingAHigherRatedOpponentGainsMoreThanBeatingALowerRatedOne() {
    int againstStronger = EloUtils.calculate2PlayersRating(1000, 1400);
    int againstWeaker = EloUtils.calculate2PlayersRating(1000, 600);

    assertTrue(againstStronger + " should beat " + againstWeaker, againstStronger > againstWeaker);
    assertTrue("a win must never cost the winner rating", againstWeaker > 0);
    assertTrue("a single win must not exceed the K factor", againstStronger < EloUtils.K_FACTOR);
  }

  @Test
  public void evenlyMatchedPlayersExchangeHalfTheKFactor() {
    assertEquals(EloUtils.K_FACTOR / 2, EloUtils.calculate2PlayersRating(1500, 1500));
  }

  @Test
  public void theMatchScoreDecidesWhoGainsNotThePositionInTheList() {
    // The second entry won the match, so it must gain even though it is listed last.
    List<Integer> result = EloUtils.calculateEloForResultList(Arrays.asList(1000, 1000), Arrays.asList(3, 5));

    assertTrue("the player who scored more must gain", result.get(1) > 1000);
    assertTrue("the player who scored less must lose", result.get(0) < 1000);
  }

  @Test
  public void equallyRatedPlayersWhoTieExchangeNothing() {
    List<Integer> result = EloUtils.calculateEloForResultList(Arrays.asList(1000, 1000), Arrays.asList(5, 5));

    assertEquals(Arrays.asList(1000, 1000), result);
  }

  @Test
  public void aDrawMovesLessRatingThanAWinBetweenUnequallyRatedPlayers() {
    List<Integer> drawn = EloUtils.calculateEloForResultList(Arrays.asList(1000, 1400), Arrays.asList(5, 5));
    List<Integer> underdogWon = EloUtils.calculateEloForResultList(Arrays.asList(1000, 1400), Arrays.asList(7, 5));

    int drawGain = drawn.get(0) - 1000;
    int winGain = underdogWon.get(0) - 1000;

    assertTrue("holding a stronger player to a draw must still pay, was " + drawGain, drawGain > 0);
    assertTrue("a draw must be worth less than a win, was " + drawGain + " vs " + winGain, drawGain < winGain);
    assertEquals("the stronger player must pay exactly what the weaker one gains", -drawGain, drawn.get(1) - 1400);
  }

  @Test
  public void aTieInTheMiddleOfTheStandingsStillCountsAgainstTheRestOfTheField() {
    // One winner, two players tied behind them: the tied pair must not move rating between themselves
    // but both still lost to the winner.
    List<Integer> result = EloUtils.calculateEloForResultList(
      Arrays.asList(1000, 1000, 1000), Arrays.asList(10, 5, 5));

    assertEquals("tied players must end up on the same rating", result.get(1), result.get(2));
    assertTrue("the winner must gain from both opponents", result.get(0) >= 1000 + 2 * (EloUtils.K_FACTOR / 2));
    assertTrue("the tied players must both lose to the winner", result.get(1) < 1000);
  }

  @Test
  public void anUnderdogWinShiftsMoreRatingThanTheFavouriteWinningTheSameMatch() {
    List<Integer> underdogWon = EloUtils.calculateEloForResultList(Arrays.asList(800, 1600), Arrays.asList(7, 5));
    List<Integer> favouriteWon = EloUtils.calculateEloForResultList(Arrays.asList(800, 1600), Arrays.asList(5, 7));

    assertTrue(underdogWon.get(0) - 800 > favouriteWon.get(1) - 1600);
  }

  @Test
  public void everyPairInTheStandingsIsSettledNotJustAdjacentOnes() {
    // With three players the leader must gain from both opponents, so their gain has to be larger
    // than in a straight head-to-head against the runner up alone.
    int headToHead = EloUtils.calculateEloForResultList(
      Arrays.asList(1000, 1000), Arrays.asList(10, 5)).get(0) - 1000;
    int threeWay = EloUtils.calculateEloForResultList(
      Arrays.asList(1000, 1000, 1000), Arrays.asList(10, 5, 1)).get(0) - 1000;

    assertEquals(2 * headToHead, threeWay);
  }

  @Test
  public void theRatingUsedForTheExpectationIsTheOneHeldBeforeTheMatch() {
    // Every pairing is judged against the pre-match ratings, so shuffling the players through the
    // lists cannot change what the match is worth.
    List<Integer> asPlayed = EloUtils.calculateEloForResultList(
      Arrays.asList(1000, 1400, 800), Arrays.asList(10, 5, 1));
    List<Integer> reordered = EloUtils.calculateEloForResultList(
      Arrays.asList(800, 1400, 1000), Arrays.asList(1, 5, 10));

    assertEquals(asPlayed.get(0), reordered.get(2));
    assertEquals(asPlayed.get(1), reordered.get(1));
    assertEquals(asPlayed.get(2), reordered.get(0));
  }
}
