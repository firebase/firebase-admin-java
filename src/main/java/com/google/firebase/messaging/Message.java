/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
  private final Map<String, String> data;

  @Key("notification")
  private final Notification notification;

  @Key("android")
  private final AndroidConfig androidConfig;

  @Key("webpush")
  private final WebpushConfig webpushConfig;

  @Key("apns")
  private final ApnsConfig apnsConfig;

  @Key("token")
  private final String token;

  @Key("topic")
  private final String topic;

  @Key("condition")
  private final String condition;

  private Message(Builder builder) {
    this.data = builder.data.isEmpty() ? null : ImmutableMap.copyOf(builder.data);
    this.notification = builder.notification;
    this.androidConfig = builder.androidConfig;
    this.webpushConfig = builder.webpushConfig;
    this.apnsConfig = builder.apnsConfig;
    int count = Booleans.countTrue(
        !Strings.isNullOrEmpty(builder.token),
        !Strings.isNullOrEmpty(builder.topic),
        !Strings.isNullOrEmpty(builder.condition)
    );
    checkArgument(count == 1, "Exactly one of token, topic or condition must be specified");
    if (builder.topic != null) {
      checkArgument(!builder.topic.startsWith("/topics/"),
          "Topic name must not contain the /topics/ prefix");
      checkArgument(builder.topic.matches("[a-zA-Z0-9-_.~%]+"), "Malformed topic name");
    }
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
    private WebpushConfig webpushConfig;
    private ApnsConfig apnsConfig;
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

    public Builder setWebpushConfig(WebpushConfig webpushConfig) {
      this.webpushConfig = webpushConfig;
      return this;
    }

    public Builder setApnsConfig(ApnsConfig apnsConfig) {
      this.apnsConfig = apnsConfig;
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
