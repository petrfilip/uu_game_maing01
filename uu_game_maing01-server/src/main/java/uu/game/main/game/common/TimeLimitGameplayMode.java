package uu.game.main.game.common;

import java.time.ZonedDateTime;
import java.util.Date;

public class TimeLimitGameplayMode implements GamePlayMode {

  private ZonedDateTime endTime;

  public TimeLimitGameplayMode(ZonedDateTime endTime) {
    this.endTime = endTime;
  }

  @Override
  public boolean isGameFinished() {
    return ZonedDateTime.now().compareTo(endTime) > 0;
  }
}
