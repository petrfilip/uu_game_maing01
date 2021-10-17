package uu.game.main.api.exceptions;

import java.util.Map;
import uu.app.exception.AppErrorMap;
import uu.app.exception.AppRuntimeException;
import uu.app.exception.ErrorCode;

public final class GameMainInitRuntimeException extends AppRuntimeException {

  public GameMainInitRuntimeException(GameMainInitRuntimeException.Error code, Map<String, ?> paramMap, Throwable cause) {
    super(code.getCode(), code.getMessage(), (AppErrorMap) null, paramMap, cause);
  }

  public GameMainInitRuntimeException(GameMainInitRuntimeException.Error code, String message, Object... params) {
    super(code.getCode(), message, params);
  }

  public GameMainInitRuntimeException(GameMainInitRuntimeException.Error code, Map<String, ?> paramMap) {
    super(code.getCode(), code.getMessage(), (AppErrorMap) null, paramMap, null);
  }

  public enum Error {

    INVALID_DTO_IN(ErrorCode.application("uu-game-main/init/invalidDtoIn"), "DtoIn is not valid."),

    SET_PROFILE_FAILED(ErrorCode.application("uu-game-main/init/sys/setProfileFailed"), "Set profile failed.");

    private ErrorCode code;

    private String message;

    Error(ErrorCode code, String message) {
      this.code = code;
      this.message = message;
    }

    public ErrorCode getCode() {
      return code;
    }

    public String getMessage() {
      return message;
    }

  }

}
