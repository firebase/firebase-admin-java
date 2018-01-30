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
import java.util.concurrent.TimeUnit;

/**
 * Represents the Android-specific options that can be included in a {@link Message}.
 * Instances of this class are thread-safe and immutable.
 */
public class AndroidConfig {

  @Key("collapse_key")
  private final String collapseKey;

  @Key("priority")
  private final String priority;

  @Key("ttl")
  private final String ttl;

  @Key("restricted_package_name")
  private final String restrictedPackageName;

  @Key("data")
  private final Map<String, String> data;

  @Key("notification")
  private final AndroidNotification notification;

  private AndroidConfig(Builder builder) {
    this.collapseKey = builder.collapseKey;
    if (builder.priority != null) {
      this.priority = builder.priority.name().toLowerCase();
    } else {
      this.priority = null;
    }
    if (builder.ttl != null) {
      checkArgument(builder.ttl >= 0, "ttl must not be negative");
      long seconds = TimeUnit.MILLISECONDS.toSeconds(builder.ttl);
      long subsecondNanos = TimeUnit.MILLISECONDS.toNanos(builder.ttl - seconds * 1000L);
      if (subsecondNanos > 0) {
        this.ttl = String.format("%d.%09ds", seconds, subsecondNanos);
      } else {
        this.ttl = String.format("%ds", seconds);
      }
    } else {
      this.ttl = null;
    }
    this.restrictedPackageName = builder.restrictedPackageName;
    this.data = builder.data.isEmpty() ? null : ImmutableMap.copyOf(builder.data);
    this.notification = builder.notification;
  }

  /**
   * Priority levels that can be set on an {@link AndroidConfig}.
   */
  public enum Priority {
    HIGH,
    NORMAL,
  }

  /**
   * Creates a new {@link AndroidConfig.Builder}.
   *
   * @return A {@link AndroidConfig.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private String collapseKey;
    private Priority priority;
    private Long ttl;
    private String restrictedPackageName;
    private final Map<String, String> data = new HashMap<>();
    private AndroidNotification notification;

    private Builder() {}

    /**
     * Sets a collapse key for the message. Collapse key serves as an identifier for a group of
     * messages that can be collapsed, so that only the last message gets sent when delivery can be
     * resumed. A maximum of 4 different collapse keys may be active at any given time.
     *
     * @param collapseKey A collapse key string.
     * @return This builder.
     */
    public Builder setCollapseKey(String collapseKey) {
      this.collapseKey = collapseKey;
      return this;
    }

    /**
     * Sets the priority of the message.
     *
     * @param priority A value from the {@link Priority} enum.
     * @return This builder.
     */
    public Builder setPriority(Priority priority) {
      this.priority = priority;
      return this;
    }

    /**
     * Sets the time-to-live duration of the message in milliseconds.
     *
     * @param ttl Time-to-live duration in milliseconds.
     * @return This builder.
     */
    public Builder setTtl(long ttl) {
      this.ttl = ttl;
      return this;
    }

    /**
     * Sets the package name of the application where the registration tokens must match in order
     * to receive the message.
     *
     * @param restrictedPackageName A package name string.
     * @return This builder.
     */
    public Builder setRestrictedPackageName(String restrictedPackageName) {
      this.restrictedPackageName = restrictedPackageName;
      return this;
    }

    /**
     * Adds the given key-value pair to the message as a data field. Key and the value may not be
     * null. When set, overrides any data fields set on the top-level {@link Message} via
     * {@link Message.Builder#putData(String, String)} and {@link Message.Builder#putAllData(Map)}.
     *
     * @param key Name of the data field. Must not be null.
     * @param value Value of the data field. Must not be null.
     * @return This builder.
     */
    public Builder putData(@NonNull String key, @NonNull String value) {
      this.data.put(key, value);
      return this;
    }

    /**
     * Adds all the key-value pairs in the given map to the message as data fields. None of the
     * keys and values may be null. When set, overrides any data fields set on the top-level
     * {@link Message} via {@link Message.Builder#putData(String, String)} and
     * {@link Message.Builder#putAllData(Map)}.
     *
     * @param map A non-null map of data fields. Map must not contain null keys or values.
     * @return This builder.
     */
    public Builder putAllData(@NonNull Map<String, String> map) {
      this.data.putAll(map);
      return this;
    }

    /**
     * Sets the Android notification to be included in the message.
     *
     * @param notification An {@link AndroidNotification} instance.
     * @return This builder.
     */
    public Builder setNotification(AndroidNotification notification) {
      this.notification = notification;
      return this;
    }

    /**
     * Creates a new {@link AndroidConfig} instance from the parameters set on this builder.
     *
     * @return A new {@link AndroidConfig} instance.
     * @throws IllegalArgumentException If any of the parameters set on the builder are invalid.
     */
    public AndroidConfig build() {
      return new AndroidConfig(this);
    }
  }
}
