package uu.game.main.test;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import uu.game.main.api.dto.GameMainInitDtoIn;
import uu.game.main.api.dto.GameMainInitDtoOut;

public class GameMainInitTest extends GameMainAbstractTest {

  @Before
  public void setUp() {
    initUuSubAppInstance();
    createUuAppWorkspace(AWID);
  }

  @Test
  public void hdsTest() {
    GameMainInitDtoIn dtoIn = new GameMainInitDtoIn();
    dtoIn.setUuAppProfileAuthorities(GG_ALL_URI);
    GameMainInitDtoOut result = gameMainAbl.init(AWID, dtoIn);
    Assert.assertNotNull(result.getUuAppErrorMap());
  }

}
