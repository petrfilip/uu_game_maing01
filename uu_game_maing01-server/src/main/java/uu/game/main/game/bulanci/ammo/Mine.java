package uu.game.main.game.bulanci.ammo;


import java.util.Collection;
import uu.game.main.game.bulanci.BulanciPlayer;

public class Mine extends Ammo {

  private Integer activatedInTick;

  public Mine() {
  }


  @Override
  public void applyEffect(Collection<BulanciPlayer> values, Integer tick) {

    for (BulanciPlayer value : values) {

      if (this.getRectangle().intersects(value.getRectangle()) && this.activatedInTick == null) {
        this.activatedInTick = tick;
      }

      if (this.getRectangle().intersects(value.getRectangle()) && this.activatedInTick - tick > 30) {
        value.setLives(0);
      }

    }

  }
}
