package uu.game.main.abl.dto.event;

import uu.game.main.abl.dto.Room;

public class RoomEvent extends GameServerEvent<Room> {


  public RoomEvent(Object source, String awid, String roomId, Room output) {
    super(source, awid, roomId, output);
  }
}
