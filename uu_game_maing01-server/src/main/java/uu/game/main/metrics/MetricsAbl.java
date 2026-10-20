package uu.game.main.metrics;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.springframework.stereotype.Component;
import uu.game.main.abl.BroadcastAbl;
import uu.game.main.api.dto.MetricsGameDtoOut;
import uu.game.main.api.dto.MetricsGetDtoOut;
import uu.game.main.engine.GameEngineRegistry;

/**
 * Assembles one reading of the server's health out of the three places that know anything about it:
 * the tick counters, the engine registry and the broadcast fan-out.
 *
 * <p>Nothing here is per workspace. It cannot be: {@link uu.game.main.engine.TickObserver} is handed
 * a game name and nothing else, so tick timings are inherently process wide, and a per-awid view of
 * only some of the numbers would be more misleading than an honest global one. The consequence is
 * that on a multi-tenant deployment this uuCmd tells one workspace's operators how busy the whole
 * process is, which is exactly why it is not {@code Public} - see
 * {@link uu.game.main.api.MetricsController}.
 */
@Component
public class MetricsAbl {

  @Inject
  private GameMetrics gameMetrics;

  @Inject
  private GameEngineRegistry gameEngineRegistry;

  @Inject
  private BroadcastAbl broadcastAbl;

  /**
   * Reads every counter once. Cheap enough to poll: the only loops are over the live rooms and over
   * one fixed-size bucket array per game, and none of them takes a lock or blocks a tick.
   */
  public MetricsGetDtoOut get() {
    MetricsGetDtoOut dtoOut = new MetricsGetDtoOut();
    dtoOut.setSinceMillis(gameMetrics.getSinceMillis());
    dtoOut.setTickMillis(gameEngineRegistry.getTickMillis());
    dtoOut.setLiveRooms(gameEngineRegistry.liveRoomCount());
    dtoOut.setRunningMatches(gameEngineRegistry.runningMatchCount());
    dtoOut.setSubscribers(broadcastAbl.subscriberCount());
    dtoOut.setRoomsWithSubscribers(broadcastAbl.roomsWithSubscribers());

    BroadcastMetrics broadcastMetrics = broadcastAbl.getBroadcastMetrics();
    dtoOut.setDroppedFrames(broadcastMetrics.getDroppedFrames());
    dtoOut.setFailedStreams(broadcastMetrics.getFailedStreams());

    List<GameTickSnapshot> snapshots = gameMetrics.snapshot();
    List<MetricsGameDtoOut> games = new ArrayList<>(snapshots.size());
    for (GameTickSnapshot snapshot : snapshots) {
      games.add(toDtoOut(snapshot));
    }
    dtoOut.setGames(games);
    return dtoOut;
  }

  private static MetricsGameDtoOut toDtoOut(GameTickSnapshot snapshot) {
    MetricsGameDtoOut dtoOut = new MetricsGameDtoOut();
    dtoOut.setGameName(snapshot.getGameName());
    dtoOut.setTicks(snapshot.getTicks());
    dtoOut.setOverruns(snapshot.getOverruns());
    dtoOut.setFailed(snapshot.getFailed());
    dtoOut.setP50Micros(snapshot.getP50Micros());
    dtoOut.setP90Micros(snapshot.getP90Micros());
    dtoOut.setP99Micros(snapshot.getP99Micros());
    dtoOut.setMaxMicros(snapshot.getMaxMicros());
    return dtoOut;
  }
}
