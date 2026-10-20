package uu.game.main.game.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * One thing that happened during a tick: a shot, a hit, a death, a pickup.
 *
 * <p>An event says <em>what kind of thing</em> it concerns, <em>which one</em> where the thing has
 * an identity, and <em>where</em> it happened. Nothing else: all a client does with an event is
 * play a sound and draw an effect somewhere.
 *
 * <p>It used to carry a bare {@code Object data} into which whole domain objects were stuffed - a
 * death event serialised the entire player, a bullet hit carried a map holding both the projectile
 * and its victim. Every one of those fields is already in the same payload under {@code game}, so
 * they were sent twice, and the client ended up reading the server's class shapes rather than a
 * protocol.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GameRuleEvent {

  private String action;

  private String objectName;

  /**
   * Which one: a player id, the name of a picked up ability. Absent for things that have no
   * identity of their own - a bullet is not addressable, it is just a bullet.
   */
  private String id;

  private Double x;

  private Double y;

  public GameRuleEvent() {
  }

  public GameRuleEvent(String action, String objectName) {
    this(action, objectName, null, null, null);
  }

  public GameRuleEvent(String action, String objectName, String id) {
    this(action, objectName, id, null, null);
  }

  /**
   * @param where the thing the event is about; its centre is published, because that is the point a
   *     client draws an explosion or a hit marker on
   */
  public GameRuleEvent(String action, String objectName, String id, GameRectangle where) {
    this(action, objectName, id,
      where == null ? null : where.getCenterX(),
      where == null ? null : where.getCenterY());
  }

  public GameRuleEvent(String action, String objectName, String id, Double x, Double y) {
    this.action = action;
    this.objectName = objectName;
    this.id = id;
    this.x = x;
    this.y = y;
  }

  public List<GameRuleEvent> asList() {
    List<GameRuleEvent> eventList = new ArrayList<>();
    eventList.add(this);
    return eventList;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getObjectName() {
    return objectName;
  }

  public void setObjectName(String objectName) {
    this.objectName = objectName;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Double getX() {
    return x;
  }

  public void setX(Double x) {
    this.x = x;
  }

  public Double getY() {
    return y;
  }

  public void setY(Double y) {
    this.y = y;
  }

  @Override
  public String toString() {
    return "GameRuleEvent{" + action + " " + objectName + (id == null ? "" : " " + id) + '}';
  }
}
