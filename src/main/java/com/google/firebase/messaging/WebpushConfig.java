/*
 * Copyright 2018 Google Inc.
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
import com.google.firebase.internal.NonNull;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the Webpush protocol options that can be included in a {@link Message}. Instances
 * of this class are thread-safe and immutable.
 */
public class WebpushConfig {

  @Key("headers")
  private final Map<String, String> headers;

  @Key("data")
  private final Map<String, String> data;

  @Key("notification")
  private final Map<String, Object> notification;

  private WebpushConfig(Builder builder) {
    this.headers = builder.headers.isEmpty() ? null : ImmutableMap.copyOf(builder.headers);
    this.data = builder.data.isEmpty() ? null : ImmutableMap.copyOf(builder.data);
    this.notification = builder.notification != null ? builder.notification.getFields() : null;
  }

  /**
   * Creates a new {@link WebpushConfig.Builder}.
   *
   * @return A {@link WebpushConfig.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private final Map<String, String> headers = new HashMap<>();
    private final Map<String, String> data = new HashMap<>();
    private WebpushNotification notification;

    private Builder() {}

    /**
     * Adds the given key-value pair as a Webpush HTTP header. Refer to
     * <a href="https://tools.ietf.org/html/rfc8030#section-5">Webpush specification</a>
     * for supported headers.
     *
     * @param key Name of the header. Must not be null.
     * @param value Value of the header. Must not be null.
     * @return This builder.
     */
    public Builder putHeader(@NonNull String key, @NonNull String value) {
      headers.put(key, value);
      return this;
    }

    /**
     * Adds all the key-value pairs in the given map as Webpush headers. Refer to
     * <a href="https://tools.ietf.org/html/rfc8030#section-5">Webpush specification</a>
     * for supported headers.
     *
     * @param map A non-null map of header values. Map must not contain null keys or values.
     * @return This builder.
     */
    public Builder putAllHeaders(@NonNull Map<String, String> map) {
      headers.putAll(map);
      return this;
    }

    /**
     * Sets the given key-value pair as a Webpush data field. When set, overrides any data fields
     * set using the methods {@link Message.Builder#putData(String, String)} and
     * {@link Message.Builder#putAllData(Map)}.
     *
     * @param key Name of the data field. Must not be null.
     * @param value Value of the data field. Must not be null.
     * @return This builder.
     */
    public Builder putData(String key, String value) {
      data.put(key, value);
      return this;
    }

    /**
     * Adds all the key-value pairs in the given map as Webpush data fields. When set, overrides any
     * data fields set using the methods {@link Message.Builder#putData(String, String)} and
     * {@link Message.Builder#putAllData(Map)}.
     *
     * @param map A non-null map of data values. Map must not contain null keys or values.
     * @return This builder.
     */
    public Builder putAllData(Map<String, String> map) {
      data.putAll(map);
      return this;
    }

    /**
     * Sets the Webpush notification to be included in the message.
     *
     * @param notification A {@link WebpushNotification} instance.
     * @return This builder.
     */
    public Builder setNotification(WebpushNotification notification) {
      this.notification = notification;
      return this;
    }

    /**
     * Creates a new {@link WebpushConfig} instance from the parameters set on this builder.
     *
     * @return A new {@link WebpushConfig} instance.
     * @throws IllegalArgumentException If any of the parameters set on the builder are invalid.
     */
    public WebpushConfig build() {
      return new WebpushConfig(this);
    }
  }
}
