package uu.game.main.game.common;

import java.time.ZonedDateTime;
import java.util.Set;
import uu.game.main.abl.dto.Player;

public class TimeLimitGameplayMode implements GamePlayMode {

  private final ZonedDateTime endTime;

  public TimeLimitGameplayMode(ZonedDateTime endTime) {
    this.endTime = endTime;
  }

  @Override
  public boolean isGameFinished(Set<Player> players) {
    return ZonedDateTime.now().compareTo(endTime) > 0;
  }
}
