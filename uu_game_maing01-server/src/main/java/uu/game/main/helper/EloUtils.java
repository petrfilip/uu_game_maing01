package uu.game.main.helper;

import java.util.ArrayList;
import java.util.List;

public class EloUtils {

  public static final int K_FACTOR = 32;

  private EloUtils(){}

  public static List<Integer> calculateEloForResultList(List<Integer> eloResults) {
    List<Integer> newEloList = new ArrayList<>(eloResults);
    for (int i = 0; i < eloResults.size(); i++) {
      for (int y = 0; y < i; y++) {
        Integer winningPlayerElo = eloResults.get(y);
        Integer losingPlayerElo = eloResults.get(i);
        int eloDifference = calculate2PlayersRating(winningPlayerElo, losingPlayerElo);

        // Add elo to winning player
        newEloList.set(y, newEloList.get(y) + eloDifference);
        // Subtract elo from loosing player
        newEloList.set(i, newEloList.get(i) - eloDifference);
      }
    }
    return newEloList;
  }

  /**
   * @param winningPlayerElo The rating of Player1
   * @param losingPlayerElo The rating of Player2
   * @return Difference of elo
   */
  public static int calculate2PlayersRating(int winningPlayerElo, int losingPlayerElo) {
    double actualScore = 1.0;

    double exponent = (double) (losingPlayerElo - winningPlayerElo) / 400;
    double expectedOutcome = (1 / (1 + (Math.pow(10, exponent))));

    return (int) Math.round(winningPlayerElo + K_FACTOR * (actualScore - expectedOutcome)) - winningPlayerElo;
  }

}
