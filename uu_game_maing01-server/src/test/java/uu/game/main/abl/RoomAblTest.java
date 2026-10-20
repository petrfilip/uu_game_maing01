package uu.game.main.abl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static uu.game.main.abl.RoomAblFixture.AWID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uu.game.main.abl.dto.Player;
import uu.game.main.abl.dto.PlayerStateEnum;
import uu.game.main.abl.dto.Room;
import uu.game.main.api.dto.AddPlayerMoveDtoIn;
import uu.game.main.api.dto.RoomDeleteDtoIn;
import uu.game.main.api.exceptions.RoomAblRuntimeException;
import uu.game.main.domain.GameState;
import uu.game.main.engine.GameEngine;

public class RoomAblTest {

  private static final String OWNER = "alice";
  private static final String PLAYER = "bob";
  private static final String STRANGER = "mallory";
  private static final String OTHER_PLAYER = "carol";

  private static final long IDLE_MILLIS = 60_000;
  private static final long GRACE_MILLIS = 15_000;
  /** Low on purpose: a bucket that refills every 250 ms cannot refill while a tight loop runs. */
  private static final int SUBMISSIONS_PER_SECOND = 4;

  private RoomAblFixture fixture;
  private RoomAbl roomAbl;

  @Before
  public void setUp() {
    fixture = new RoomAblFixture(IDLE_MILLIS, SUBMISSIONS_PER_SECOND, GRACE_MILLIS);
    roomAbl = fixture.roomAbl();
  }

  @After
  public void tearDown() {
    fixture.shutdown();
  }

  private RoomDeleteDtoIn deleteDtoIn(String roomId, String playerId) {
    RoomDeleteDtoIn dtoIn = new RoomDeleteDtoIn();
    dtoIn.setRoomId(roomId);
    dtoIn.setPlayerId(playerId);
    return dtoIn;
  }

  private static List<Object> oneRawMove() {
    Map<String, Object> move = new HashMap<>();
    move.put("firedAngle", 1);
    return Collections.<Object>singletonList(move);
  }

  private boolean submit(String roomId, String playerId) {
    AddPlayerMoveDtoIn dtoIn = new AddPlayerMoveDtoIn();
    dtoIn.setRoomId(roomId);
    dtoIn.setPlayerId(playerId);
    dtoIn.setPlayerMoves(oneRawMove());
    return roomAbl.addPlayerMove(AWID, dtoIn);
  }

  private boolean submitNumbered(String roomId, String playerId, int seq) {
    Map<String, Object> move = new HashMap<>();
    move.put("firedAngle", 1);
    move.put("seq", seq);
    AddPlayerMoveDtoIn dtoIn = new AddPlayerMoveDtoIn();
    dtoIn.setRoomId(roomId);
    dtoIn.setPlayerId(playerId);
    dtoIn.setPlayerMoves(Collections.<Object>singletonList(move));
    return roomAbl.addPlayerMove(AWID, dtoIn);
  }

  private Player enginePlayer(String roomId, String playerId) {
    GameEngine engine = fixture.registry().find(AWID, roomId);
    assertNotNull("the room has no runtime", engine);
    for (Player player : engine.getPlayers()) {
      if (playerId.equals(player.getPlayerId())) {
        return player;
      }
    }
    throw new AssertionError("player " + playerId + " is not in the room");
  }

  // -------------------------------------------------------------------------------------------
  // Lifecycle
  // -------------------------------------------------------------------------------------------

  @Test
  public void deletingARoomStopsItsEngineAndLeavesNothingBehind() {
    Room room = fixture.createRoom(OWNER);
    fixture.join(room.getId(), PLAYER);
    fixture.startGame(room.getId(), OWNER);
    assertEquals("the match should be ticking", 1, fixture.scheduler().repeatingCount());

    roomAbl.roomDelete(AWID, deleteDtoIn(room.getId(), OWNER));

    // Nothing used to remove a room at all: the DAO and the registry both grew for the lifetime of
    // the process, and a deleted room's engine would have gone on ticking forever.
    assertTrue("the tick loop is still scheduled", fixture.scheduler().repeating(0).wasCancelled());
    assertNull("the engine is still in the registry", fixture.registry().find(AWID, room.getId()));
    assertFalse("the room is still in the datastore", fixture.roomDao().exists(AWID, room.getId()));
  }

  @Test
  public void onlyTheOwnerMayDeleteARoom() {
    Room room = fixture.createRoom(OWNER);
    fixture.join(room.getId(), PLAYER);

    try {
      roomAbl.roomDelete(AWID, deleteDtoIn(room.getId(), PLAYER));
      fail("a player who does not own the room must not be able to delete it");
    } catch (RoomAblRuntimeException expected) {
      assertEquals(RoomAblRuntimeException.Error.NOT_ROOM_OWNER.getCode().getErrorCode(), expected.getCode().getErrorCode());
    }

    assertTrue("the room was deleted anyway", fixture.roomDao().exists(AWID, room.getId()));
    assertNotNull("the engine was discarded anyway", fixture.registry().find(AWID, room.getId()));
  }

  @Test
  public void aRoomNobodyIsUsingIsReclaimedTogetherWithItsDatastoreRow() {
    Room room = fixture.createRoom(OWNER);

    assertEquals("nothing should be reclaimed before the timeout",
      0, reclaim(System.currentTimeMillis()));
    assertEquals(1, reclaim(System.currentTimeMillis() + IDLE_MILLIS + 1));

    // The registry can only drop the engine; the room row is the other half of the leak, and it
    // goes only because RoomAbl listens for reclamation.
    assertNull(fixture.registry().find(AWID, room.getId()));
    assertFalse("the abandoned room is still in the datastore", fixture.roomDao().exists(AWID, room.getId()));
  }

  private int reclaim(long nowMillis) {
    return fixture.registry().reclaimIdle(nowMillis);
  }

  // -------------------------------------------------------------------------------------------
  // Membership
  // -------------------------------------------------------------------------------------------

  @Test
  public void onlyTheOwnerAndTheJoinedPlayersAreMembersOfARoom() {
    Room room = fixture.createRoom(OWNER);
    fixture.join(room.getId(), PLAYER);

    // No exception expected for either of these.
    roomAbl.requireMember(AWID, room.getId(), OWNER);
    roomAbl.requireMember(AWID, room.getId(), PLAYER);

    try {
      roomAbl.requireMember(AWID, room.getId(), STRANGER);
      fail("knowing a room id must not be enough to watch the room");
    } catch (RoomAblRuntimeException expected) {
      assertEquals(RoomAblRuntimeException.Error.NOT_ROOM_MEMBER.getCode().getErrorCode(), expected.getCode().getErrorCode());
    }
  }

  // -------------------------------------------------------------------------------------------
  // Connect / disconnect
  // -------------------------------------------------------------------------------------------

  @Test
  public void aDisconnectedPlayerIsMarkedGoneOnlyOnceTheGracePeriodHasRunOut() {
    Room room = fixture.createRoom(OWNER);
    fixture.join(room.getId(), PLAYER);
    fixture.startGame(room.getId(), OWNER);
    long now = System.currentTimeMillis();

    roomAbl.playerDisconnected(AWID, room.getId(), PLAYER);

    roomAbl.applyPendingDisconnects(now);
    assertFalse("a player must not lose their place the instant the stream drops",
      PlayerStateEnum.DONE == enginePlayer(room.getId(), PLAYER).getState());

    roomAbl.applyPendingDisconnects(now + GRACE_MILLIS + 1);
    // removePlayer existed from the start and nothing ever called it, so a closed tab left a
    // motionless target on the board for the rest of the match.
    assertEquals(PlayerStateEnum.DONE, enginePlayer(room.getId(), PLAYER).getState());
  }

  @Test
  public void aPlayerWhoReconnectsWithinTheGracePeriodKeepsTheirPlace() {
    Room room = fixture.createRoom(OWNER);
    fixture.join(room.getId(), PLAYER);
    fixture.startGame(room.getId(), OWNER);
    long now = System.currentTimeMillis();

    roomAbl.playerDisconnected(AWID, room.getId(), PLAYER);
    roomAbl.playerConnected(AWID, room.getId(), PLAYER);

    roomAbl.applyPendingDisconnects(now + GRACE_MILLIS + 1);
    assertFalse("a refresh must not cost a player their match",
      PlayerStateEnum.DONE == enginePlayer(room.getId(), PLAYER).getState());
  }

  @Test
  public void rejoiningARoomCallsOffARemovalArmedByAClosedStream() {
    Room room = fixture.createRoom(OWNER);
    fixture.join(room.getId(), PLAYER);
    fixture.startGame(room.getId(), OWNER);
    long now = System.currentTimeMillis();

    roomAbl.playerDisconnected(AWID, room.getId(), PLAYER);
    // Reopening the stream is not the only way back into a match: a client that reloads the page
    // joins first and subscribes afterwards. Somebody who has demonstrably just called room/join
    // must not be dropped by a timer armed before they came back.
    fixture.join(room.getId(), PLAYER);

    roomAbl.applyPendingDisconnects(now + GRACE_MILLIS + 1);
    assertFalse("rejoining did not call off the removal armed by the closed stream",
      PlayerStateEnum.DONE == enginePlayer(room.getId(), PLAYER).getState());
  }

  @Test
  public void aDisconnectThatLandsAfterTheEngineIsGoneDoesNotBringItBack() {
    Room room = fixture.createRoom(OWNER);
    fixture.join(room.getId(), PLAYER);

    roomAbl.playerDisconnected(AWID, room.getId(), PLAYER);
    fixture.registry().remove(AWID, room.getId());
    roomAbl.applyPendingDisconnects(System.currentTimeMillis() + GRACE_MILLIS + 1);

    // The request path recreates a missing engine on demand, which is right for a join and wrong
    // here: a timer firing minutes later would rebuild a runtime nobody asked for, and the room
    // would be immortal by exactly the mechanism meant to reclaim it.
    assertNull(fixture.registry().find(AWID, room.getId()));
  }

  // -------------------------------------------------------------------------------------------
  // Initial state
  // -------------------------------------------------------------------------------------------

  @Test
  public void theSnapshotOfARunningMatchCarriesTheStaticPartsOfTheState() {
    Room room = fixture.createRoom(OWNER);
    fixture.join(room.getId(), PLAYER);
    fixture.startGame(room.getId(), OWNER);

    Object snapshot = roomAbl.currentSnapshot(AWID, room.getId());

    assertTrue("a client joining mid-match needs a game state, got " + snapshot, snapshot instanceof GameState);
    GameState<?> state = (GameState<?>) snapshot;
    // Per-tick payloads leave the arena out, so a client handed one of those on connect would never
    // learn the level geometry at all.
    assertTrue("the snapshot must be a full state", state.isFull());
    assertNotNull("the static parts are missing from the snapshot", state.getSetup());
  }

  @Test
  public void theSnapshotOfARoomWithNoMatchIsTheRoomItself() {
    Room room = fixture.createRoom(OWNER);

    Object snapshot = roomAbl.currentSnapshot(AWID, room.getId());

    assertTrue("a lobby client needs the room, got " + snapshot, snapshot instanceof Room);
    assertEquals(room.getId(), ((Room) snapshot).getId());
  }

  // -------------------------------------------------------------------------------------------
  // Input throttling
  // -------------------------------------------------------------------------------------------

  @Test
  public void submissionsBeyondTheLimitAreRejectedWithoutBeingDeserialised() {
    Room room = fixture.createRoom(OWNER);
    fixture.join(room.getId(), PLAYER);
    fixture.startGame(room.getId(), OWNER);

    int accepted = 0;
    for (int i = 0; i < 500; i++) {
      if (submit(room.getId(), PLAYER)) {
        accepted++;
      }
    }

    assertEquals(SUBMISSIONS_PER_SECOND, accepted);
    // The whole point: the rejected submissions must cost nothing. The engine caps how many moves
    // are applied, but every submission still paid for a Jackson conversion before that cap.
    assertEquals("a rejected submission was deserialised anyway",
      SUBMISSIONS_PER_SECOND, fixture.moveConversions());
  }

  @Test
  public void oneRoomsFloodDoesNotThrottleAnother() {
    Room flooded = fixture.createRoom(OWNER);
    Room quiet = fixture.createRoom(OWNER);
    fixture.join(flooded.getId(), PLAYER);
    fixture.join(quiet.getId(), PLAYER);
    fixture.startGame(flooded.getId(), OWNER);
    fixture.startGame(quiet.getId(), OWNER);

    for (int i = 0; i < 500; i++) {
      submit(flooded.getId(), PLAYER);
    }

    assertTrue("a player was throttled out of a room they had not flooded", submit(quiet.getId(), PLAYER));
  }

  @Test
  public void onePlayersFloodDoesNotThrottleAnother() {
    Room room = fixture.createRoom(OWNER);
    fixture.join(room.getId(), PLAYER);
    fixture.join(room.getId(), OTHER_PLAYER);
    fixture.startGame(room.getId(), OWNER);

    for (int i = 0; i < 500; i++) {
      submit(room.getId(), PLAYER);
    }

    // The ceiling is per player, not per match. A bucket shared by everyone in the room would let
    // one client on a bad connection - or one retry loop - spend the whole room's budget, and the
    // players who did nothing wrong would be the ones whose keypresses get dropped.
    assertTrue("a player was throttled by a flood somebody else sent",
      submit(room.getId(), OTHER_PLAYER));
  }

  @Test
  public void aThrottledSubmissionIsStillAcknowledgedSoTheClientStopsReplayingIt() {
    Room room = fixture.createRoom(OWNER);
    fixture.join(room.getId(), PLAYER);
    fixture.startGame(room.getId(), OWNER);

    int seq = 0;
    boolean throttled = false;
    while (seq < 100 && !throttled) {
      seq++;
      throttled = !submitNumbered(room.getId(), PLAYER, seq);
    }
    assertTrue("the limiter never rejected anything, so this test asserts nothing", throttled);

    // A client predicting locally replays every move the server has not settled. Refusing a
    // submission without settling it leaves the client offering it on top of every state it
    // receives until its own buffer evicts it - about a second and a half of being out of step
    // with the server, over input the server decided to ignore the moment it arrived.
    GameEngine engine = fixture.registry().find(AWID, room.getId());
    Integer settled = engine.lastProcessedSeq(PLAYER);
    assertNotNull("a throttled submission was swallowed without telling the client", settled);
    assertTrue("the throttled submission was never acknowledged", settled >= seq);
  }

  @Test
  public void aClientSubmittingOncePerTickIsNeverThrottled() {
    RoomAbl.MoveRateLimiter limiter = new RoomAbl.MoveRateLimiter();
    limiter.configure(100);

    long now = 1_000_000L;
    for (int tick = 0; tick < 2_000; tick++) {
      // One submission per 50 ms tick - what a correct client does, for a hundred seconds.
      assertTrue("a legitimate client was throttled on tick " + tick,
        limiter.tryAcquire("room/p1", now + tick * 50L));
    }
  }

  @Test
  public void aBurstIsCappedAtOneSecondOfSubmissionsAndThenDripsThrough() {
    RoomAbl.MoveRateLimiter limiter = new RoomAbl.MoveRateLimiter();
    limiter.configure(100);
    long now = 1_000_000L;

    int accepted = 0;
    for (int i = 0; i < 500; i++) {
      if (limiter.tryAcquire("room/p1", now)) {
        accepted++;
      }
    }

    assertEquals(100, accepted);
    // The bucket drains at the configured rate, so 10 ms later exactly one more gets through.
    assertTrue(limiter.tryAcquire("room/p1", now + 10));
    assertFalse(limiter.tryAcquire("room/p1", now + 10));
  }

  @Test
  public void concurrentSubmissionsForOnePlayerCannotExceedTheLimit() throws Exception {
    final RoomAbl.MoveRateLimiter limiter = new RoomAbl.MoveRateLimiter();
    limiter.configure(100);
    final long now = 1_000_000L;

    int threads = 8;
    int attemptsPerThread = 500;
    final AtomicInteger accepted = new AtomicInteger();
    final CountDownLatch go = new CountDownLatch(1);
    final CountDownLatch done = new CountDownLatch(threads);
    List<Thread> workers = new ArrayList<>();

    for (int i = 0; i < threads; i++) {
      Thread worker = new Thread(() -> {
        try {
          go.await();
          for (int attempt = 0; attempt < attemptsPerThread; attempt++) {
            if (limiter.tryAcquire("room/p1", now)) {
              accepted.incrementAndGet();
            }
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          done.countDown();
        }
      }, "submitter-" + i);
      workers.add(worker);
      worker.start();
    }

    go.countDown();
    assertTrue("the submitters did not finish", done.await(30, TimeUnit.SECONDS));
    for (Thread worker : workers) {
      worker.join(30_000);
    }

    // This runs on request threads: a read-modify-write that was not a compare-and-set would let
    // the limit be exceeded by roughly the number of threads racing.
    assertEquals(100, accepted.get());
  }
}
