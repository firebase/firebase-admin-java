package com.google.firebase.messaging;

import com.google.api.client.util.Key;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;

public class ApnsConfig {

  @Key("headers")
  private final Map<String, String> headers;

  @Key("payload")
  private final Map<String, Object> payload;

  private ApnsConfig(Builder builder) {
    this.headers = builder.headers.isEmpty() ? null : ImmutableMap.copyOf(builder.headers);
    this.payload = builder.payload == null || builder.payload.isEmpty()
        ? null : ImmutableMap.copyOf(builder.payload);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private final Map<String, String> headers = new HashMap<>();
    private Map<String, Object> payload;

    public Builder putHeader(String key, String value) {
      headers.put(key, value);
      return this;
    }

    public Builder putAllHeaders(Map<String, String> map) {
      headers.putAll(map);
      return this;
    }

    public Builder setPayload(Map<String, Object> payload) {
      this.payload = payload;
      return this;
    }

    public ApnsConfig build() {
      return new ApnsConfig(this);
    }
  }
}
