package uu.game.main.game.bulanci;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import uu.game.main.abl.dto.Player;
import uu.game.main.game.bulanci.ammo.Ammo;
import uu.game.main.helper.PlayerSerializer;

public class BulanciBoard {

  @JsonSerialize(keyUsing = PlayerSerializer.class)
  private Map<Player, BulanciPlayer> players = new HashMap<>();
  private List<Obstacle> obstacles = new ArrayList<>();
  private List<Ammo> ammo = new ArrayList<>();


  public Map<Player, BulanciPlayer> getPlayers() {
    return players;
  }

  public void setPlayers(Map<Player, BulanciPlayer> players) {
    this.players = players;
  }


  public List<Obstacle> getObstacles() {
    return obstacles;
  }

  public void setObstacles(List<Obstacle> obstacles) {
    this.obstacles = obstacles;
  }

  public List<Ammo> getAmmo() {
    return ammo;
  }

  public void setAmmo(List<Ammo> ammo) {
    this.ammo = ammo;
  }
}
