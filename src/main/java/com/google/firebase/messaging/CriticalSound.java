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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

/**
 * The sound configuration for APNs critical alerts.
 */
public final class CriticalSound {

  private final Map<String, Object> fields;

  private CriticalSound(Builder builder) {
    checkArgument(!Strings.isNullOrEmpty(builder.name), "name must not be null or empty");
    ImmutableMap.Builder<String, Object> fields = ImmutableMap.<String, Object>builder()
        .put("name", builder.name);
    if (builder.critical) {
      fields.put("critical", 1);
    }
    if (builder.volume != null) {
      checkArgument(builder.volume >= 0 && builder.volume <= 1,
          "volume must be in the interval [0,1]");
      fields.put("volume", builder.volume);
    }
    this.fields = fields.build();
  }

  Map<String, Object> getFields() {
    return fields;
  }

  /**
   * Creates a new {@link CriticalSound.Builder}.
   *
   * @return A {@link CriticalSound.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private boolean critical;
    private String name;
    private Double volume;

    private Builder() {
    }

    /**
     * Sets the critical alert flag on the sound configuration.
     *
     * @param critical True to set the critical alert flag.
     * @return This builder.
     */
    public Builder setCritical(boolean critical) {
      this.critical = critical;
      return this;
    }

    /**
     * The name of a sound file in your app's main bundle or in the {@code Library/Sounds} folder
     * of your app’s container directory. Specify the string {@code default} to play the system
     * sound.
     *
     * @param name Sound file name.
     * @return This builder.
     */
    public Builder setName(String name) {
      this.name = name;
      return this;
    }

    /**
     * The volume for the critical alert's sound. Must be a value between 0.0 (silent) and 1.0
     * (full volume).
     *
     * @param volume A volume between 0.0 (inclusive) and 1.0 (inclusive).
     * @return This builder.
     */
    public Builder setVolume(double volume) {
      this.volume = volume;
      return this;
    }

    /**
     * Builds a new {@link CriticalSound} instance from the fields set on this builder.
     *
     * @return A non-null {@link CriticalSound}.
     * @throws IllegalArgumentException If the volume value is out of range.
     */
    public CriticalSound build() {
      return new CriticalSound(this);
    }
  }
}
