package uu.game.main.abl.dto.event;

import uu.game.main.domain.GameState;

public class GameEvent extends GameServerEvent<GameState> {


  public GameEvent(Object source, String awid, String roomId, GameState output) {
    super(source, awid, roomId, output);
  }
}
