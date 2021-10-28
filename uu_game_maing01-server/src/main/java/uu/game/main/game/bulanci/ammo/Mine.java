package uu.game.main.game.bulanci.ammo;


import java.util.Collection;
import uu.game.main.game.bulanci.BulanciMove;
import uu.game.main.game.bulanci.BulanciPlayer;
import uu.game.main.game.common.GameRectangle;

public class Mine extends Ammo {

  private Integer activatedInTick;

  public Mine() {
  }


  @Override
  public void applyEffect(Collection<? extends AmmoDamagable> values, Integer tick) {

    for (AmmoDamagable value : values) {
      if (!(value instanceof GameRectangle)) {
        continue;
      }


      GameRectangle damagableRect = (GameRectangle) value;

      if (this.getRectangle().intersects(damagableRect.getRectangle()) && this.activatedInTick == null) {
        this.activatedInTick = tick;
      }

      if (this.getRectangle().intersects(damagableRect.getRectangle()) && this.activatedInTick - tick > 30) {
        value.applyAmmoDamage(10);
      }
    }
  }

  @Override
  public void init(BulanciPlayer bulanciPlayer, BulanciMove bulanciMove) {
    this.setX(bulanciPlayer.getX());
    this.setY(bulanciPlayer.getY());
    this.setWidth(40);
    this.setWidth(40);
  }
}
