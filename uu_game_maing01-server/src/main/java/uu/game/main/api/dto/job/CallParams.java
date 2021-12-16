package uu.game.main.api.dto.job;

import java.util.Map;

public final class CallParams {

  private static final String DEFAULT_CMD_HTTP_METHOD = "POST";

  private String uri;
  private String httpMethod = DEFAULT_CMD_HTTP_METHOD;
  private Map<String, String> httpHeaderMap;
  private Map<String, Object> data;

  public String getUri() {
    return uri;
  }

  public void setUri(String uri) {
    this.uri = uri;
  }

  public String getHttpMethod() {
    return httpMethod;
  }

  public void setHttpMethod(String httpMethod) {
    this.httpMethod = httpMethod;
  }

  public Map<String, String> getHttpHeaderMap() {
    return httpHeaderMap;
  }

  public void setHttpHeaderMap(Map<String, String> httpHeaderMap) {
    this.httpHeaderMap = httpHeaderMap;
  }

  public Map<String, Object> getData() {
    return data;
  }

  public void setData(Map<String, Object> data) {
    this.data = data;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("CallParams{");
    sb.append("uri='").append(uri).append('\'');
    sb.append(", httpMethod='").append(httpMethod).append('\'');
    sb.append(", httpHeaderMap=").append(httpHeaderMap);
    sb.append(", data=").append(data);
    sb.append('}');
    return sb.toString();
  }
}
