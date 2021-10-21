package uu.game.main.abl;

import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.modelmapper.ModelMapper;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import uu.app.datastore.domain.PageInfo;
import uu.app.datastore.domain.PagedResult;
import uu.app.validation.ValidationResult;
import uu.app.validation.Validator;
import uu.app.validation.utils.ValidationResultUtils;
import uu.game.main.abl.dto.GameCallback;
import uu.game.main.abl.dto.Player;
import uu.game.main.abl.dto.Room;
import uu.game.main.abl.dto.RoomStateEnum;
import uu.game.main.abl.dto.event.GameEvent;
import uu.game.main.abl.dto.event.RoomEvent;
import uu.game.main.api.dto.AddPlayerMoveDtoIn;
import uu.game.main.api.dto.RoomCreateDtoIn;
import uu.game.main.api.dto.RoomJoinDtoIn;
import uu.game.main.api.dto.RoomListDtoOut;
import uu.game.main.api.dto.StartGameDtoIn;
import uu.game.main.api.exceptions.RoomAblRuntimeException;
import uu.game.main.dao.RoomDao;
import uu.game.main.domain.GameState;
import uu.game.main.domain.IRule;
import uu.game.main.helper.EventPublisherHelper;

@Component
public class RoomAbl {

  private static final Logger LOGGER = LogManager.getLogger(RoomAbl.class);

  @Inject
  private ApplicationContext applicationContext;
  @Inject
  private Validator validator;
  @Inject
  private RoomDao roomDao;
  @Inject
  private ModelMapper modelMapper;
  @Inject
  private EventPublisherHelper eventPublisher;


  public Room roomCreate(String awid, RoomCreateDtoIn dtoIn) {
    LOGGER.info("Creating new room: {}", dtoIn);

    ValidationResult validationResult = validator.validate(dtoIn);
    if (!validationResult.isValid()) {
      // A1
      throw new RoomAblRuntimeException(RoomAblRuntimeException.Error.INVALID_DTO_IN, ValidationResultUtils.validationResultToAppErrorMap(validationResult));
    }

    Room room = new Room();
    room.setAwid(awid);
    room.setRoomName(dtoIn.getRoomName());
    room.setRoomOwner(new Player(dtoIn.getRoomOwner()));
    room.setGame(createGameInstance("bulanci"));
    room.setState(RoomStateEnum.WAITING);

    room = roomDao.create(room);
    LOGGER.info("Room created: {}", room);
    return room;
  }

  public RoomListDtoOut roomList(String awid) {
    PagedResult<Room> rooms = roomDao.list(awid, new PageInfo(0, Integer.MAX_VALUE));
    return modelMapper.map(rooms, RoomListDtoOut.class);
  }


  public Room roomJoinPlayer(String awid, RoomJoinDtoIn dtoIn) {
    if (!roomDao.exists(awid, dtoIn.getRoomId())) {
      throw new IllegalArgumentException("Room does not exists!");
    }

    LOGGER.info("New player joined to room: {}", dtoIn);

    Room room = roomDao.get(awid, dtoIn.getRoomId());

    if (!room.getConnectedPlayers().contains(new Player(dtoIn.getPlayerId()))) { // prevent to add same player multiple times
      room.getConnectedPlayers().add(new Player(dtoIn.getPlayerId()));
    }

    room.getGame().addPlayer(new Player(dtoIn.getPlayerId()));

    eventPublisher.publish(new RoomEvent(this, awid, dtoIn.getRoomId(), room));

    return room;
  }

  public void addPlayerMove(String awid, AddPlayerMoveDtoIn dtoIn) {
    Room room = roomDao.get(awid, dtoIn.getRoomId());
    room.getGame().addPlayerMove(dtoIn.getPlayerId(), dtoIn.getPlayerMoves());
    eventPublisher.publish(new RoomEvent(this, awid, dtoIn.getRoomId(), room));
  }

  public GameState startGame(String awid, StartGameDtoIn dtoIn) {
    if (!roomDao.exists(awid, dtoIn.getRoomId())) {
      throw new IllegalArgumentException("Room does not exists!");
    }

    Room room = roomDao.get(awid, dtoIn.getRoomId());
    GameInstanceAbl game = room.getGame();

    if (!dtoIn.getPlayerId().equals(room.getRoomOwner().getPlayerId())) {
      throw new IllegalAccessError("Game can be started only by room owner");
    }

    LOGGER.info("Starting new game {}", dtoIn);

    room.setState(RoomStateEnum.ACTIVE);
    for (Player connectedPlayer : room.getConnectedPlayers()) {
      game.addPlayer(connectedPlayer);
    }

    GameState gameState = game.startGame(new GameCallback() {
      @Override
      public void onNextRound(GameState gameState) {
        LOGGER.info("Next round  calculated: {}", gameState);
        eventPublisher.publish(new GameEvent(this, awid, dtoIn.getRoomId(), gameState));
      }

      @Override
      public void onGameEnd(GameState gameState) {
        LOGGER.info("Game finished: {}", gameState);
        eventPublisher.publish(new GameEvent(this, awid, dtoIn.getRoomId(), gameState));
      }
    }, dtoIn.getGameParameters());
    eventPublisher.publish(new GameEvent(this, awid, dtoIn.getRoomId(), gameState));
    LOGGER.info("Game started: {}", gameState);
    return gameState;
  }

  private GameInstanceAbl createGameInstance(String gameName) {
    try {
      GameInstanceAbl gameServiceBean = applicationContext.getBean(GameInstanceAbl.class);
      gameServiceBean.setGame(applicationContext.getBean(gameName, IRule.class));
      return gameServiceBean;
    } catch (BeansException e) {
      throw new IllegalArgumentException("Game " + gameName + " not found!");
    }
  }
}
