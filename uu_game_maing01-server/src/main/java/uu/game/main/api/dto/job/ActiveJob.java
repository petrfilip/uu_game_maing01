package uu.game.main.api.dto.job;

import java.time.ZonedDateTime;
import uu.app.objectstore.mongodb.domain.AbstractUuObject;

public class ActiveJob extends AbstractUuObject {

  private String code;
  private String queueCode;
  private ZonedDateTime notBeforeTime;
  private ZonedDateTime notAfterTime;
  private String brokerBaseUri;
  private CallParams call;
  private CallParams callback;
  private Object callResult;
  private AuthenticationCredentials authentication;
  private StateCode state;
  private Signal signal;
//  private List<StateCode> stateHistory = new ArrayList<>();
  private RetryOptions retryOptions = new RetryOptions();

  public ActiveJob() {
  }

  public ActiveJob(String awid, String id) {
    setAwid(awid);
    setId(id);
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getBrokerBaseUri() {
    return brokerBaseUri;
  }

  public void setBrokerBaseUri(String brokerBaseUri) {
    this.brokerBaseUri = brokerBaseUri;
  }

  public StateCode getState() {
    return state;
  }

  public void setState(StateCode state) {
    this.state = state;
  }
// public List<StateCode> getStateHistory() {
  //   return stateHistory;
  // }
  //
  // public void setStateHistory(List<StateCode> stateHistory) {
  //   this.stateHistory = stateHistory;
  // }

  public ZonedDateTime getNotBeforeTime() {
    return notBeforeTime;
  }

  public void setNotBeforeTime(ZonedDateTime notBeforeTime) {
    this.notBeforeTime = notBeforeTime;
  }

  public RetryOptions getRetryOptions() {
    return retryOptions;
  }

  public void setRetryOptions(RetryOptions retryOptions) {
    this.retryOptions = retryOptions;
  }

  public ZonedDateTime getNotAfterTime() {
    return notAfterTime;
  }

  public void setNotAfterTime(ZonedDateTime notAfterTime) {
    this.notAfterTime = notAfterTime;
  }

  public String getQueueCode() {
    return queueCode;
  }

  public void setQueueCode(String queueCode) {
    this.queueCode = queueCode;
  }

  public CallParams getCall() {
    return call;
  }

  public void setCall(CallParams call) {
    this.call = call;
  }

  public CallParams getCallback() {
    return callback;
  }

  public void setCallback(CallParams callback) {
    this.callback = callback;
  }

  public AuthenticationCredentials getAuthentication() {
    return authentication;
  }

  public void setAuthentication(AuthenticationCredentials authentication) {
    this.authentication = authentication;
  }

  public Object getCallResult() {
    return callResult;
  }

  public void setCallResult(Object callResult) {
    this.callResult = callResult;
  }


  public Signal getSignal() {
    return signal;
  }

  public void setSignal(Signal signal) {
    this.signal = signal;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("ActiveJob{");
    sb.append("id='").append(getId()).append('\'');
    sb.append("awid='").append(getAwid()).append('\'');
    sb.append("queueCode='").append(queueCode).append('\'');
    sb.append("code='").append(code).append('\'');
    sb.append(", notBeforeTime=").append(notBeforeTime);
    sb.append(", notAfterTime=").append(notAfterTime);
    sb.append(", brokerBaseUri=").append(brokerBaseUri);
    sb.append(", call=").append(call);
    sb.append(", callback=").append(callback);
    sb.append(", authentication=").append(authentication);
    sb.append(", state=").append(state);
//    sb.append(", stateHistory=").append(stateHistory);
    sb.append(", retryOptions=").append(retryOptions);
    sb.append('}');
    return sb.toString();
  }
}
