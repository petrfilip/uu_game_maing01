package uu.game.main.game.soldat.specialability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.inject.Inject;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import uu.game.main.helper.Utils;

@Service
public class BonusItemService {

  @Inject
  private ApplicationContext applicationContext;

  private final Random random = new Random();

  public BonusItem generate() {

    Map<String, SpecialAbility> beansOfType = applicationContext.getBeansOfType(SpecialAbility.class);

    List<SpecialAbility> valuesList = new ArrayList<>(beansOfType.values());
    int randomIndex = random.nextInt(valuesList.size());
    SpecialAbility randomValue = valuesList.get(randomIndex);

    return new BonusItem(Utils.getRandomNumber(100, 700), 0, 30, 30, randomValue);

  }

}
