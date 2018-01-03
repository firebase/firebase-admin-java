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

import com.google.api.client.util.Key;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;

public class WebpushConfig {

  @Key("headers")
  private final Map<String, String> headers;

  @Key("data")
  private final Map<String, String> data;

  @Key("notification")
  private final WebpushNotification notification;

  private WebpushConfig(Builder builder) {
    this.headers = builder.headers.isEmpty() ? null : ImmutableMap.copyOf(builder.headers);
    this.data = builder.data.isEmpty() ? null : ImmutableMap.copyOf(builder.data);
    this.notification = builder.notification;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private final Map<String, String> headers = new HashMap<>();
    private final Map<String, String> data = new HashMap<>();
    private WebpushNotification notification;

    public Builder putHeader(String key, String value) {
      headers.put(key, value);
      return this;
    }

    public Builder putAllHeaders(Map<String, String> map) {
      headers.putAll(map);
      return this;
    }

    public Builder putData(String key, String value) {
      data.put(key, value);
      return this;
    }

    public Builder putAllData(Map<String, String> map) {
      data.putAll(map);
      return this;
    }

    public Builder setNotification(WebpushNotification notification) {
      this.notification = notification;
      return this;
    }

    public WebpushConfig build() {
      return new WebpushConfig(this);
    }
  }
}
