package uu.game.main.api.exceptions;

import java.util.Map;
import uu.app.exception.AppErrorMap;
import uu.app.exception.AppRuntimeException;
import uu.app.exception.ErrorCode;

public final class RoomAblRuntimeException extends AppRuntimeException {

  public RoomAblRuntimeException(RoomAblRuntimeException.Error code, Map<String, ?> paramMap, Throwable cause) {
    super(code.getCode(), code.getMessage(), (AppErrorMap) null, paramMap, cause);
  }

  public RoomAblRuntimeException(RoomAblRuntimeException.Error code, String message, Object... params) {
    super(code.getCode(), message, params);
  }

  public RoomAblRuntimeException(RoomAblRuntimeException.Error code, Map<String, ?> paramMap) {
    super(code.getCode(), code.getMessage(), (AppErrorMap) null, paramMap, null);
  }

  public enum Error {

    INVALID_DTO_IN(ErrorCode.application("uu-game-main/room/invalidDtoIn"), "DtoIn is not valid."),
    ROOM_DOES_NOT_EXIST(ErrorCode.application("uu-game-main/room/roomDoesNotExist"), "Room does not exist."),
    NOT_ROOM_OWNER(ErrorCode.application("uu-game-main/room/notRoomOwner"), "Only the room owner may do this."),
    NOT_ROOM_MEMBER(ErrorCode.application("uu-game-main/room/notRoomMember"), "Only players of the room may watch it.");


    private final ErrorCode code;

    private final String message;

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
