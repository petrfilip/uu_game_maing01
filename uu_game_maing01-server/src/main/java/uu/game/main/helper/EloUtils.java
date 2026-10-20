package uu.game.main.helper;

import java.util.ArrayList;
import java.util.List;

public class EloUtils {

  public static final int K_FACTOR = 32;

  /** What a player actually scores in a pairing they won. */
  private static final double WIN = 1.0;
  /** What both players actually score in a drawn pairing - half a win each. */
  private static final double DRAW = 0.5;
  /** What a player actually scores in a pairing they lost. */
  private static final double LOSS = 0.0;

  private EloUtils(){}

  /**
   * Recalculates the rating of every participant of a single finished match.
   *
   * <p>Every pair of players is settled against the other, always using the ratings they held
   * <em>before</em> the match, so the result does not depend on the order of the lists. Players who
   * finished the match on the same score draw the pairing: they exchange the draw amount instead of
   * a full win. Deciding this from the match score rather than from the position in the list is the
   * whole point - the previous version treated everything earlier in the list as a winner, so two
   * players tied on the same score swapped rating purely because of how the sort happened to order
   * them.</p>
   *
   * @param ratings rating each player held before the match, must not contain nulls
   * @param matchScores score each player finished the match with, positionally aligned with
   * {@code ratings}; equal values are a draw, the higher value wins. Must not contain nulls.
   * @return new ratings, positionally aligned with {@code ratings}
   */
  public static List<Integer> calculateEloForResultList(List<Integer> ratings, List<Integer> matchScores) {
    if (ratings.size() != matchScores.size()) {
      throw new IllegalArgumentException(
        "ratings and matchScores must describe the same players, got " + ratings.size() + " and " + matchScores.size() + ".");
    }

    List<Integer> newRatings = new ArrayList<>(ratings);
    for (int i = 0; i < ratings.size(); i++) {
      for (int y = 0; y < i; y++) {
        int comparison = matchScores.get(y).compareTo(matchScores.get(i));
        double actualScore = comparison == 0 ? DRAW : (comparison > 0 ? WIN : LOSS);

        // The exchange is computed once and applied with opposite signs. Computing it separately for
        // both sides would let the two roundings disagree and a match could then create or destroy
        // rating out of nothing.
        int eloDifference = calculateRatingChange(ratings.get(y), ratings.get(i), actualScore);

        newRatings.set(y, newRatings.get(y) + eloDifference);
        newRatings.set(i, newRatings.get(i) - eloDifference);
      }
    }
    return newRatings;
  }

  /**
   * Rating a player gains from one pairing.
   *
   * @param playerElo The rating of the player the change is calculated for
   * @param opponentElo The rating of the opponent
   * @param actualScore What the player got out of the pairing: 1 for a win, 0.5 for a draw, 0 for a loss
   * @return Rating the player gains, negative when the player loses rating
   */
  public static int calculateRatingChange(int playerElo, int opponentElo, double actualScore) {
    double exponent = (double) (opponentElo - playerElo) / 400;
    double expectedOutcome = (1 / (1 + (Math.pow(10, exponent))));

    return (int) Math.round(K_FACTOR * (actualScore - expectedOutcome));
  }

  /**
   * @param winningPlayerElo The rating of Player1
   * @param losingPlayerElo The rating of Player2
   * @return Difference of elo
   */
  public static int calculate2PlayersRating(int winningPlayerElo, int losingPlayerElo) {
    return calculateRatingChange(winningPlayerElo, losingPlayerElo, WIN);
  }

}
