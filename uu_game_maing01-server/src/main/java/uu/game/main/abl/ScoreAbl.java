package uu.game.main.abl;

import javax.inject.Inject;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import uu.app.datastore.domain.PageInfo;
import uu.app.datastore.domain.PagedResult;
import uu.app.validation.ValidationResult;
import uu.app.validation.Validator;
import uu.app.validation.utils.ValidationResultUtils;
import uu.game.main.abl.entity.Score;
import uu.game.main.api.dto.ScoreGetDtoOut;
import uu.game.main.api.dto.ScoreListDtoIn;
import uu.game.main.api.dto.ScoreListDtoOut;
import uu.game.main.api.exceptions.ScoreAblRuntimeException;
import uu.game.main.api.exceptions.ScoreAblRuntimeException.Error;
import uu.game.main.dao.ScoreDao;

@Component
public final class ScoreAbl {

  @Inject
  private Validator validator;
  @Inject
  private ScoreDao scoreDao;
  @Inject
  private ModelMapper modelMapper;

  public ScoreListDtoOut list(String awid, ScoreListDtoIn dtoIn) {
    // HDS 1
    ValidationResult validationResult = validator.validate(dtoIn);
    if (!validationResult.isValid()) {
      // A1
      throw new ScoreAblRuntimeException(Error.INVALID_DTO_IN, ValidationResultUtils.validationResultToAppErrorMap(validationResult));
    }

    PagedResult<Score> scoreList = scoreDao.list(awid, new PageInfo(dtoIn.getPageInfo().getPageIndex(), dtoIn.getPageInfo().getPageSize()));

    return modelMapper.map(scoreList, ScoreListDtoOut.class);
  }

  public ScoreGetDtoOut getByUuIdentity(String awid, String uuIdentity) {
    // HDS 1
    ValidationResult validationResult = validator.validate(uuIdentity);
    if (!validationResult.isValid()) {
      // A1
      throw new ScoreAblRuntimeException(Error.INVALID_DTO_IN, ValidationResultUtils.validationResultToAppErrorMap(validationResult));
    }

    Score score = scoreDao.getByUuIdentity(awid, uuIdentity);

    return new ScoreGetDtoOut(score);
  }

}
