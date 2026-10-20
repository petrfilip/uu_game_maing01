package uu.game.main.abl.dto;

import java.util.ArrayList;
import java.util.List;
import uu.app.datastore.domain.LockableSysAttributes;
import uu.app.datastore.mongodb.domain.UuDataEntitySysAttributes;
import uu.app.objectstore.domain.UuObject;

/**
 * Persistable description of a room.
 *
 * <p>Deliberately plain data. It used to hold the live game runtime in a {@code GameInstanceAbl}
 * field, which meant an object with a running scheduler was part of the entity a DAO was supposed
 * to write - no Mongo implementation could ever have stored it, and the in-memory DAO only worked
 * because callers mutated the very instance it handed back. The runtime now lives in
 * {@link uu.game.main.engine.GameEngineRegistry}, keyed by awid and room id.
 */
public class Room implements UuObject {

  private String awid;
  private UuDataEntitySysAttributes sys;

  private String id;
  private String gameName;
  private String roomName;
  private RoomStateEnum state;
  private Player roomOwner;
  private List<Player> connectedPlayers = new ArrayList<>();

  public String getGameName() {
    return gameName;
  }

  public void setGameName(String gameName) {
    this.gameName = gameName;
  }

  public String getRoomName() {
    return roomName;
  }

  public void setRoomName(String roomName) {
    this.roomName = roomName;
  }

  public RoomStateEnum getState() {
    return state;
  }

  public void setState(RoomStateEnum state) {
    this.state = state;
  }

  public Player getRoomOwner() {
    return roomOwner;
  }

  public void setRoomOwner(Player roomOwner) {
    this.roomOwner = roomOwner;
  }

  public List<Player> getConnectedPlayers() {
    return connectedPlayers;
  }

  public void setConnectedPlayers(List<Player> connectedPlayers) {
    this.connectedPlayers = connectedPlayers;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String getAwid() {
    return awid;
  }

  @Override
  public void setAwid(String awid) {
    this.awid = awid;
  }

  @Override
  public String getAsid() {
    return null;
  }

  @Override
  public void setAsid(String asid) {
    // Rooms are workspace scoped; asid is not used.
  }

  @Override
  public LockableSysAttributes getSys() {
    if (sys == null) {
      sys = new UuDataEntitySysAttributes();
    }
    return sys;
  }
}
