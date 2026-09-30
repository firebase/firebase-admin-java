/*
 * Copyright 2026 Google LLC
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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.util.Key;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Booleans;
import com.google.firebase.internal.NonNull;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the Android-specific v2 options that can be included in a {@link Message}.
 * Instances of this class are thread-safe and immutable.
 */
public final class AndroidConfigV2 {

  @Key("collapse_key")
  private final String collapseKey;

  @Key("ttl")
  private final String ttl;

  @Key("restricted_package_name")
  private final String restrictedPackageName;

  @Key("data")
  private final Map<String, String> data;

  @Key("remote_notification")
  private final AndroidRemoteNotification remoteNotification;

  @Key("background_sync")
  private final AndroidBackgroundSyncMessage backgroundSync;

  @Key("fcm_options")
  private final AndroidFcmOptions fcmOptions;

  @Key("direct_boot_ok")
  private final Boolean directBootOk;

  @Key("bandwidth_constrained_ok")
  private final Boolean bandwidthConstrainedOk;

  @Key("restricted_satellite_ok")
  private final Boolean restrictedSatelliteOk;

  private AndroidConfigV2(Builder builder) {
    this.collapseKey = builder.collapseKey;
    if (builder.ttl != null) {
      checkArgument(!builder.ttl.isNegative(), "ttl must not be negative");
      long seconds = builder.ttl.getSeconds();
      long subsecondNanos = builder.ttl.getNano();
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

    int targets = Booleans.countTrue(
        builder.remoteNotification != null,
        builder.backgroundSync != null
    );
    checkArgument(targets == 1,
        "Exactly one of remoteNotification or backgroundSync must be specified");
    this.remoteNotification = builder.remoteNotification;
    this.backgroundSync = builder.backgroundSync;

    this.fcmOptions = builder.fcmOptions;
    this.directBootOk = builder.directBootOk;
    this.bandwidthConstrainedOk = builder.bandwidthConstrainedOk;
    this.restrictedSatelliteOk = builder.restrictedSatelliteOk;
  }

  /**
   * Creates a new {@link AndroidConfigV2.Builder}.
   *
   * @return An {@link AndroidConfigV2.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String collapseKey;
    private Duration ttl;
    private String restrictedPackageName;
    private final Map<String, String> data = new HashMap<>();
    private AndroidRemoteNotification remoteNotification;
    private AndroidBackgroundSyncMessage backgroundSync;
    private AndroidFcmOptions fcmOptions;
    private Boolean directBootOk;
    private Boolean bandwidthConstrainedOk;
    private Boolean restrictedSatelliteOk;

    private Builder() {}

    /**
     * Sets a collapse key for the message. The collapse key serves as an identifier for a group of
     * messages that can be collapsed, so that only the last message gets sent when delivery can be
     * resumed. A maximum of 4 different collapse keys may be active at any given time.
     * 
     * <p>By default, the collapse key is the app package name registered in
     * the Firebase console.</p>
     *
     * @param collapseKey A collapse key string.
     * @return This builder.
     */
    public Builder setCollapseKey(String collapseKey) {
      this.collapseKey = collapseKey;
      return this;
    }

    /**
     * Sets the time-to-live duration of the message.
     *
     * @param ttl Time-to-live duration.
     * @return This builder.
     */
    public Builder setTtl(Duration ttl) {
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
     * Sets the remote notification payload.
     * 
     * <p>Exactly one of remote notification or background sync must be specified. This setting is
     * mutually exclusive with {@link #setBackgroundSync(AndroidBackgroundSyncMessage)}.</p>
     *
     * @param remoteNotification Remote notification config.
     * @return This builder.
     */
    public Builder setRemoteNotification(AndroidRemoteNotification remoteNotification) {
      this.remoteNotification = remoteNotification;
      return this;
    }

    /**
     * Sets the background sync payload.
     * 
     * <p>Exactly one of remote notification or background sync must be specified. This setting is
     * mutually exclusive with {@link #setRemoteNotification(AndroidRemoteNotification)}.</p>
     *
     * @param backgroundSync Background sync config.
     * @return This builder.
     */
    public Builder setBackgroundSync(AndroidBackgroundSyncMessage backgroundSync) {
      this.backgroundSync = backgroundSync;
      return this;
    }

    /**
     * Sets the {@link AndroidFcmOptions}, which overrides values set in the {@link FcmOptions}
     * for Android messages.
     *
     * @param fcmOptions FCM options.
     * @return This builder.
     */
    public Builder setFcmOptions(AndroidFcmOptions fcmOptions) {
      this.fcmOptions = fcmOptions;
      return this;
    }

    /**
     * Sets the {@code direct_boot_ok} flag. If set to true, messages are delivered to 
     * the app while the device is in direct boot mode.
     *
     * @param directBootOk True to allow delivery in direct boot mode.
     * @return This builder.
     */
    public Builder setDirectBootOk(boolean directBootOk) {
      this.directBootOk = directBootOk;
      return this;
    }

    /**
     * Sets the {@code bandwidth_constrained_ok} flag. If set to true, messages will be allowed
     * to be delivered to the app while the device is on a bandwidth constrained network.
     *
     * @param bandwidthConstrainedOk True to allow delivery on bandwidth constrained networks.
     * @return This builder.
     */
    public Builder setBandwidthConstrainedOk(boolean bandwidthConstrainedOk) {
      this.bandwidthConstrainedOk = bandwidthConstrainedOk;
      return this;
    }

    /**
     * Sets the {@code restricted_satellite_ok} flag. If set to true, messages will be allowed
     * to be delivered to the app while the device is on a restricted satellite network.
     *
     * @param restrictedSatelliteOk True to allow delivery on restricted satellite networks.
     * @return This builder.
     */
    public Builder setRestrictedSatelliteOk(boolean restrictedSatelliteOk) {
      this.restrictedSatelliteOk = restrictedSatelliteOk;
      return this;
    }

    /**
     * Creates a new {@link AndroidConfigV2} instance from the parameters set on this builder.
     *
     * @return A new {@link AndroidConfigV2} instance.
     * @throws IllegalArgumentException If any of the parameters set on the builder are invalid.
     */
    public AndroidConfigV2 build() {
      return new AndroidConfigV2(this);
    }
  }
}
