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

/**
 * A class representing color in LightSettings.
 */
public class Color {
  
  @Key("red")
  private final Float red;
  
  @Key("green")
  private final Float green;
  
  @Key("blue")
  private final Float blue;
  
  private Color(Builder builder) {
    this.red = builder.red;
    this.green = builder.green;
    this.blue = builder.blue;
  }
  
  /**
   * Creates a new {@link Color.Builder}.
   *
   * @return A {@link Color.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }
  
  public static class Builder {
    
    private Float red;
    private Float green;
    private Float blue;

    private Builder() {}

    /**
     * Sets the RGB component values with a string.   
     *
     * @param color Color specified in the {@code #rrggbb} format.
     * @return This builder.
     */
    public Builder fromString(String color) {
      this.red = Float.valueOf(color.substring(1,3));
      this.green = Float.valueOf(color.substring(3,5));
      this.blue = Float.valueOf(color.substring(5,7));
      return this;
    }

    /**
     * Builds a new {@link Color} instance from the fields set on this builder.
     *
     * @return A non-null {@link Color}.
     * @throws IllegalArgumentException If the volume value is out of range.
     */ 
    public Color build() {
      return new Color(this);
    }
  }
}
