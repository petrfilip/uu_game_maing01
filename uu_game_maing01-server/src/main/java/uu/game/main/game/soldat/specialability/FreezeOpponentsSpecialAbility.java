package uu.game.main.game.soldat.specialability;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import uu.game.main.game.soldat.SoldatPlayer;

@Service()
@Scope(scopeName = SCOPE_PROTOTYPE)
public class FreezeOpponentsSpecialAbility extends SpecialAbility {

  private ZonedDateTime endTime;
  private List<SoldatPlayer> opponents;


  @Override
  public void applyAbility(SoldatPlayer soldatPlayer, List<SoldatPlayer> opponents) {
    this.opponents = opponents;
    this.endTime = ZonedDateTime.now().plusSeconds(15);

    for (SoldatPlayer opponent : this.opponents) {
      opponent.setSpeed(0);
    }

  }

  @Override
  public void applyAbilityFinished() {
    for (SoldatPlayer opponent : this.opponents) {
      opponent.setSpeed(SoldatPlayer.INIT_SPEED);
    }
  }

  @Override
  public boolean isDone() {
    return endTime != null && ZonedDateTime.now().compareTo(endTime) > 0;
  }

}
