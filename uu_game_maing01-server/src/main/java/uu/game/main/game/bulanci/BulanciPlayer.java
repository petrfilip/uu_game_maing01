package uu.game.main.game.bulanci;

import java.awt.Rectangle;
import java.util.List;
import uu.game.main.abl.dto.Player;

public class BulanciPlayer {

  private Player player;

  private Integer x;
  private Integer y;

  private Integer width;
  private Integer height;

  private Integer speed;

  private List<Ammo> ammo;

  private Integer lives = 1;

  public Rectangle getRectangle() {
    return new Rectangle(x,y,width,height);
  }

  public Player getPlayer() {
    return player;
  }

  public void setPlayer(Player player) {
    this.player = player;
  }

  public Integer getX() {
    return x;
  }

  public void setX(Integer x) {
    this.x = x;
  }

  public Integer getY() {
    return y;
  }

  public void setY(Integer y) {
    this.y = y;
  }

  public Integer getWidth() {
    return width;
  }

  public void setWidth(Integer width) {
    this.width = width;
  }

  public Integer getHeight() {
    return height;
  }

  public void setHeight(Integer height) {
    this.height = height;
  }

  public Integer getSpeed() {
    return speed;
  }

  public void setSpeed(Integer speed) {
    this.speed = speed;
  }

  public List<Ammo> getAmmo() {
    return ammo;
  }

  public void setAmmo(List<Ammo> ammo) {
    this.ammo = ammo;
  }

  public Integer getLives() {
    return lives;
  }

  public void setLives(Integer lives) {
    this.lives = lives;
  }
}
