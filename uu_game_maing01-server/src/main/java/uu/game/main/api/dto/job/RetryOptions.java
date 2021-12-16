package uu.game.main.api.dto.job;

import java.util.List;

public class RetryOptions {

  private List<Long> invokeRetryAfter;
  private Integer invokeRetryIndex = -1; //last used retry index, -1 noretry occurred
  private Integer brokerRetryIndex = -1; //last used retry index, -1 noretry occurred

  public List<Long> getInvokeRetryAfter() {
    return invokeRetryAfter;
  }

  public void setInvokeRetryAfter(List<Long> invokeRetryAfter) {
    this.invokeRetryAfter = invokeRetryAfter;
  }

  public Integer getInvokeRetryIndex() {
    return invokeRetryIndex;
  }

  public void setInvokeRetryIndex(Integer invokeRetryIndex) {
    this.invokeRetryIndex = invokeRetryIndex;
  }

  public Integer getBrokerRetryIndex() {
    return brokerRetryIndex;
  }

  public void setBrokerRetryIndex(Integer brokerRetryIndex) {
    this.brokerRetryIndex = brokerRetryIndex;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("RetryOptions{");
    sb.append("invokeRetryAfter=").append(invokeRetryAfter);
    sb.append(", invokeRetryIndex=").append(invokeRetryIndex);
    sb.append(", brokerRetryIndex=").append(brokerRetryIndex);
    sb.append('}');
    return sb.toString();
  }
}
