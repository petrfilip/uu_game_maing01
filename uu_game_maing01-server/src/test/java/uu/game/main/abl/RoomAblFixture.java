package uu.game.main.abl;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import uu.game.main.abl.dto.Player;
import uu.game.main.abl.dto.Room;
import uu.game.main.api.dto.RoomCreateDtoIn;
import uu.game.main.api.dto.RoomJoinDtoIn;
import uu.game.main.api.dto.StartGameDtoIn;
import uu.game.main.dao.memory.RoomMemoryDao;
import uu.game.main.domain.GameState;
import uu.game.main.engine.FakeRule;
import uu.game.main.engine.GameEngineRegistry;
import uu.game.main.helper.EventPublisherHelper;

/**
 * A {@link RoomAbl} wired up the way Spring would, but over things a test can drive: the real
 * in-memory DAO, a registry whose scheduler records instead of running, and a rule that is not a
 * game.
 *
 * <p>Shared by the room and broadcast tests, which both need a room somebody is actually a member
 * of before they can assert anything about access or about disconnects.
 */
public class RoomAblFixture {

  public static final String AWID = "awid-1";
  public static final String GAME = "fake";

  private final RoomMemoryDao roomDao = new RoomMemoryDao();
  private final RecordingScheduler scheduler = new RecordingScheduler();
  private final CountingObjectMapper objectMapper = new CountingObjectMapper();
  private final List<Object> publishedEvents = new CopyOnWriteArrayList<>();

  private final GameEngineRegistry registry;
  private final RoomAbl roomAbl;

  public RoomAblFixture(long idleRoomMillis, int moveSubmissionsPerSecond, long disconnectGraceMillis) {
    try {
      registry = new GameEngineRegistry();
      set(registry, "applicationContext", ruleOnlyContext());
      set(registry, "objectMapper", objectMapper);
      set(registry, "tickMillis", 50L);
      set(registry, "schedulerThreads", 1);
      set(registry, "idleRoomMillis", idleRoomMillis);
      set(registry, "reclaimIntervalMillis", 60_000L);
      // Pre-set so the registry never builds a real pool; every scheduled task is ours to run.
      set(registry, "scheduler", scheduler);

      roomAbl = new RoomAbl();
      set(roomAbl, "roomDao", roomDao);
      set(roomAbl, "gameEngineRegistry", registry);
      set(roomAbl, "eventPublisher", recordingPublisher());
      set(roomAbl, "defaultGame", GAME);
      set(roomAbl, "moveSubmissionsPerSecond", moveSubmissionsPerSecond);
      set(roomAbl, "disconnectGraceMillis", disconnectGraceMillis);
      roomAbl.init();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** Creates a room without going through validation, which needs a Spring context of its own. */
  public Room createRoom(String owner) {
    Room room = new Room();
    room.setAwid(AWID);
    room.setRoomName("room of " + owner);
    room.setRoomOwner(new Player(owner));
    room.setGameName(GAME);
    room = roomDao.create(room);
    registry.create(AWID, room.getId(), GAME);
    return room;
  }

  public Room join(String roomId, String playerId) {
    RoomJoinDtoIn dtoIn = new RoomJoinDtoIn();
    dtoIn.setRoomId(roomId);
    dtoIn.setPlayerId(playerId);
    return roomAbl.roomJoinPlayer(AWID, dtoIn);
  }

  public GameState startGame(String roomId, String ownerId) {
    StartGameDtoIn dtoIn = new StartGameDtoIn();
    dtoIn.setRoomId(roomId);
    dtoIn.setPlayerId(ownerId);
    dtoIn.setGameParameters(new HashMap<>());
    return roomAbl.startGame(AWID, dtoIn);
  }

  public RoomAbl roomAbl() {
    return roomAbl;
  }

  /**
   * Runs the disconnect grace check against a clock of the test's choosing. Package private on
   * {@link RoomAbl} itself, so tests in other packages come through here.
   */
  public void applyPendingDisconnects(long nowMillis) {
    roomAbl.applyPendingDisconnects(nowMillis);
  }

  public GameEngineRegistry registry() {
    return registry;
  }

  public RoomMemoryDao roomDao() {
    return roomDao;
  }

  public RecordingScheduler scheduler() {
    return scheduler;
  }

  /** How many times the engine has asked Jackson to turn raw input into the rule's move type. */
  public int moveConversions() {
    return objectMapper.conversions.get();
  }

  public List<Object> publishedEvents() {
    return publishedEvents;
  }

  public void shutdown() {
    registry.shutdown();
  }

  private static ApplicationContext ruleOnlyContext() {
    return (ApplicationContext) Proxy.newProxyInstance(
      RoomAblFixture.class.getClassLoader(),
      new Class<?>[] {ApplicationContext.class},
      (proxy, method, args) -> {
        if ("getBean".equals(method.getName()) && args != null && args.length == 2) {
          if (GAME.equals(args[0])) {
            return new FakeRule();
          }
          throw new IllegalArgumentException("no such game: " + args[0]);
        }
        if ("hashCode".equals(method.getName())) {
          return System.identityHashCode(proxy);
        }
        if ("equals".equals(method.getName())) {
          return proxy == args[0];
        }
        if ("toString".equals(method.getName())) {
          return "ruleOnlyContext";
        }
        return null;
      });
  }

  private EventPublisherHelper recordingPublisher() {
    ApplicationEventPublisher publisher = (ApplicationEventPublisher) Proxy.newProxyInstance(
      RoomAblFixture.class.getClassLoader(),
      new Class<?>[] {ApplicationEventPublisher.class},
      (proxy, method, args) -> {
        if ("publishEvent".equals(method.getName()) && args != null && args.length == 1) {
          publishedEvents.add(args[0]);
        }
        return null;
      });
    return new EventPublisherHelper(publisher);
  }

  private static void set(Object target, String field, Object value) throws Exception {
    Field declared = target.getClass().getDeclaredField(field);
    declared.setAccessible(true);
    declared.set(target, value);
  }

  /**
   * Counts the conversion the engine performs on every accepted submission - the expensive half of
   * handling input, and the half throttling is supposed to skip.
   */
  private static final class CountingObjectMapper extends ObjectMapper {

    private final AtomicInteger conversions = new AtomicInteger();

    @Override
    public <T> T convertValue(Object fromValue, JavaType toValueType) {
      conversions.incrementAndGet();
      return super.convertValue(fromValue, toValueType);
    }
  }
}
