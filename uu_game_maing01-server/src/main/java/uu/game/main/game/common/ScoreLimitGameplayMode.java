package uu.game.main.game.common;

import java.util.Collection;
import uu.game.main.abl.dto.Player;

public class ScoreLimitGameplayMode implements GamePlayMode {

  private final int stopAtScore;

  public ScoreLimitGameplayMode(int stopAtScore) {
    this.stopAtScore = stopAtScore;
  }

  @Override
  public boolean isGameFinished(Collection<Player> players, int tick) {
    // ">=" not ">": "stop at 5" should end on the fifth kill, not the sixth.
    return players.stream().anyMatch(player -> player.getScore() >= stopAtScore);
  }

  @Override
  public double getProgress(Collection<Player> players, int tick) {
    if (stopAtScore <= 0) {
      return 1;
    }
    int best = players.stream().mapToInt(Player::getScore).max().orElse(0);
    return Math.min(1, (double) best / stopAtScore);
  }

  public int getStopAtScore() {
    return stopAtScore;
  }
}
