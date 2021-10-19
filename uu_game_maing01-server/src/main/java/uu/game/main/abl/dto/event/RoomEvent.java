package uu.game.main.abl.dto.event;

import uu.game.main.abl.dto.Room;

public class RoomEvent extends GameServerEvent<Room> {


  public RoomEvent(Object source, Room output) {
    super(source, output);
  }
}
