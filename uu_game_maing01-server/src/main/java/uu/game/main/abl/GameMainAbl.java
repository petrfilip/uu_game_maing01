package uu.game.main.abl;

import javax.inject.Inject;
import org.springframework.stereotype.Component;
import uu.app.exception.AppErrorMap;
import uu.app.validation.ValidationResult;
import uu.app.validation.Validator;
import uu.app.validation.utils.ValidationResultUtils;
import uu.app.workspace.api.Profile;
import uu.game.main.dao.GameMainDao;
import uu.game.main.api.dto.GameMainInitDtoIn;
import uu.game.main.api.dto.GameMainInitDtoOut;
import uu.game.main.api.exceptions.GameMainInitRuntimeException;
import uu.game.main.api.exceptions.GameMainInitRuntimeException.Error;

@Component
public final class GameMainAbl {

  private static final String AUTHORITIES_CODE = "Authorities";

  @Inject
  private Validator validator;

  @Inject
  private Profile profile;

  @Inject
  private GameMainDao gameMainDao;

  public GameMainInitDtoOut init(String awid, GameMainInitDtoIn dtoIn) {
    // HDS 1
    ValidationResult validationResult = validator.validate(dtoIn);
    if (!validationResult.isValid()) {
      // A1
      throw new GameMainInitRuntimeException(Error.INVALID_DTO_IN, ValidationResultUtils.validationResultToAppErrorMap(validationResult));
    }

    // HDS 2
    profile.set(awid, AUTHORITIES_CODE, dtoIn.getUuAppProfileAuthorities());

    // HDS 3 - HDS N
    // TODO Implement according to application needs...

    // HDS N+1
    GameMainInitDtoOut gameMainInitDtoOut = new GameMainInitDtoOut();
    gameMainInitDtoOut.setUuAppErrorMap(new AppErrorMap());
    return gameMainInitDtoOut;
  }

}
