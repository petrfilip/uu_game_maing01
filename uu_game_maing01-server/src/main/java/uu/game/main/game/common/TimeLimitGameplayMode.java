package uu.game.main.game.common;

import java.util.Collection;
import uu.game.main.abl.dto.Player;

public class TimeLimitGameplayMode implements GamePlayMode {

  private final int endTick;

  public TimeLimitGameplayMode(int endTick) {
    this.endTick = endTick;
  }

  @Override
  public boolean isGameFinished(Collection<Player> players, int tick) {
    return tick >= endTick;
  }

  @Override
  public double getProgress(Collection<Player> players, int tick) {
    return endTick <= 0 ? 1 : Math.min(1, (double) tick / endTick);
  }

  public int getEndTick() {
    return endTick;
  }
}
