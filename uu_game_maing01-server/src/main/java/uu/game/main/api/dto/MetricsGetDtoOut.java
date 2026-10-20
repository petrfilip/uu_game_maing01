package uu.game.main.api.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Whether the server is keeping up, read from a running process.
 *
 * <p>Every number is a running total taken since {@code sinceMillis} rather than a rate, so that
 * two readings a minute apart give a rate without the server having to keep any windowed state on
 * the tick thread.
 */
public final class MetricsGetDtoOut {

  private long sinceMillis;
  private long tickMillis;
  private int liveRooms;
  private int runningMatches;
  private int subscribers;
  private int roomsWithSubscribers;
  private long droppedFrames;
  private long failedStreams;
  private List<MetricsGameDtoOut> games = new ArrayList<>();

  /** Epoch millisecond at which these counters started; subtract it from "now" for the window. */
  public long getSinceMillis() {
    return sinceMillis;
  }

  public void setSinceMillis(long sinceMillis) {
    this.sinceMillis = sinceMillis;
  }

  /** The configured tick period, i.e. the threshold a tick has to beat not to count as an overrun. */
  public long getTickMillis() {
    return tickMillis;
  }

  public void setTickMillis(long tickMillis) {
    this.tickMillis = tickMillis;
  }

  /** Rooms that still hold a live runtime, whether or not a match is in progress in them. */
  public int getLiveRooms() {
    return liveRooms;
  }

  public void setLiveRooms(int liveRooms) {
    this.liveRooms = liveRooms;
  }

  /** The subset of those rooms whose simulation is actually ticking. */
  public int getRunningMatches() {
    return runningMatches;
  }

  public void setRunningMatches(int runningMatches) {
    this.runningMatches = runningMatches;
  }

  /** SSE streams currently open, across every room. */
  public int getSubscribers() {
    return subscribers;
  }

  public void setSubscribers(int subscribers) {
    this.subscribers = subscribers;
  }

  public int getRoomsWithSubscribers() {
    return roomsWithSubscribers;
  }

  public void setRoomsWithSubscribers(int roomsWithSubscribers) {
    this.roomsWithSubscribers = roomsWithSubscribers;
  }

  /**
   * Frames thrown away because a subscriber's queue was full. Not an error - it is how a slow client
   * is kept from stalling the game - but it is the client seeing a match with gaps in it.
   */
  public long getDroppedFrames() {
    return droppedFrames;
  }

  public void setDroppedFrames(long droppedFrames) {
    this.droppedFrames = droppedFrames;
  }

  /** Streams closed because a write threw; normally just clients that went away. */
  public long getFailedStreams() {
    return failedStreams;
  }

  public void setFailedStreams(long failedStreams) {
    this.failedStreams = failedStreams;
  }

  public List<MetricsGameDtoOut> getGames() {
    return games;
  }

  public void setGames(List<MetricsGameDtoOut> games) {
    this.games = games;
  }

  @Override
  public String toString() {
    ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
    try {
      return ow.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      return super.toString();
    }
  }
}
