package uu.game.main.game.common;

public class GameRuleEvent {

  private String action;

  private String objectName;

  private Object data;

  public GameRuleEvent() {
  }

  public GameRuleEvent(String action, String objectName, Object data) {
    this.action = action;
    this.objectName = objectName;
    this.data = data;
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

  public Object getData() {
    return data;
  }

  public void setData(Object data) {
    this.data = data;
  }
}
