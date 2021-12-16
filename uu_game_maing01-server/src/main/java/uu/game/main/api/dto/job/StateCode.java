package uu.game.main.api.dto.job;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public enum StateCode {
  SUBMITTED, SCHEDULED, ALLOCATED, STARTED, COMPLETED, FAILED, CANCELED;

  public static final Set<StateCode> FINAL_STATES = initStateSet(COMPLETED, CANCELED, FAILED);
  public static final Set<StateCode> ACTIVE_STATES = initStateSet(SUBMITTED, SCHEDULED, ALLOCATED, STARTED);
  public static final Set<StateCode> ACTIVE_NOTSTARTED_STATES = initStateSet(SUBMITTED, SCHEDULED, ALLOCATED);
  public static final Set<StateCode> CANCELABLE_SATES = initStateSet(SUBMITTED, SCHEDULED, ALLOCATED);

  @JsonValue
  public String toJson() {
    return this.name();
  }


  private static Set<StateCode> initStateSet(StateCode... states) {
    Set<StateCode> activeStates = new HashSet<>(states.length, 1);
    Collections.addAll(activeStates, states);
    return Collections.unmodifiableSet(activeStates);
  }

}
