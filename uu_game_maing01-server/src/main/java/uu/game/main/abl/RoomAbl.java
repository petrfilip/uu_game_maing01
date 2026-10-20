package uu.game.main.abl;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uu.app.datastore.domain.PageInfo;
import uu.app.datastore.domain.PagedResult;
import uu.app.validation.ValidationResult;
import uu.app.validation.Validator;
import uu.app.validation.utils.ValidationResultUtils;
import uu.game.main.abl.dto.Player;
import uu.game.main.abl.dto.Room;
import uu.game.main.abl.dto.RoomStateEnum;
import uu.game.main.abl.dto.event.GameEvent;
import uu.game.main.abl.dto.event.RoomEvent;
import uu.game.main.api.dto.AddPlayerMoveDtoIn;
import uu.game.main.api.dto.RoomCreateDtoIn;
import uu.game.main.api.dto.RoomDeleteDtoIn;
import uu.game.main.api.dto.RoomJoinDtoIn;
import uu.game.main.api.dto.RoomListDtoOut;
import uu.game.main.api.dto.StartGameDtoIn;
import uu.game.main.api.exceptions.RoomAblRuntimeException;
import uu.game.main.dao.RoomDao;
import uu.game.main.domain.GameState;
import uu.game.main.engine.GameCallback;
import uu.game.main.engine.GameEngine;
import uu.game.main.engine.GameEngineRegistry;
import uu.game.main.helper.EventPublisherHelper;

@Component
public class RoomAbl implements GameEngineRegistry.ReclaimListener {

  private static final Logger LOGGER = LogManager.getLogger(RoomAbl.class);

  @Inject
  private Validator validator;
  @Inject
  private RoomDao roomDao;
  @Inject
  private ModelMapper modelMapper;
  @Inject
  private EventPublisherHelper eventPublisher;
  @Inject
  private GameEngineRegistry gameEngineRegistry;

  @Value("${game.defaultGame:soldat}")
  private String defaultGame;

  /**
   * Ceiling on how many move submissions one player may make per second and per room.
   *
   * <p>A legitimate client submits once per received tick - 20/s at the default 50 ms tick - so 100
   * leaves five times the headroom a correct client will ever need, including the burst it sends
   * after a reconnect.
   */
  @Value("${game.room.moveSubmissionsPerSecond:100}")
  private int moveSubmissionsPerSecond = 100;

  /**
   * How long a player keeps their place on the board after their last stream closes.
   *
   * <p>Not zero, and not forever. A page refresh reopens the stream in well under a second and a
   * mobile network hiccup in a few, so removing on the first {@code onCompletion} would kill players
   * who are still very much playing - exactly the failure the disconnect handling is meant to avoid.
   * Fifteen seconds is about 300 ticks of standing still: survivable for the player who is coming
   * back, and short enough that someone who closed the tab is not a free target for the rest of the
   * match.
   */
  @Value("${game.room.disconnectGraceMillis:15000}")
  private long disconnectGraceMillis = 15_000;

  private final MoveRateLimiter moveRateLimiter = new MoveRateLimiter();

  /**
   * Players whose stream has closed and whose grace period has not run out yet, keyed by
   * awid/room/player.
   */
  private final ConcurrentMap<String, PendingDisconnect> pendingDisconnects = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    moveRateLimiter.configure(moveSubmissionsPerSecond);
    // Reclaiming an engine has to take the room with it, otherwise the DAO keeps growing - which is
    // the half of the leak the registry cannot see.
    gameEngineRegistry.addReclaimListener(this);
  }

  public Room roomCreate(String awid, RoomCreateDtoIn dtoIn) {
    LOGGER.info("Creating new room: {}", dtoIn);

    ValidationResult validationResult = validator.validate(dtoIn);
    if (!validationResult.isValid()) {
      // A1
      throw new RoomAblRuntimeException(RoomAblRuntimeException.Error.INVALID_DTO_IN, ValidationResultUtils.validationResultToAppErrorMap(validationResult));
    }

    String gameName = dtoIn.getGameName() == null ? defaultGame : dtoIn.getGameName();
    // Checked before the room is persisted, so an unknown game cannot leave a room behind that can
    // never be started.
    gameEngineRegistry.requireGame(gameName);

    Room room = new Room();
    room.setAwid(awid);
    room.setRoomName(dtoIn.getRoomName());
    room.setRoomOwner(new Player(dtoIn.getRoomOwner()));
    room.setGameName(gameName);
    room.setState(RoomStateEnum.WAITING);

    room = roomDao.create(room);
    gameEngineRegistry.create(awid, room.getId(), gameName);

    LOGGER.info("Room created: {}", room.getId());
    return room;
  }

  public RoomListDtoOut roomList(String awid) {
    PagedResult<Room> rooms = roomDao.list(awid, new PageInfo(0, Integer.MAX_VALUE));
    return modelMapper.map(rooms, RoomListDtoOut.class);
  }

  /**
   * Removes a room and everything the server was holding on its behalf.
   *
   * <p>Owner only, the same check {@code startGame} makes: a room id travels in a URL, so without it
   * anybody who has ever seen a link could end someone else's match.
   */
  public Room roomDelete(String awid, RoomDeleteDtoIn dtoIn) {
    Room room = requireRoom(awid, dtoIn.getRoomId());
    requireOwner(room, dtoIn.getPlayerId());

    LOGGER.info("Deleting room {}", room.getId());

    // Engine first: once the room row is gone nothing can look the engine up any more, and an
    // engine left behind would keep ticking - and keep its rule, board and roster alive - forever.
    gameEngineRegistry.remove(awid, room.getId());
    roomDao.delete(awid, room.getId());
    forgetRoom(awid, room.getId());

    // Published so clients watching the room stop rather than waiting out the SSE timeout.
    room.setState(RoomStateEnum.CLOSED);
    eventPublisher.publish(new RoomEvent(this, awid, room.getId(), room));
    return room;
  }

  @Override
  public void onRoomReclaimed(String awid, String roomId) {
    roomDao.delete(awid, roomId);
    forgetRoom(awid, roomId);
  }

  public Room roomJoinPlayer(String awid, RoomJoinDtoIn dtoIn) {
    Room room = requireRoom(awid, dtoIn.getRoomId());

    LOGGER.info("New player joined to room: {}", dtoIn.getRoomId());

    Player player = new Player(dtoIn.getPlayerId());
    if (!room.getConnectedPlayers().contains(player)) { // prevent to add same player multiple times
      room.getConnectedPlayers().add(player);
      // The in-memory DAO happens to hand back a live reference, but relying on that is what made
      // swapping in a real datastore silently lose writes.
      roomDao.update(room);
    }

    // Rejoining is a reconnect: whoever is calling this is plainly still here, so any removal armed
    // by their previous stream closing has to be called off.
    playerConnected(awid, dtoIn.getRoomId(), dtoIn.getPlayerId());
    engine(awid, dtoIn.getRoomId()).addPlayer(player);

    eventPublisher.publish(new RoomEvent(this, awid, dtoIn.getRoomId(), room));

    return room;
  }

  /**
   * Buffers a player's input for the next tick.
   *
   * <p>No room lookup and no RoomEvent here: this is the hottest endpoint in the app and the room
   * has not changed. Publishing a room-wide event per keypress meant every input from every player
   * was fanned out to every subscriber.
   *
   * @return {@code false} when the submission was throttled away. Dropped silently rather than
   *     answered with an error: a game client already copes with a lost input frame, and building a
   *     fault response would cost more than the work the throttle is there to avoid.
   */
  public boolean addPlayerMove(String awid, AddPlayerMoveDtoIn dtoIn) {
    // Before anything expensive. The engine caps how many moves are *applied* per tick, but a
    // submission that is going to be thrown away still pays for a room lookup and for Jackson
    // converting the raw move list into the rule's move type, and a client can loop on this
    // endpoint as fast as the network allows.
    if (!moveRateLimiter.tryAcquire(
      playerKey(awid, dtoIn.getRoomId(), dtoIn.getPlayerId()), System.currentTimeMillis())) {
      acknowledgeThrottled(awid, dtoIn);
      return false;
    }

    engine(awid, dtoIn.getRoomId()).addPlayerMove(dtoIn.getPlayerId(), dtoIn.getPlayerMoves());
    return true;
  }

  /**
   * Tells the simulation to settle input it has just refused, so a client predicting locally is not
   * left replaying it forever; see {@link GameEngine#acknowledgeDiscarded}.
   *
   * <p>This is on the rejected path only and deliberately stays cheap: a lookup in the registry's
   * map and a walk over a list the request had already parsed. What the throttle exists to avoid is
   * the Jackson conversion into the rule's move type, and that still does not happen. A room that is
   * not live gets nothing - there is no simulation to be out of step with.
   */
  private void acknowledgeThrottled(String awid, AddPlayerMoveDtoIn dtoIn) {
    GameEngine engine = gameEngineRegistry.find(awid, dtoIn.getRoomId());
    if (engine != null) {
      engine.acknowledgeDiscarded(dtoIn.getPlayerId(), highestSeq(dtoIn.getPlayerMoves()));
    }
  }

  /**
   * The highest {@code seq} in a raw, still undeserialised move list, or {@code null} if the client
   * does not number its input.
   */
  private static Integer highestSeq(List<Object> rawMoves) {
    if (rawMoves == null) {
      return null;
    }
    Integer highest = null;
    for (Object rawMove : rawMoves) {
      if (!(rawMove instanceof Map)) {
        continue;
      }
      Object seq = ((Map<?, ?>) rawMove).get("seq");
      if (seq instanceof Number) {
        int value = ((Number) seq).intValue();
        if (highest == null || value > highest) {
          highest = value;
        }
      }
    }
    return highest;
  }

  public GameState startGame(String awid, StartGameDtoIn dtoIn) {
    Room room = requireRoom(awid, dtoIn.getRoomId());
    GameEngine engine = engine(awid, dtoIn.getRoomId());

    requireOwner(room, dtoIn.getPlayerId());

    LOGGER.info("Starting new game in room {}", dtoIn.getRoomId());

    room.setState(RoomStateEnum.ACTIVE);
    roomDao.update(room);
    for (Player connectedPlayer : room.getConnectedPlayers()) {
      engine.addPlayer(connectedPlayer);
    }

    dtoIn.getGameParameters().put("awid", awid);

    String roomId = dtoIn.getRoomId();
    engine.startGame(new GameCallback() {
      @Override
      public void onNextRound(GameState gameState) {
        eventPublisher.publish(new GameEvent(this, awid, roomId, gameState));
      }

      @Override
      public void onGameEnd(GameState gameState) {
        LOGGER.info("Game finished in room {}", roomId);
        eventPublisher.publish(new GameEvent(this, awid, roomId, gameState));
      }
    }, dtoIn.getGameParameters());

    // A match start is the one moment every client needs the static parts of the state, so this
    // goes out as a full state rather than as the delta the ticks carry.
    GameState<Object> startState = fullState(engine);
    eventPublisher.publish(new GameEvent(this, awid, roomId, startState));
    return startState;
  }

  // ---------------------------------------------------------------------------------------------
  // Membership
  // ---------------------------------------------------------------------------------------------

  /**
   * Asserts that a player is entitled to watch a room.
   *
   * <p>Spectating is deliberately <em>not</em> allowed. A room id is not a secret - it sits in the
   * page URL and in every {@code room/list} response - so without this check it works as a bearer
   * token for someone else's match: any account could open a stream on a room it never joined, read
   * every player's position twenty times a second and hold a connection and an outbound queue on the
   * server for as long as it liked. If spectating is wanted later it should be a room setting the
   * owner turns on, not a gap.
   */
  public void requireMember(String awid, String roomId, String playerId) {
    Room room = requireRoom(awid, roomId);
    if (!isMember(room, playerId)) {
      throw new RoomAblRuntimeException(RoomAblRuntimeException.Error.NOT_ROOM_MEMBER, null);
    }
  }

  private boolean isMember(Room room, String playerId) {
    if (playerId == null) {
      return false;
    }
    Player owner = room.getRoomOwner();
    if (owner != null && playerId.equals(owner.getPlayerId())) {
      // The owner may watch before anyone - including the owner - has joined.
      return true;
    }
    return room.getConnectedPlayers().contains(new Player(playerId));
  }

  private void requireOwner(Room room, String playerId) {
    Player owner = room.getRoomOwner();
    if (owner == null || !owner.getPlayerId().equals(playerId)) {
      throw new RoomAblRuntimeException(RoomAblRuntimeException.Error.NOT_ROOM_OWNER, null);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Presence
  // ---------------------------------------------------------------------------------------------

  /**
   * The snapshot a client that has just connected needs in order to render at all.
   *
   * @return the full game state if a match has started, otherwise the room itself, or {@code null}
   *     when there is nothing to send
   */
  public Object currentSnapshot(String awid, String roomId) {
    GameEngine engine = gameEngineRegistry.find(awid, roomId);
    GameState<Object> state = engine == null ? null : engine.getCurrentState();
    if (state != null) {
      return fullState(engine);
    }
    return roomDao.exists(awid, roomId) ? roomDao.get(awid, roomId) : null;
  }

  /** A player has a live stream again; any pending removal is called off. */
  public void playerConnected(String awid, String roomId, String playerId) {
    if (playerId == null) {
      return;
    }
    pendingDisconnects.remove(playerKey(awid, roomId, playerId));
  }

  /**
   * A player's last stream for a room has closed.
   *
   * <p>The removal is armed, not applied: see {@code game.room.disconnectGraceMillis} for why a
   * refresh must not cost a player their match. The timer only re-checks the deadline, so a
   * reconnect that arrives first simply leaves nothing to do.
   */
  public void playerDisconnected(String awid, String roomId, String playerId) {
    if (playerId == null) {
      return;
    }
    long now = System.currentTimeMillis();
    pendingDisconnects.put(playerKey(awid, roomId, playerId),
      new PendingDisconnect(awid, roomId, playerId, now + disconnectGraceMillis));

    // Runs on the engine registry's pool; a grace period is not worth a scheduler of its own.
    gameEngineRegistry.schedule(
      () -> applyPendingDisconnects(System.currentTimeMillis()), disconnectGraceMillis, TimeUnit.MILLISECONDS);
  }

  /**
   * Marks every player whose grace period has run out as gone.
   *
   * @param nowMillis the clock to judge against; passed in so tests need not wait for one
   */
  void applyPendingDisconnects(long nowMillis) {
    Iterator<Map.Entry<String, PendingDisconnect>> iterator = pendingDisconnects.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, PendingDisconnect> entry = iterator.next();
      PendingDisconnect pending = entry.getValue();
      if (pending.deadlineMillis > nowMillis) {
        continue;
      }
      // Two-argument remove: a reconnect that has just replaced this entry must not be undone.
      if (!pendingDisconnects.remove(entry.getKey(), pending)) {
        continue;
      }

      // find, not engine(): a room that has gone away in the meantime must not be resurrected by a
      // disconnect arriving late.
      GameEngine engine = gameEngineRegistry.find(pending.awid, pending.roomId);
      if (engine == null) {
        continue;
      }
      LOGGER.debug("Player {} gave up their place in room {}", pending.playerId, pending.roomId);
      // The player stays on the roster so the scoreboard still shows them; the rule sees they are
      // done and stops simulating them, which is what stops the abandoned body being a free target.
      engine.removePlayer(pending.playerId);
    }
  }

  private static final class PendingDisconnect {

    private final String awid;
    private final String roomId;
    private final String playerId;
    private final long deadlineMillis;

    private PendingDisconnect(String awid, String roomId, String playerId, long deadlineMillis) {
      this.awid = awid;
      this.roomId = roomId;
      this.playerId = playerId;
      this.deadlineMillis = deadlineMillis;
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------------------------------

  private Room requireRoom(String awid, String roomId) {
    if (roomId == null || !roomDao.exists(awid, roomId)) {
      throw new RoomAblRuntimeException(RoomAblRuntimeException.Error.ROOM_DOES_NOT_EXIST, null);
    }
    return roomDao.get(awid, roomId);
  }

  /**
   * Engines do not survive a restart of the server while rooms (in a real datastore) would, so an
   * engine is recreated on demand from the room's game name.
   */
  private GameEngine engine(String awid, String roomId) {
    GameEngine engine = gameEngineRegistry.find(awid, roomId);
    if (engine != null) {
      return engine;
    }
    Room room = requireRoom(awid, roomId);
    return gameEngineRegistry.create(awid, roomId, room.getGameName() == null ? defaultGame : room.getGameName());
  }

  /**
   * The state including the parts a client cannot get from a tick payload - the arena and the match
   * settings, which are sent once rather than twenty times a second.
   */
  private GameState<Object> fullState(GameEngine engine) {
    return engine.fullState();
  }

  /** Drops the per-room bookkeeping that would otherwise outlive the room it belongs to. */
  private void forgetRoom(String awid, String roomId) {
    String prefix = awid + "/" + roomId + "/";
    moveRateLimiter.forget(prefix);
    pendingDisconnects.keySet().removeIf(key -> key.startsWith(prefix));
  }

  private String playerKey(String awid, String roomId, String playerId) {
    return awid + "/" + roomId + "/" + playerId;
  }

  /**
   * Per-player admission control for move submission.
   *
   * <p>A leaky bucket in its virtual-scheduling form: one {@link AtomicLong} per player holding the
   * clock reading at which its bucket would next be empty. Admitting a submission is one read and
   * one compare-and-set - no lock, no allocation and nothing that request threads can contend on
   * beyond a single word, which matters because this is on the hot path of every keypress in every
   * match on the server.
   */
  static final class MoveRateLimiter {

    private final ConcurrentMap<String, AtomicLong> buckets = new ConcurrentHashMap<>();

    /** Milliseconds of bucket the next submission costs. */
    private volatile long costMillis = 10;

    /** How far the bucket may run ahead of the clock, i.e. the burst, expressed in cost. */
    private volatile long toleranceMillis = 990;

    void configure(int submissionsPerSecond) {
      int rate = Math.max(1, submissionsPerSecond);
      // Integer division: a rate above 1000/s would round the cost to zero and let everything
      // through, so the cost is floored at a millisecond.
      this.costMillis = Math.max(1, 1000L / rate);
      // One full second of submissions may arrive back to back from an empty bucket; after that the
      // caller is held to the configured rate.
      this.toleranceMillis = (rate - 1) * this.costMillis;
    }

    boolean tryAcquire(String key, long nowMillis) {
      AtomicLong bucket = buckets.computeIfAbsent(key, k -> new AtomicLong());
      for (; ; ) {
        long emptyAt = bucket.get();
        long arrival = Math.max(emptyAt, nowMillis);
        if (arrival - toleranceMillis > nowMillis) {
          return false;
        }
        if (bucket.compareAndSet(emptyAt, arrival + costMillis)) {
          return true;
        }
        // Lost the race to another request thread for the same player; re-read and decide again.
      }
    }

    void forget(String keyPrefix) {
      buckets.keySet().removeIf(key -> key.startsWith(keyPrefix));
    }
  }
}
