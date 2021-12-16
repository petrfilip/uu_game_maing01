package uu.game.main.api.dto.job;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import uu.app.authentication.Session;

public class Signal {

  private String code;
  private ZonedDateTime ts;
  private String uuIdentity;

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public ZonedDateTime getTs() {
    return ts;
  }

  public void setTs(ZonedDateTime ts) {
    this.ts = ts;
  }

  public String getUuIdentity() {
    return uuIdentity;
  }

  public void setUuIdentity(String uuIdentity) {
    this.uuIdentity = uuIdentity;
  }

  /**
   * Helper method for instance construction.
   */
  public static Signal of(String code, Session session) {
    Signal signal = new Signal();
    signal.setCode(code);
    signal.setTs(ZonedDateTime.now(ZoneOffset.UTC));
    signal.setUuIdentity(session.getIdentity().getUUIdentity());
    return signal;
  }
}
