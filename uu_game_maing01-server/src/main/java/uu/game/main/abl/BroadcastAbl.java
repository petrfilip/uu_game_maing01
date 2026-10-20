package uu.game.main.abl;

import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import uu.game.main.abl.dto.ObjectHolder;
import uu.game.main.abl.dto.event.GameEvent;
import uu.game.main.abl.dto.event.GameServerEvent;
import uu.game.main.abl.dto.event.RoomEvent;
import uu.game.main.api.dto.PollDtoIn;
import uu.game.main.api.dto.PollDtoOut;
import uu.game.main.domain.GameState;
import uu.game.main.metrics.BroadcastMetrics;

/**
 * Fans game and room events out to clients, over SSE or long-polling.
 *
 * <p>Three things changed here, all of which were visible to players:
 * <ul>
 *   <li><b>Subscriptions are per room.</b> Emitters used to live in one server-wide collection and
 *       the {@code roomId} the client sent was ignored, so every client received the full state of
 *       every game in progress - wasted bandwidth, and anyone could watch a game they had not
 *       joined.</li>
 *   <li><b>Only members may watch.</b> Scoping by room stopped the accidental fan-out but not the
 *       deliberate one: a room id is in the page URL, so without a membership check it is a bearer
 *       token for someone else's match. See {@link RoomAbl#requireMember}.</li>
 *   <li><b>No lock is held across I/O.</b> Dispatch used to run inside a single global
 *       {@code synchronized} block that also performed the network writes, so one slow client
 *       blocked every room's simulation.</li>
 * </ul>
 */
@Component
public class BroadcastAbl implements ApplicationListener<GameServerEvent> {

  private static final Logger LOGGER = LogManager.getLogger(BroadcastAbl.class);

  /**
   * How long a long-poll request waits before returning empty. Without a bound, a request thread
   * parks forever on a room that never produces an event.
   */
  private static final long POLL_TIMEOUT_SECONDS = 30;

  private final Map<String, Set<SseSubscriber>> subscribers = new ConcurrentHashMap<>();
  private final Map<String, Queue<ObjectHolder<PollDtoOut>>> pollHolders = new ConcurrentHashMap<>();

  /**
   * Owned here rather than injected, so that a subscriber always has somewhere to report a loss -
   * including in the tests that build this component field by field. Counting a dropped frame must
   * never depend on the wiring being complete.
   */
  private final BroadcastMetrics broadcastMetrics = new BroadcastMetrics();

  /**
   * Used only to render outbound frames; it is deliberately not the Spring-configured mapper,
   * because what goes onto an SSE stream must not change when request mapping is reconfigured.
   */
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Inject
  private RoomAbl roomAbl;

  private ExecutorService dispatcher;

  @Value("${game.broadcast.dispatcherThreads:8}")
  private int dispatcherThreads = 8;

  private synchronized ExecutorService dispatcher() {
    if (dispatcher == null) {
      AtomicInteger counter = new AtomicInteger();
      ThreadFactory threadFactory = runnable -> {
        Thread thread = new Thread(runnable, "sse-dispatch-" + counter.incrementAndGet());
        thread.setDaemon(true);
        return thread;
      };
      dispatcher = Executors.newFixedThreadPool(dispatcherThreads, threadFactory);
    }
    return dispatcher;
  }

  public PollDtoOut poll(String awid, String playerId, PollDtoIn dtoIn) {
    // Long-polling carries exactly the same state as the SSE stream, so it has to be gated the same
    // way; closing one door and leaving the other open would have been no change at all.
    roomAbl.requireMember(awid, dtoIn.getRoomId(), playerId);

    // For a polling client this request is the only sign of life there is - there is no stream whose
    // closing the room could notice. Without this the disconnect grace would take their slot
    // mid-match, exactly as it would for a player whose stream died.
    roomAbl.playerConnected(awid, dtoIn.getRoomId(), playerId);

    if (dtoIn.isFull()) {
      // The same hand-off subscribe() gives a new stream. A client that only ever received the
      // per-tick deltas would never see the arena, because those carry only what changed - it would
      // stay blank for the whole match. Answered immediately rather than held: the caller is asking
      // what the state *is*, not to be told when it next changes.
      PollDtoOut current = currentStateEvent(awid, dtoIn.getRoomId());
      if (current != null) {
        return current;
      }
    }

    String key = key(awid, dtoIn.getRoomId());
    ObjectHolder<PollDtoOut> holder = new ObjectHolder<>();
    Queue<ObjectHolder<PollDtoOut>> holders =
      pollHolders.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
    holders.add(holder);

    PollDtoOut result = holder.get(POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    if (result == null) {
      // Timed out: drop our slot so the queue does not grow with abandoned requests.
      holders.remove(holder);
    }
    return result;
  }

  @Override
  public void onApplicationEvent(GameServerEvent event) {
    String key = key(event.getAwid(), event.getRoomId());
    PollDtoOut dtoOut = new PollDtoOut(event.getOutput(), event.getClass().getSimpleName());

    releasePollHolders(key, dtoOut);
    notifySubscribers(key, dtoOut);
  }

  private void releasePollHolders(String key, PollDtoOut dtoOut) {
    Queue<ObjectHolder<PollDtoOut>> holders = pollHolders.get(key);
    if (holders == null) {
      return;
    }
    // Drain rather than iterate-then-clear: a concurrent poll() adding a holder mid-dispatch would
    // otherwise be cleared away without ever being served.
    ObjectHolder<PollDtoOut> holder;
    while ((holder = holders.poll()) != null) {
      holder.set(dtoOut);
    }
  }

  private void notifySubscribers(String key, PollDtoOut dtoOut) {
    Set<SseSubscriber> room = subscribers.get(key);
    if (room == null || room.isEmpty()) {
      return;
    }
    Object payload = serialiseOnce(dtoOut);
    for (SseSubscriber subscriber : room) {
      if (subscriber.isDead()) {
        room.remove(subscriber);
        continue;
      }
      // Returns immediately; the actual write happens on the dispatcher pool.
      subscriber.enqueue(payload);
    }
  }

  /**
   * Turns a payload into the JSON every subscriber of the room will receive, once.
   *
   * <p>{@link SseEmitter#send(Object)} serialises on the emitter, so handing the same object to a
   * room of subscribers serialised the identical state once per person watching - and a state is
   * published twenty times a second. A pre-serialised string is written through unchanged, so every
   * client still receives exactly the same bytes as before.
   *
   * <p>If serialisation fails the object is passed through instead: each emitter will then fail on
   * its own, which is the behaviour there was before, rather than the room losing the frame.
   */
  private Object serialiseOnce(PollDtoOut dtoOut) {
    try {
      return OBJECT_MAPPER.writeValueAsString(dtoOut);
    } catch (Exception e) {
      LOGGER.error("Cannot serialise a broadcast payload; falling back to per-subscriber writes", e);
      return dtoOut;
    }
  }

  public void subscribe(String awid, String roomId, String playerId, SseEmitter emitter) {
    // Before the subscriber exists, so a stranger cannot even cost us a queue and a connection.
    roomAbl.requireMember(awid, roomId, playerId);

    String key = key(awid, roomId);
    SseSubscriber subscriber = new SseSubscriber(playerId, emitter, dispatcher(), broadcastMetrics);
    // CopyOnWriteArraySet: reads (dispatch) vastly outnumber writes (connect/disconnect) and
    // iteration is safe without a lock, unlike the synchronizedCollection + forEach it replaces.
    subscribers.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>()).add(subscriber);

    // Opening a stream is the clearest possible sign the player is still here, so it cancels a
    // removal armed by a previous stream of theirs closing - the refresh case.
    roomAbl.playerConnected(awid, roomId, playerId);

    emitter.onCompletion(() -> unsubscribe(awid, roomId, subscriber));
    emitter.onTimeout(() -> unsubscribe(awid, roomId, subscriber));

    sendCurrentState(awid, roomId, subscriber);

    LOGGER.debug("SSE subscribed to room {}", roomId);
  }

  /**
   * Hands a brand new subscriber everything it needs to draw the match.
   *
   * <p>Without it a client that connects mid-match sees nothing until the next tick, and since the
   * per-tick payload carries only what changed it would never receive the arena at all - it would
   * stay blank for the rest of the game.
   */
  private void sendCurrentState(String awid, String roomId, SseSubscriber subscriber) {
    PollDtoOut current = currentStateEvent(awid, roomId);
    if (current != null) {
      subscriber.enqueue(current);
    }
  }

  /**
   * Builds the full-state event, or null when there is nothing to hand over yet.
   *
   * <p>Shared by both delivery channels on purpose: a long-polling client needs exactly the same
   * hand-off a stream gets, and letting the two build it separately is how they drift apart.
   *
   * @return the full state in the envelope tick events use, or null if it cannot be built
   */
  private PollDtoOut currentStateEvent(String awid, String roomId) {
    Object snapshot;
    try {
      snapshot = roomAbl.currentSnapshot(awid, roomId);
    } catch (RuntimeException e) {
      // A snapshot that cannot be built is not a reason to refuse the subscription; the next tick
      // will carry the client along.
      LOGGER.warn("Could not build the initial state of room {}", roomId, e);
      return null;
    }
    if (snapshot == null) {
      return null;
    }
    // Same envelope the tick events use, so the client has one code path for both.
    String eventType = snapshot instanceof GameState
      ? GameEvent.class.getSimpleName() : RoomEvent.class.getSimpleName();
    return new PollDtoOut(snapshot, eventType);
  }

  private void unsubscribe(String awid, String roomId, SseSubscriber subscriber) {
    String key = key(awid, roomId);
    Set<SseSubscriber> room = subscribers.get(key);
    if (room != null) {
      room.remove(subscriber);
      if (!hasLiveSubscriber(room, subscriber.getPlayerId())) {
        // Their last stream for this room is gone. Armed rather than applied - a second tab or a
        // reconnect within the grace period calls it off again.
        roomAbl.playerDisconnected(awid, roomId, subscriber.getPlayerId());
      }
      if (room.isEmpty()) {
        subscribers.remove(key, room);
      }
    }
    subscriber.die(null);
  }

  /** A player may legitimately have several streams open - two tabs, or a reconnect that overlapped. */
  private boolean hasLiveSubscriber(Set<SseSubscriber> room, String playerId) {
    if (playerId == null) {
      return false;
    }
    for (SseSubscriber other : room) {
      if (!other.isDead() && playerId.equals(other.getPlayerId())) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return number of live subscribers of a room; exposed for tests and diagnostics.
   */
  public int subscriberCount(String awid, String roomId) {
    Collection<SseSubscriber> room = subscribers.get(key(awid, roomId));
    return room == null ? 0 : room.size();
  }

  /**
   * How many SSE streams are open across every room.
   *
   * <p>Counted by walking the rooms rather than kept in a counter next to the map: the map is the
   * truth, and a second number maintained alongside it is a number that will eventually disagree
   * with it. There are as many rooms as there are matches, so the walk is trivial.
   */
  public int subscriberCount() {
    int total = 0;
    for (Set<SseSubscriber> room : subscribers.values()) {
      total += room.size();
    }
    return total;
  }

  /** Rooms with at least one open stream, i.e. rooms somebody is actually watching. */
  public int roomsWithSubscribers() {
    return subscribers.size();
  }

  /** What the fan-out has lost: see {@link BroadcastMetrics}. */
  public BroadcastMetrics getBroadcastMetrics() {
    return broadcastMetrics;
  }

  private String key(String awid, String roomId) {
    return awid + "/" + roomId;
  }

  @PreDestroy
  public void shutdown() {
    subscribers.values().forEach(room -> room.forEach(subscriber -> subscriber.die(null)));
    subscribers.clear();

    ExecutorService executor = dispatcher;
    if (executor == null) {
      return;
    }
    executor.shutdown();
    try {
      if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
