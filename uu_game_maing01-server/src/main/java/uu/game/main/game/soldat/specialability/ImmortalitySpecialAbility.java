package uu.game.main.game.soldat.specialability;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import uu.game.main.game.soldat.SoldatPlayer;

@Service()
@Scope(scopeName = SCOPE_PROTOTYPE)
public class ImmortalitySpecialAbility extends SpecialAbility {

  private ZonedDateTime endTime;
  private SoldatPlayer soldatPlayer;
  private Integer originLive = 0;


  public void applyAbility(SoldatPlayer soldatPlayer, List<SoldatPlayer> opponents) {
    this.soldatPlayer = soldatPlayer;
    this.endTime = ZonedDateTime.now().plusSeconds(15);
    this.originLive = soldatPlayer.getLives();
    soldatPlayer.setLives(999);
  }

  public void applyAbilityFinished() {
    soldatPlayer.setLives(this.originLive);
  }

  public boolean isDone() {
    return endTime != null && ZonedDateTime.now().compareTo(endTime) > 0;
  }

}
