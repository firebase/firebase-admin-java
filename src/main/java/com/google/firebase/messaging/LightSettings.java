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
import java.util.concurrent.TimeUnit;

/**
 * A class representing light settings in an Android Notification.
 */
public class LightSettings {
  
  @Key("color")
  private final Color color;
  
  @Key("light_on_duration")
  private final String lightOnDuration;
  
  @Key("light_off_duration")
  private final String lightOffDuration;
  
  private LightSettings(Builder builder) {
    this.color = builder.color;
    this.lightOnDuration = builder.lightOnDuration;
    this.lightOffDuration = builder.lightOffDuration;
  }
  
  /**
   * Creates a new {@link LightSettings.Builder}.
   *
   * @return A {@link LightSettings.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }
  
  public static class Builder {

    private Color color;
    private String lightOnDuration;
    private String lightOffDuration;

    private Builder() {}

    /**
     * Sets the color value with a string.   
     *
     * @param color Color specified in the {@code #rrggbb} format.
     * @return This builder.
     */
    public Builder setColorFromString(String color) {
      this.color = Color.fromString(color);
      return this;
    }
 
    /**
     * Sets the light on duration in milliseconds.   
     *
     * @param lightOnDurationInMillis The time duration in milliseconds for the LED light to be on.
     * @return This builder.
     */
    public Builder setLightOnDurationInMillis(long lightOnDurationInMillis) {
      checkArgument(lightOnDurationInMillis >= 0, "ttl must not be negative");
      long seconds = TimeUnit.MILLISECONDS.toSeconds(lightOnDurationInMillis);
      long subsecondNanos = TimeUnit.MILLISECONDS
          .toNanos(lightOnDurationInMillis - seconds * 1000L);
      if (subsecondNanos > 0) {
        this.lightOnDuration = String.format("%d.%09ds", seconds, subsecondNanos);
      } else {
        this.lightOnDuration = String.format("%ds", seconds);
      }
      return this;
    }

    /**
     * Sets the light off duration in milliseconds.   
     *
     * @param lightOffDurationInMillis The time duration in milliseconds for the LED light to be 
     *     off.
     * @return This builder.
     */
    public Builder setLightOffDurationInMillis(long lightOffDurationInMillis) {
      checkArgument(lightOffDurationInMillis >= 0, "ttl must not be negative");
      long seconds = TimeUnit.MILLISECONDS.toSeconds(lightOffDurationInMillis);
      long subsecondNanos = TimeUnit.MILLISECONDS
          .toNanos(lightOffDurationInMillis - seconds * 1000L);
      if (subsecondNanos > 0) {
        this.lightOffDuration = String.format("%d.%09ds", seconds, subsecondNanos);
      } else {
        this.lightOffDuration = String.format("%ds", seconds);
      }
      return this;
    }
 
    /**
     * Builds a new {@link LightSettings} instance from the fields set on this builder.
     *
     * @return A non-null {@link LightSettings}.
     * @throws IllegalArgumentException If the volume value is out of range.
     */ 
    public LightSettings build() {
      return new LightSettings(this);
    }
  }
}
