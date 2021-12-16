package uu.game.main.api.dto.job;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO contains basic information about job, which should be created.
 */
public class CreateJobDtoIn {

  public static final ZonedDateTime DEFAULT_NOT_AFTER_TIME = Instant.ofEpochMilli(Long.MAX_VALUE).atZone(ZoneOffset.UTC);

  private String code;
  private String queueCode;
  private ZonedDateTime notBeforeTime;
  private ZonedDateTime notAfterTime = DEFAULT_NOT_AFTER_TIME;
  private CallParams call;
  private CallParams callback;
  private AuthenticationCredentials authentication;
  private List<Long> invokeRetryAfter = new ArrayList<>();

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getQueueCode() {
    return queueCode;
  }

  public void setQueueCode(String channelCode) {
    this.queueCode = channelCode;
  }

  public ZonedDateTime getNotBeforeTime() {
    return notBeforeTime;
  }

  public void setNotBeforeTime(ZonedDateTime notBeforeTime) {
    this.notBeforeTime = notBeforeTime;
  }

  public ZonedDateTime getNotAfterTime() {
    return notAfterTime;
  }

  public void setNotAfterTime(ZonedDateTime notAfterTime) {
    this.notAfterTime = notAfterTime;
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

  public List<Long> getInvokeRetryAfter() {
    return invokeRetryAfter;
  }

  public void setInvokeRetryAfter(List<Long> invokeRetryAfter) {
    this.invokeRetryAfter = invokeRetryAfter;
  }
}
