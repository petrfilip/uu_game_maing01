package uu.game.main.abl;

import javax.inject.Inject;
import org.modelmapper.ModelMapper;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import uu.app.datastore.domain.PageInfo;
import uu.app.datastore.domain.PagedResult;
import uu.app.validation.ValidationResult;
import uu.app.validation.Validator;
import uu.app.validation.utils.ValidationResultUtils;
import uu.game.main.abl.dto.Player;
import uu.game.main.abl.dto.Room;
import uu.game.main.abl.dto.RoomStateEnum;
import uu.game.main.api.dto.RoomCreateDtoIn;
import uu.game.main.api.dto.RoomJoinDtoIn;
import uu.game.main.api.dto.RoomListDtoOut;
import uu.game.main.api.exceptions.RoomAblRuntimeException;
import uu.game.main.dao.RoomDao;
import uu.game.main.domain.IRule;

@Component
public class RoomAbl {

  @Inject
  private ApplicationContext applicationContext;
  @Inject
  private Validator validator;
  @Inject
  private RoomDao roomDao;
  @Inject
  private ModelMapper modelMapper;


  public Room roomCreate(String awid, RoomCreateDtoIn dtoIn) {

    ValidationResult validationResult = validator.validate(dtoIn);
    if (!validationResult.isValid()) {
      // A1
      throw new RoomAblRuntimeException(RoomAblRuntimeException.Error.INVALID_DTO_IN, ValidationResultUtils.validationResultToAppErrorMap(validationResult));
    }

    Room room = new Room();
    room.setAwid(awid);
    room.setRoomName(dtoIn.getRoomName());
    room.setRoomOwner(new Player(dtoIn.getRoomOwner()));
    room.setGame(createGameInstance("draw"));
    room.setState(RoomStateEnum.WAITING);

    return roomDao.create(room);
  }

  public RoomListDtoOut roomList(String awid) {
    PagedResult<Room> rooms = roomDao.list(awid, new PageInfo(0, Integer.MAX_VALUE));
    return modelMapper.map(rooms, RoomListDtoOut.class);
  }


  private GameInstance createGameInstance(String gameName) {
    try {
      GameInstance gameServiceBean = applicationContext.getBean(GameInstance.class);
      gameServiceBean.setGame(applicationContext.getBean(gameName, IRule.class));
      return gameServiceBean;
    } catch (BeansException e) {
      throw new IllegalArgumentException("Game " + gameName + " not found!");
    }
  }

  public Room roomJoinPlayer(String awid, RoomJoinDtoIn dtoIn) {
    if (!roomDao.exists(awid, dtoIn.getRoomId())) {
      throw new IllegalArgumentException("Room does not exists!");
    }

    Room room = roomDao.get(awid, dtoIn.getRoomId());

    if (!room.getConnectedPlayers().contains(new Player(dtoIn.getPlayerId()))) { // prevent to add same player multiple times
      room.getConnectedPlayers().add(new Player(dtoIn.getPlayerId()));
    }

    room.getGame().addPlayer(new Player(dtoIn.getPlayerId()));

    return room;
  }
}
