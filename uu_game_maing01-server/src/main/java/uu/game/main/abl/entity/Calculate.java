package uu.game.main.abl.entity;

import java.time.ZonedDateTime;
import java.util.List;
import uu.app.objectstore.mongodb.domain.AbstractUuObject;

public class Calculate extends AbstractUuObject {

  private ZonedDateTime key;
  private List<Integer> valueList;

  public ZonedDateTime getKey() {
    return key;
  }

  public void setKey(ZonedDateTime key) {
    this.key = key;
  }

  public List<Integer> getValueList() {
    return valueList;
  }

  public void setValueList(List<Integer> valueList) {
    this.valueList = valueList;
  }
}
