package uu.game.main.game.bulanci;

import java.util.List;
import java.util.Map;
import uu.game.main.abl.dto.Player;

public class Board {

  private Map<Player, BulanciPlayer> player;
  private List<Wall> wall;

  private List<Ammo> ammo;


  public Map<Player, BulanciPlayer> getPlayer() {
    return player;
  }

  public void setPlayer(Map<Player, BulanciPlayer> player) {
    this.player = player;
  }

  public List<Wall> getWall() {
    return wall;
  }

  public void setWall(List<Wall> wall) {
    this.wall = wall;
  }

  public List<Ammo> getAmmo() {
    return ammo;
  }

  public void setAmmo(List<Ammo> ammo) {
    this.ammo = ammo;
  }
}
