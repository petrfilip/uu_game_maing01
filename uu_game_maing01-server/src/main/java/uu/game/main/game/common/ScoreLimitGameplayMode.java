package uu.game.main.game.common;

import java.time.ZonedDateTime;
import java.util.Set;
import uu.game.main.abl.dto.Player;

public class ScoreLimitGameplayMode implements GamePlayMode {

  private final Integer stopAtScore;

  public ScoreLimitGameplayMode(Integer stopAtScore) {
    this.stopAtScore = stopAtScore;
  }

  @Override
  public boolean isGameFinished(Set<Player> players) {
    return players.stream().anyMatch(p -> p.getScore() > stopAtScore);
  }
}
