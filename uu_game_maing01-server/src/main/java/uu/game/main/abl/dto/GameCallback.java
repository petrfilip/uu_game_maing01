package uu.game.main.abl.dto;

import uu.game.main.domain.GameState;

public interface GameCallback {

  void onGameEnd(GameState gameState);

  void onNextRound(GameState gameState);
}
