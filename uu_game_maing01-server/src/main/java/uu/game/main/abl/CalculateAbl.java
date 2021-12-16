package uu.game.main.abl;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Random;
import javax.inject.Inject;
import org.springframework.stereotype.Component;
import uu.app.datastore.domain.PageInfo;
import uu.app.datastore.domain.PagedResult;
import uu.game.main.abl.entity.Calculate;
import uu.game.main.api.dto.CalculateDtoIn;
import uu.game.main.dao.CalculateDao;

@Component
public class CalculateAbl {

  private final Random random = new Random();

  @Inject
  private CalculateDao calculateDao;

  public Calculate calculate(String awid, CalculateDtoIn dtoIn) {
    Calculate calculate = new Calculate();
    calculate.setAwid(awid);

    calculate.setKey(ZonedDateTime.now());
    calculate.setValueList(generateValues());

    return calculateDao.create(calculate);
  }

  private ArrayList<Integer> generateValues() {
    ArrayList<Integer> values = new ArrayList<>();
    values.add(getRandomNumber(2, 10));
    values.add(getRandomNumber(1, 8));
    values.add(getRandomNumber(3, 9));
    return values;
  }

  private int getRandomNumber(int min, int max) {
    return random.nextInt(max - min) + min;
  }

  public PagedResult<Calculate> list(String awid, CalculateDtoIn dtoIn) {
    return calculateDao.list(awid, new PageInfo(0, 20));
  }
}
