/*
 * Copyright 2019 Google Inc.
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

/**
 * A class representing color in LightSettings.
 */
public final class LightSettingsColor {
  
  @Key("red")
  private final Float red;
  
  @Key("green")
  private final Float green;
  
  @Key("blue")
  private final Float blue;
  
  @Key("alpha")
  private final Float alpha;
  
  /**
   * Creates a new {@link LightSettingsColor} using the given red, green, blue, and
   * alpha values.
   *
   * @param red The red component.
   * @param green The green component.
   * @param blue The blue component.
   * @param alpha The alpha component.
   */
  public LightSettingsColor(float red, float green, float blue, float alpha) {
    this.red = red;
    this.green = green;
    this.blue = blue;
    this.alpha = alpha;
  }

  /**
   * Creates a new {@link LightSettingsColor} with a string. Alpha of the color will be 
   * set to 1.
   *
   * @param rrggbb LightSettingsColor specified in the {@code #rrggbb} format.
   * @return A {@link LightSettingsColor} instance.
   */
  public static LightSettingsColor fromString(String rrggbb) {
    checkArgument(rrggbb.matches("^#[0-9a-fA-F]{6}$"), 
              "LightSettingsColor must be in the form #RRGGBB");
    float red = (float) Integer.parseInt(rrggbb.substring(1, 3), 16) / 255.0f;
    float green = (float) Integer.valueOf(rrggbb.substring(3, 5), 16) / 255.0f;
    float blue = (float) Integer.valueOf(rrggbb.substring(5, 7), 16) / 255.0f;
    return new LightSettingsColor(red, green, blue, 1.0f);
  }
}
