package uu.game.main.game.common;

import java.util.Set;
import uu.game.main.abl.dto.Player;

public interface GamePlayMode {

  boolean isGameFinished(Set<Player> players);
}
