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
import com.google.firebase.internal.NonNull;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the APNS-specific options that can be included in a {@link Message}.
 * Instances of this class are thread-safe and immutable.
 */
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

  /**
   * Creates a new {@link ApnsConfig.Builder}.
   *
   * @return A {@link ApnsConfig.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private final Map<String, String> headers = new HashMap<>();
    private Map<String, Object> payload;

    /**
     * Sets the given key-value pair as an APNS header.
     *
     * @param key Name of the header field. Must not be null.
     * @param value Value of the header field. Must not be null.
     * @return This builder.
     */
    public Builder putHeader(@NonNull String key, @NonNull String value) {
      headers.put(key, value);
      return this;
    }

    /**
     * Adds all the key-value pairs in the given map as APNS headers.
     *
     * @param map A non-null map of headers. Map must not contain null keys or values.
     * @return This builder.
     */
    public Builder putAllHeaders(@NonNull Map<String, String> map) {
      headers.putAll(map);
      return this;
    }

    /**
     * Sets APNS payload as JSON-serializable map.
     *
     * @param payload Map containing both aps dictionary and custom payload.
     * @return This builder.
     */
    public Builder setPayload(Map<String, Object> payload) {
      this.payload = payload;
      return this;
    }

    /**
     * Creates a new {@link ApnsConfig} instance from the parameters set on this builder.
     *
     * @return A new {@link ApnsConfig} instance.
     * @throws IllegalArgumentException If any of the parameters set on the builder are invalid.
     */
    public ApnsConfig build() {
      return new ApnsConfig(this);
    }
  }
}
