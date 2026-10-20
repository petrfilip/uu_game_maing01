package uu.game.main.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uu.game.main.abl.dto.Player;
import uu.game.main.domain.GameState;
import uu.game.main.domain.GameStateEnum;
import uu.game.main.game.common.GameRuleEvent;

public class GameEngineTest {

  private static final long JOIN_TIMEOUT_MS = 10_000;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private CapturingScheduler scheduler;
  private FakeRule rule;
  private GameEngine engine;
  private RecordingCallback callback;

  private static final class RecordingObserver implements TickObserver {

    private final AtomicInteger ticks = new AtomicInteger();
    private final AtomicInteger failures = new AtomicInteger();

    @Override
    public void onTick(String gameName, long durationNanos, boolean failed) {
      ticks.incrementAndGet();
      if (failed) {
        failures.incrementAndGet();
      }
    }
  }

  private static final class RecordingCallback implements GameCallback {

    private final AtomicInteger ends = new AtomicInteger();
    private final AtomicInteger rounds = new AtomicInteger();

    @Override
    public void onGameEnd(GameState gameState) {
      ends.incrementAndGet();
    }

    @Override
    public void onNextRound(GameState gameState) {
      rounds.incrementAndGet();
    }
  }

  @Before
  public void setUp() {
    scheduler = new CapturingScheduler();
    rule = new FakeRule();
    callback = new RecordingCallback();
    engine = new GameEngine("fake", rule, objectMapper, scheduler, 50);
  }

  @After
  public void tearDown() {
    scheduler.shutdownNow();
  }

  private Player join(String playerId) {
    Player player = new Player(playerId);
    player.setPlayerName(playerId);
    engine.addPlayer(player);
    return player;
  }

  private void start() {
    engine.startGame(callback, new HashMap<String, Object>());
  }

  /** One raw move as it arrives from the client, tagged with {@code firedAngle}. */
  private static Map<String, Object> rawMove(int tag) {
    Map<String, Object> move = new HashMap<>();
    move.put("firedAngle", tag);
    return move;
  }

  private static List<Object> rawMoves(int... tags) {
    List<Object> moves = new ArrayList<>();
    for (int tag : tags) {
      moves.add(rawMove(tag));
    }
    return moves;
  }

  /**
   * Raw moves as they arrive from a client that numbers its input, in the order given - which is
   * deliberately not always ascending, because these travel over HTTP and can overtake each other.
   */
  private static List<Object> numberedMoves(int... seqs) {
    List<Object> moves = new ArrayList<>();
    for (int seq : seqs) {
      Map<String, Object> move = new HashMap<>();
      move.put("seq", seq);
      moves.add(move);
    }
    return moves;
  }

  private List<Integer> appliedSeqs() {
    List<Integer> seqs = new ArrayList<>();
    for (Object move : rule.getLastMoves()) {
      seqs.add(((FakeMove) move).getSeq());
    }
    return seqs;
  }

  /** What the published state promises this player about how far its input has got. */
  private Integer acknowledged(String playerId) {
    return engine.getCurrentState().getLastProcessedSeq().get(playerId);
  }

  @Test
  public void restartingWaitsForThePreviousTickToFinish() throws Exception {
    CountDownLatch tickEntered = new CountDownLatch(1);
    CountDownLatch releaseTick = new CountDownLatch(1);
    CountDownLatch restartAttempted = new CountDownLatch(1);
    CountDownLatch restartFinished = new CountDownLatch(1);
    FakeRule blockingRule = new FakeRule() {
      @Override
      public GameState<String> getNextGameState(GameState<String> state,
                                               Map<Player, List<FakeMove>> moves) {
        tickEntered.countDown();
        try {
          assertTrue(releaseTick.await(JOIN_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
          throw new AssertionError(e);
        }
        return super.getNextGameState(state, moves);
      }
    };
    engine = new GameEngine("fake", blockingRule, objectMapper, scheduler, 50);
    start();
    Thread tick = new Thread(engine::calculateNextState);
    Thread restart = new Thread(() -> {
      restartAttempted.countDown();
      start();
      restartFinished.countDown();
    });
    tick.start();
    try {
      assertTrue(tickEntered.await(JOIN_TIMEOUT_MS, TimeUnit.MILLISECONDS));
      restart.start();
      assertTrue(restartAttempted.await(JOIN_TIMEOUT_MS, TimeUnit.MILLISECONDS));
      assertFalse("restart reinitialised the rule while its old tick was running",
        restartFinished.await(100, TimeUnit.MILLISECONDS));
    } finally {
      releaseTick.countDown();
      tick.join(JOIN_TIMEOUT_MS);
      restart.join(JOIN_TIMEOUT_MS);
    }
    assertFalse(tick.isAlive());
    assertFalse(restart.isAlive());
    assertEquals("the previous tick overwrote the restarted match", Integer.valueOf(0),
      engine.getCurrentState().getTick());
  }

  @Test
  public void deserialisesInputWithTheMoveClassTheRuleDeclares() {
    join("p1");
    start();

    engine.addPlayerMove("p1", rawMoves(7));
    engine.calculateNextState();

    List<Object> seen = rule.getLastMoves();
    assertEquals(1, seen.size());
    // The engine used to construct one game's move class by hand, so a second game was impossible.
    assertTrue("engine handed the rule a " + seen.get(0).getClass(), seen.get(0) instanceof FakeMove);
    assertEquals(7, ((FakeMove) seen.get(0)).getFiredAngle(), 1e-9);
  }

  @Test
  public void appliesAtMostTheCapPerTickAndKeepsTheNewestMoves() {
    join("p1");
    start();

    engine.addPlayerMove("p1", rawMoves(1, 2, 3));
    engine.addPlayerMove("p1", rawMoves(4, 5, 6));
    engine.calculateNextState();

    List<Double> tags = new ArrayList<>();
    for (Object move : rule.getLastMoves()) {
      tags.add(((FakeMove) move).getFiredAngle());
    }
    // Honouring every submission was a straight teleport across the map; the newest moves describe
    // the player's most recent intent.
    assertEquals(Arrays.asList(3.0, 4.0, 5.0, 6.0), tags);
    assertEquals(GameEngine.MAX_MOVES_PER_TICK, tags.size());
  }

  @Test
  public void movesFromDifferentPlayersAreCappedIndependently() {
    join("p1");
    join("p2");
    start();

    engine.addPlayerMove("p1", rawMoves(1, 2, 3, 4, 5, 6));
    engine.addPlayerMove("p2", rawMoves(1, 2));
    engine.calculateNextState();

    assertEquals(GameEngine.MAX_MOVES_PER_TICK + 2, rule.getMovesSeen());
  }

  @Test
  public void inputSubmittedWhileTheGameIsNotRunningIsIgnored() {
    join("p1");

    engine.addPlayerMove("p1", rawMoves(1));
    start();
    engine.calculateNextState();

    assertEquals(0, rule.getMovesSeen());
  }

  @Test
  public void inputFromAPlayerWhoLeftIsIgnored() {
    join("p1");
    start();
    engine.removePlayer("p1");

    engine.addPlayerMove("p1", rawMoves(1));
    engine.calculateNextState();

    assertEquals(0, rule.getMovesSeen());
  }

  @Test
  public void noInputSubmittedConcurrentlyWithATickIsLost() throws Exception {
    int producers = 8;
    int playersPerProducer = 25;
    for (int producer = 0; producer < producers; producer++) {
      for (int i = 0; i < playersPerProducer; i++) {
        join("p-" + producer + "-" + i);
      }
    }
    start();

    final CountDownLatch go = new CountDownLatch(1);
    final CountDownLatch done = new CountDownLatch(producers);
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    List<Thread> threads = new ArrayList<>();

    for (int producer = 0; producer < producers; producer++) {
      final int index = producer;
      Thread thread = new Thread(() -> {
        try {
          go.await();
          for (int i = 0; i < playersPerProducer; i++) {
            // One move per player, so the per-player cap can never be the reason a move vanishes.
            engine.addPlayerMove("p-" + index + "-" + i, rawMoves(i));
          }
        } catch (Throwable t) {
          failure.compareAndSet(null, t);
        } finally {
          done.countDown();
        }
      }, "producer-" + producer);
      threads.add(thread);
      thread.start();
    }

    Thread ticker = new Thread(() -> {
      try {
        go.await();
        while (done.getCount() > 0) {
          engine.calculateNextState();
        }
      } catch (Throwable t) {
        failure.compareAndSet(null, t);
      }
    }, "ticker");
    ticker.start();

    go.countDown();
    assertTrue("producers did not finish", done.await(JOIN_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    ticker.join(JOIN_TIMEOUT_MS);
    for (Thread thread : threads) {
      thread.join(JOIN_TIMEOUT_MS);
    }
    // Final drain for anything submitted after the ticker stopped looping.
    engine.calculateNextState();

    if (failure.get() != null) {
      throw new AssertionError(failure.get());
    }
    assertEquals(producers * playersPerProducer, rule.getMovesSeen());
  }

  @Test
  public void tickingStopsWhenTheRuleReportsFinishedAndOnGameEndFiresExactlyOnce() {
    join("p1");
    start();
    rule.finishAtTick(2);

    engine.calculateNextState();
    engine.calculateNextState();
    assertEquals(GameStateEnum.FINISHED, engine.getCurrentState().getState());

    // Anything still queued on the executor, or a caller that keeps ticking, must be a no-op.
    engine.calculateNextState();
    engine.calculateNextState();

    assertEquals(1, callback.ends.get());
    assertEquals(2, rule.getTicks());
    assertTrue("the schedule must be cancelled when the match ends",
      scheduler.future(0).isCancelledByEngine());
    assertFalse(engine.isRunning());
  }

  @Test
  public void restartingAGameCancelsThePreviousSchedule() {
    join("p1");
    start();
    start();

    assertEquals(2, scheduler.scheduleCount());
    assertTrue("the first tick loop is still running", scheduler.future(0).isCancelledByEngine());
    assertFalse(scheduler.future(1).isCancelledByEngine());
  }

  @Test
  public void restartingAGameDropsInputBufferedForThePreviousMatch() {
    join("p1");
    start();
    engine.addPlayerMove("p1", rawMoves(1));

    start();
    engine.calculateNextState();

    assertEquals(0, rule.getMovesSeen());
  }

  @Test
  public void aRuleThatThrowsDoesNotKillTheTickLoop() {
    join("p1");
    start();
    rule.throwOnTick(1);

    Runnable tick = scheduler.lastTask();
    tick.run();
    tick.run();

    // A task that throws is dropped by the executor, so the game would freeze silently.
    assertEquals(2, rule.getTicks());
    assertTrue(engine.isRunning());
  }

  @Test
  public void aRuleThatFailsWithAnErrorIsNeitherLostNorReportedAsHealthy() {
    join("p1");
    start();
    rule.throwErrorOnTick(1);
    RecordingObserver observed = new RecordingObserver();
    engine.setTickObserver(observed);

    Runnable tick = scheduler.lastTask();
    tick.run();
    tick.run();

    // An Error is not an Exception, so a catch of Exception lets it escape: the executor drops the
    // task and the match freezes for good. Worse, the tick would be measured in the finally and
    // recorded as a *successful* one, so the single failure that permanently kills a room would be
    // the one the numbers call healthy.
    assertEquals(2, rule.getTicks());
    assertTrue(engine.isRunning());
    assertEquals(2, observed.ticks.get());
    assertEquals("a tick that died on an Error was reported as a good one", 1, observed.failures.get());
  }

  @Test
  public void aBrokenTickObserverDoesNotKillTheTickLoop() {
    join("p1");
    start();
    engine.setTickObserver(new TickObserver() {
      @Override
      public void onTick(String gameName, long durationNanos, boolean failed) {
        throw new IllegalStateException("counting the tick blew up");
      }
    });

    Runnable tick = scheduler.lastTask();
    tick.run();
    tick.run();

    // The tick body is guarded, but the observer runs in the finally: unguarded, a bug in something
    // whose only job is to count would escape the task and freeze every match on the server.
    assertEquals(2, rule.getTicks());
    assertTrue(engine.isRunning());
  }

  @Test
  public void everyTickPublishesTheRosterAlongsideTheBoard() {
    Player player = join("p1");
    player.setScore(4);
    // A rule is free to return a brand new state object, so the roster cannot simply survive from
    // startGame - the engine has to attach it every tick.
    rule.returnFreshStateEachTick();
    start();

    engine.calculateNextState();

    // The board's player map is keyed by id only, so without this the client cannot render a
    // scoreboard at all.
    assertEquals(1, engine.getCurrentState().getPlayers().size());
    assertEquals(Integer.valueOf(4), engine.getCurrentState().getPlayers().iterator().next().getScore());
  }

  @Test
  public void theStateBroadcastOnEveryTickLeavesTheStaticPartsOut() {
    join("p1");
    start();
    engine.calculateNextState();

    GameState<Object> published = engine.getCurrentState();
    // What goes out twenty times a second is a delta; everything that cannot change is somebody
    // else's payload.
    assertFalse(published.isFull());
    assertNull(published.getSetup());
  }

  @Test
  public void aClientConnectingMidMatchIsGivenTheStaticPartsWithTheLiveSnapshot() {
    join("p1");
    start();
    engine.calculateNextState();
    engine.calculateNextState();

    GameState<Object> full = engine.fullState();

    assertTrue("a client with nothing to merge into has to be told it received everything", full.isFull());
    assertNotNull("without the setup the client cannot draw the level at all", full.getSetup());
    assertEquals(1, full.getSetup().getObstacles().size());
    assertEquals(320, full.getSetup().getArea().getWidth());
    // ...and it is the live snapshot, not the state the match started in.
    assertEquals(engine.getCurrentState().getTick(), full.getTick());
    assertEquals(engine.getCurrentState().getGame(), full.getGame());
  }

  @Test
  public void aClientConnectingMidMatchIsNotToldAboutTheEventsItMissed() {
    join("p1");
    rule.emitEventEachTick(new GameRuleEvent("death", "Player", "p1"));
    start();
    engine.calculateNextState();

    assertEquals("the tick that produced the event still has to carry it",
      1, engine.getCurrentState().getGameEvents().size());
    // An event belongs to the tick that produced it. Carrying the last tick's events into the
    // snapshot a late joiner receives would have it play the sound of a death that happened
    // before it was watching, at the moment it opens the page.
    assertTrue("a client connecting mid match replayed an event from before it connected",
      engine.fullState().getGameEvents().isEmpty());
  }

  @Test
  public void theStaticPartsSurviveARuleThatReturnsAFreshStateEveryTick() {
    join("p1");
    // A rule is allowed to hand back a brand new state object, so nothing the engine must keep
    // publishing can be left to survive on the state itself.
    rule.returnFreshStateEachTick();
    start();
    engine.calculateNextState();

    assertNotNull(engine.fullState().getSetup());
  }

  @Test
  public void aRoomWhoseMatchHasNotStartedStillAnswersWithSomethingRenderable() {
    join("p1");

    GameState<Object> full = engine.fullState();

    // A subscriber connects to a room long before anybody presses start; returning null here would
    // be an NPE in the broadcast path rather than a "waiting" screen.
    assertNotNull(full);
    assertTrue(full.isFull());
    assertEquals(GameStateEnum.WAITING, full.getState());
    assertEquals(1, full.getPlayers().size());
  }

  @Test
  public void theMatchStartBroadcastCarriesTheStaticParts() {
    join("p1");

    // startGame's return value is what the room publishes when a match begins; it is the first
    // thing every client in the room sees.
    GameState<Object> started = engine.startGame(callback, new HashMap<String, Object>());

    assertTrue(started.isFull());
    assertNotNull(started.getSetup());
    assertFalse("the live state the engine keeps ticking is the lean one",
      engine.getCurrentState().isFull());
  }

  @Test
  public void theStateTellsAClientHowFarThroughItsInputTheSimulationHasGot() {
    join("p1");
    start();

    engine.addPlayerMove("p1", numberedMoves(1, 2));
    engine.calculateNextState();

    // Without this number the client cannot tell which of its own keystrokes are already in the
    // position it was just sent, so its only safe option is to throw its prediction away - which is
    // the snap-back every player saw at any real latency.
    assertEquals(Integer.valueOf(2), acknowledged("p1"));
    // ...and it is the number off the wire, so the field really is called `seq` in the payload.
    assertEquals(Arrays.asList(1, 2), appliedSeqs());
  }

  @Test
  public void movesThePerTickCapThrewAwayAreAcknowledgedAsWell() {
    join("p1");
    start();

    // Six moves for one tick, arriving newest-first because two submissions overtook each other.
    // The cap keeps the four newest by arrival, so 5 and 6 are discarded and will never be applied.
    engine.addPlayerMove("p1", numberedMoves(6, 5, 4, 3, 2, 1));
    engine.calculateNextState();

    assertEquals(Arrays.asList(4, 3, 2, 1), appliedSeqs());
    // The watermark is a promise - "nothing of yours at or below this will ever be applied" - not a
    // receipt for what was applied. Acknowledging only the highest applied move (4) would leave 5
    // and 6 in the client's pending buffer, where it would replay them on top of the authoritative
    // position on every state from here on. The server never reaches that position and nothing ever
    // corrects the client, so the offset is permanent.
    assertEquals(Integer.valueOf(6), acknowledged("p1"));
  }

  @Test
  public void theAcknowledgementIsTheHighestNumberInABatchAndNotTheLastOneInIt() {
    join("p1");
    start();

    // Two submissions that overtook each other on the way in and are drained by the same tick.
    engine.addPlayerMove("p1", numberedMoves(9));
    engine.addPlayerMove("p1", numberedMoves(3));
    engine.calculateNextState();

    // Taking the last move's number would un-acknowledge 9 and have the client replay a move the
    // simulation has already made.
    assertEquals(Integer.valueOf(9), acknowledged("p1"));
  }

  @Test
  public void inputTheSimulationHasAlreadySettledIsNotAppliedASecondTime() {
    join("p1");
    start();

    engine.addPlayerMove("p1", numberedMoves(5));
    engine.calculateNextState();
    assertEquals(Integer.valueOf(5), acknowledged("p1"));

    // A retry, and a submission that lost the race to a later one. Moves travel over HTTP, so both
    // happen. Applying them would walk the player further than the client counted on, and the two
    // would be permanently out of step.
    engine.addPlayerMove("p1", numberedMoves(5));
    engine.addPlayerMove("p1", numberedMoves(3, 4));
    engine.calculateNextState();

    assertEquals(1, rule.getMovesSeen());
    assertEquals("a sequence number that goes backwards must not drag the watermark back with it",
      Integer.valueOf(5), acknowledged("p1"));
  }

  @Test
  public void everyPlayerIsAcknowledgedSeparately() {
    join("p1");
    join("p2");
    start();

    engine.addPlayerMove("p1", numberedMoves(7));
    engine.addPlayerMove("p2", numberedMoves(2));
    engine.calculateNextState();

    // Sequence numbers are the client's own counter, so one player being further along must not
    // acknowledge - or reject - another player's input.
    assertEquals(Integer.valueOf(7), acknowledged("p1"));
    assertEquals(Integer.valueOf(2), acknowledged("p2"));
  }

  @Test
  public void inputFromAClientThatDoesNotNumberItsMovesIsStillApplied() {
    join("p1");
    start();

    engine.addPlayerMove("p1", numberedMoves(5));
    engine.calculateNextState();

    // Numbering is the client's half of a bargain it is free not to enter into, and a game that
    // never predicts locally has no reason to. Swallowing this would stop such a player dead.
    engine.addPlayerMove("p1", rawMoves(1));
    engine.calculateNextState();

    assertEquals(2, rule.getMovesSeen());
    assertEquals("an unnumbered move acknowledges nothing, least of all backwards",
      Integer.valueOf(5), acknowledged("p1"));
  }

  @Test
  public void aNewMatchStartsTheAcknowledgementsAgainFromNothing() {
    join("p1");
    start();
    engine.addPlayerMove("p1", numberedMoves(9));
    engine.calculateNextState();

    start();
    // Sequence numbers are per match, so the next one a client sends starts low again. A watermark
    // left over from the previous match would reject everything it sends for the rest of the round.
    engine.addPlayerMove("p1", numberedMoves(1));
    engine.calculateNextState();

    assertEquals(Arrays.asList(1), appliedSeqs());
    assertEquals(Integer.valueOf(1), acknowledged("p1"));
  }

  @Test
  public void theAcknowledgementsSurviveARuleThatReturnsAFreshStateEveryTick() {
    join("p1");
    // Nothing the engine has to publish on every state can be left to survive on the state itself.
    rule.returnFreshStateEachTick();
    start();

    engine.addPlayerMove("p1", numberedMoves(3));
    engine.calculateNextState();

    assertEquals(Integer.valueOf(3), acknowledged("p1"));
  }

  @Test
  public void aClientConnectingMidMatchIsToldWhereTheSimulationHasGotToWithItsInput() {
    join("p1");
    start();
    engine.addPlayerMove("p1", numberedMoves(4));
    engine.calculateNextState();

    // A reconnecting client has to learn the watermark before it can predict anything, and before
    // it can send a number the server will not reject as already settled.
    assertEquals(Integer.valueOf(4), engine.fullState().getLastProcessedSeq().get("p1"));
  }

  @Test
  public void aPublishedStateKeepsTheAcknowledgementsItWasPublishedWith() {
    join("p1");
    // A rule that hands back a new state object each tick is what makes "the state it arrived with"
    // a thing a client can hold on to at all.
    rule.returnFreshStateEachTick();
    start();
    engine.addPlayerMove("p1", numberedMoves(4));
    engine.calculateNextState();
    GameState<Object> published = engine.getCurrentState();

    engine.addPlayerMove("p1", numberedMoves(5));
    engine.calculateNextState();

    // A state is handed to the broadcaster and serialised on another thread while the tick thread
    // keeps going. Publishing the live map would let a client read a watermark that does not match
    // the positions in the payload it arrived with, and drop input that snapshot had not applied.
    assertEquals(Integer.valueOf(4), published.getLastProcessedSeq().get("p1"));
    assertEquals(Integer.valueOf(5), acknowledged("p1"));
  }

  @Test
  public void theTickCounterAdvancesByOnePerTick() {
    join("p1");
    start();

    engine.calculateNextState();
    engine.calculateNextState();
    engine.calculateNextState();

    assertEquals(Integer.valueOf(3), engine.getCurrentState().getTick());
    assertEquals(3, callback.rounds.get());
  }
}
