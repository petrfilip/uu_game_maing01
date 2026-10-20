package uu.game.main.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import uu.game.main.abl.dto.Player;
import uu.game.main.domain.GameState;
import uu.game.main.domain.GameStateEnum;
import uu.game.main.domain.IRule;
import uu.game.main.game.common.GameArea;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.GameSetup;
import uu.game.main.game.common.Obstacle;
import uu.game.main.game.common.ObstacleTypeEnum;

/**
 * A rule that does nothing but record what the engine handed it.
 *
 * <p>Deliberately not a game: the point of most engine tests is that the engine is game agnostic.
 */
public class FakeRule implements IRule<String, FakeMove> {

  private final AtomicInteger ticks = new AtomicInteger();
  private final AtomicInteger movesSeen = new AtomicInteger();
  private final List<Object> lastMoves = new CopyOnWriteArrayList<>();

  private volatile int finishAtTick = Integer.MAX_VALUE;
  private volatile int throwOnTick = -1;
  private volatile int errorOnTick = -1;
  private volatile boolean freshStateEachTick;
  private volatile GameRuleEvent eventEachTick;

  /** Stands in for a real game's level geometry: something static the engine has to publish once. */
  private volatile GameSetup setup;

  @Override
  public Class<FakeMove> getMoveClass() {
    return FakeMove.class;
  }

  @Override
  public GameSetup getSetup() {
    return setup;
  }

  @Override
  public GameState<String> init(GameState<String> currentState, Collection<Player> players) {
    currentState.setGame("board");
    currentState.setState(GameStateEnum.RUNNING);
    currentState.setTick(0);
    setup = new GameSetup(new GameArea(320, 240),
      Collections.singletonList(new Obstacle(ObstacleTypeEnum.WALL, 10, 20, 30, 40)),
      currentState.getParams());
    return currentState;
  }

  @Override
  public GameState<String> getNextGameState(GameState<String> state, Map<Player, List<FakeMove>> unprocessedMoves) {
    int tick = ticks.incrementAndGet();
    if (tick == throwOnTick) {
      throw new IllegalStateException("rule blew up on purpose");
    }
    if (tick == errorOnTick) {
      // Not an Exception: a rule can fail in ways a catch of Exception never sees.
      throw new StackOverflowError("rule blew up on purpose, harder");
    }

    List<Object> seen = new ArrayList<>();
    for (List<FakeMove> moves : unprocessedMoves.values()) {
      movesSeen.addAndGet(moves.size());
      seen.addAll(moves);
    }
    lastMoves.clear();
    lastMoves.addAll(seen);

    if (state.getTick() + 1 >= finishAtTick) {
      state.setState(GameStateEnum.FINISHED);
    }
    GameState<String> next = state;
    if (freshStateEachTick) {
      next = new GameState<>();
      next.setTick(state.getTick());
      next.setState(state.getState());
      next.setParams(state.getParams());
      next.setGame(state.getGame());
    }
    if (eventEachTick != null) {
      next.setGameEvents(eventEachTick.asList());
    }
    return next;
  }

  /** Makes every tick report something happened, so a test can ask who gets told about it. */
  public void emitEventEachTick(GameRuleEvent event) {
    this.eventEachTick = event;
  }

  /** Makes the given tick fail with an {@link Error} rather than an exception. */
  public void throwErrorOnTick(int tick) {
    this.errorOnTick = tick;
  }

  /**
   * Makes the rule hand back a brand new state object, which {@link IRule} allows. Anything the
   * engine wants published on every state has to be attached by the engine itself.
   */
  void returnFreshStateEachTick() {
    this.freshStateEachTick = true;
  }

  void finishAtTick(int tick) {
    this.finishAtTick = tick;
  }

  void throwOnTick(int tick) {
    this.throwOnTick = tick;
  }

  int getTicks() {
    return ticks.get();
  }

  int getMovesSeen() {
    return movesSeen.get();
  }

  List<Object> getLastMoves() {
    return new ArrayList<>(lastMoves);
  }
}
