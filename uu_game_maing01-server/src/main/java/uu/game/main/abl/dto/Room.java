package uu.game.main.abl.dto;

import java.util.ArrayList;
import java.util.List;
import uu.app.datastore.domain.LockableSysAttributes;
import uu.app.datastore.mongodb.domain.UuDataEntitySysAttributes;
import uu.app.objectstore.domain.UuObject;
import uu.game.main.abl.GameInstance;

public class Room implements UuObject {
  
  private String awid;
  private UuDataEntitySysAttributes sys;


  private String id;
  private GameInstance game;
  private String roomName;
  private RoomStateEnum state;
  private Player roomOwner;
  private List<Player> connectedPlayers = new ArrayList<>();

  public GameInstance getGame() {
    return game;
  }

  public void setGame(GameInstance game) {
    this.game = game;
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

  }

  @Override
  public LockableSysAttributes getSys() {
    if (sys == null) {
      sys = new UuDataEntitySysAttributes();
    }
    return sys;
  }
}
