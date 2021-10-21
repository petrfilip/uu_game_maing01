package uu.game.main.game.bulanci;


import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

public class Mine extends Ammo {

  private Instant activeTime;

  @Override
  void applyEffect(Collection<BulanciPlayer> values) {

    for (BulanciPlayer value : values) {

      if (this.getRectangle().intersects(value.getRectangle())) {
        if (activeTime == null) {
          activeTime = Instant.now();
        }
      }

      if (this.getRectangle().intersects(value.getRectangle()) && Duration.between(activeTime, Instant.now()).toMillis() > 3000) {
        value.setLives(0);
      }

    }

  }
}
