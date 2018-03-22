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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.api.client.util.Key;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.internal.NonNull;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the APNS-specific options that can be included in a {@link Message}.
 * Instances of this class are thread-safe and immutable. Refer to
 * <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/APNSOverview.html">
 * Apple documentation</a> for various headers and payload fields supported by APNS.
 */
public class ApnsConfig {

  @Key("headers")
  private final Map<String, String> headers;

  @Key("payload")
  private final Map<String, Object> payload;

  private ApnsConfig(Builder builder) {
    checkArgument(builder.aps != null, "aps must be specified");
    checkArgument(!builder.customData.containsKey("aps"),
        "aps cannot be specified as part of custom data");
    this.headers = builder.headers.isEmpty() ? null : ImmutableMap.copyOf(builder.headers);
    this.payload = ImmutableMap.<String, Object>builder()
        .putAll(builder.customData)
        .put("aps", builder.aps.getFields())
        .build();
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
    private final Map<String, Object> customData = new HashMap<>();
    private Aps aps;

    private Builder() {}

    /**
     * Adds the given key-value pair as an APNS header.
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
     * Sets the aps dictionary of the APNS message.
     *
     * @param aps A non-null instance of {@link Aps}.
     * @return This builder.
     */
    public Builder setAps(@NonNull Aps aps) {
      this.aps = aps;
      return this;
    }

    /**
     * Adds the given key-value pair as an APNS custom data field.
     *
     * @param key Name of the data field. Must not be null.
     * @param value Value of the data field. Must not be null.
     * @return This builder.
     */
    public Builder putCustomData(@NonNull String key, @NonNull Object value) {
      this.customData.put(key, value);
      return this;
    }

    /**
     * Adds all the key-value pairs in the given map as APNS custom data fields.
     *
     * @param map A non-null map. Map must not contain null keys or values.
     * @return This builder.
     */
    public Builder putAllCustomData(@NonNull Map<String, Object> map) {
      this.customData.putAll(map);
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
