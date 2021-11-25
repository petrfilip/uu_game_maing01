package uu.game.main.game.soldat.specialability;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import java.util.List;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import uu.game.main.game.soldat.SoldatPlayer;

@Service()
@Scope(scopeName = SCOPE_PROTOTYPE)
public class IncreaseLiveSpecialAbility implements SpecialAbility {

  public void applyAbility(SoldatPlayer soldatPlayer, List<SoldatPlayer> opponents) {
    soldatPlayer.setLives(SoldatPlayer.INIT_LIVES);
  }

  @Override
  public void applyAbilityFinished() {

  }

  public boolean isDone() {
    return true;
  }


}
