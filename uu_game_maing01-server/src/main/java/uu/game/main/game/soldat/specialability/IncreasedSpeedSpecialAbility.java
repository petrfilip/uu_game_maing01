package uu.game.main.game.soldat.specialability;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import uu.game.main.game.soldat.SoldatPlayer;

@Service()
@Scope(scopeName = SCOPE_PROTOTYPE)
public class IncreasedSpeedSpecialAbility implements SpecialAbility {

  private ZonedDateTime endTime;
  private SoldatPlayer soldatPlayer;


  public void applyAbility(SoldatPlayer soldatPlayer, List<SoldatPlayer> opponents) {
    this.soldatPlayer = soldatPlayer;
    this.endTime = ZonedDateTime.now().plusSeconds(30);
    soldatPlayer.setSpeed(this.soldatPlayer.getSpeed()+3);
  }

  public void applyAbilityFinished() {
    this.soldatPlayer.setSpeed(soldatPlayer.getSpeed()-3);
  }

  public boolean isDone() {
    return endTime != null && ZonedDateTime.now().compareTo(endTime) > 0;
  }

}
