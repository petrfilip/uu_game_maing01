package uu.game.main.game.common;

import java.util.Collection;
import uu.game.main.abl.dto.Player;

/**
 * Decides when a match is over. Tick based rather than wall-clock so a match is reproducible and
 * can be tested without waiting.
 */
public interface GamePlayMode {

  boolean isGameFinished(Collection<Player> players, int tick);

  /**
   * Progress towards the end of the match, 0..1. Published so the client can draw a timer or a
   * score bar without duplicating the rule.
   */
  double getProgress(Collection<Player> players, int tick);
}
