package com.google.firebase.messaging;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.api.client.util.Key;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Booleans;
import java.util.HashMap;
import java.util.Map;

public class Message {

  @Key("data")
  private Map<String, String> data;

  @Key("notification")
  private Notification notification;

  @Key("android")
  private AndroidConfig androidConfig;

  @Key("token")
  private String token;

  @Key("topic")
  private String topic;

  @Key("condition")
  private String condition;

  private Message(Builder builder) {
    this.data = builder.data.isEmpty() ? null : ImmutableMap.copyOf(builder.data);
    this.notification = builder.notification;
    this.androidConfig = builder.androidConfig;
    int count = Booleans.countTrue(
        !Strings.isNullOrEmpty(builder.token),
        !Strings.isNullOrEmpty(builder.topic),
        !Strings.isNullOrEmpty(builder.condition)
    );
    checkArgument(count == 1, "Exactly one of token, topic or condition must be specified");
    this.token = builder.token;
    this.topic = builder.topic;
    this.condition = builder.condition;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class  Builder {
    private final Map<String, String> data = new HashMap<>();
    private Notification notification;
    private AndroidConfig androidConfig;
    private String token;
    private String topic;
    private String condition;

    private Builder() {

    }

    public Builder setNotification(Notification notification) {
      this.notification = notification;
      return this;
    }

    public Builder setAndroidConfig(AndroidConfig androidConfig) {
      this.androidConfig = androidConfig;
      return this;
    }

    public Builder setToken(String token) {
      this.token = token;
      return this;
    }

    public Builder setTopic(String topic) {
      this.topic = topic;
      return this;
    }

    public Builder setCondition(String condition) {
      this.condition = condition;
      return this;
    }

    public Builder putData(String key, String value) {
      this.data.put(key, value);
      return this;
    }

    public Builder putAllData(Map<String, String> map) {
      this.data.putAll(map);
      return this;
    }

    public Message build() {
      return new Message(this);
    }
  }

}
