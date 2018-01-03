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
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;

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
      this.priority = builder.priority.name();
    } else {
      this.priority = null;
    }
    if (builder.ttl != null) {
      checkArgument(builder.ttl.endsWith("s"), "ttl must end with 's'");
      String numeric = builder.ttl.substring(0, builder.ttl.length() - 1);
      checkArgument(numeric.matches("[0-9.\\-]*"), "malformed ttl string");
      try {
        checkArgument(Double.parseDouble(numeric) >= 0, "ttl must not be negative");
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("invalid ttl value", e);
      }
    }
    this.ttl = builder.ttl;
    this.restrictedPackageName = builder.restrictedPackageName;
    this.data = builder.data.isEmpty() ? null : ImmutableMap.copyOf(builder.data);
    this.notification = builder.notification;
  }

  public enum Priority {
    high,
    normal,
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private String collapseKey;
    private Priority priority;
    private String ttl;
    private String restrictedPackageName;
    private final Map<String, String> data = new HashMap<>();
    private AndroidNotification notification;

    private Builder() {

    }

    public Builder setCollapseKey(String collapseKey) {
      this.collapseKey = collapseKey;
      return this;
    }

    public Builder setPriority(Priority priority) {
      this.priority = priority;
      return this;
    }

    public Builder setTtl(String ttl) {
      this.ttl = ttl;
      return this;
    }

    public Builder setRestrictedPackageName(String restrictedPackageName) {
      this.restrictedPackageName = restrictedPackageName;
      return this;
    }

    public Builder putData(String key, String value) {
      this.data.put(key, value);
      return this;
    }

    public Builder putAllData(Map<String, String> data) {
      this.data.putAll(data);
      return this;
    }

    public Builder setNotification(AndroidNotification notification) {
      this.notification = notification;
      return this;
    }

    public AndroidConfig build() {
      return new AndroidConfig(this);
    }
  }
}
