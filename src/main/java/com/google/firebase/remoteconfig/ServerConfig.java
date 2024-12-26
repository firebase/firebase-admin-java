/*
 * Copyright 2020 Google LLC
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

package com.google.firebase.remoteconfig;

import com.google.firebase.internal.NonNull;

import java.util.Map;

/**
 * Represents the configuration produced by evaluating a server template.
 */
public final class ServerConfig {
  private final Map<String, Value> configValues;

  ServerConfig(Map<String, Value> configValues) {
    this.configValues = configValues;
  }

  /**
   * Gets the value for the given key as a string. Convenience method for calling
   * serverConfig.getValue(key).asString().
   * 
   * @param key The name of the parameter.
   * @return config value for the given key as string.
   */
  @NonNull
  public String getString(@NonNull String key) {
    return configValues.get(key).asString();
  }

  /**
   * Gets the value for the given key as a boolean.Convenience method for calling
   * serverConfig.getValue(key).asBoolean().
   * 
   * @param key The name of the parameter.
   * @return config value for the given key as boolean.
   */
  @NonNull
  public boolean getBoolean(@NonNull String key) {
    return Boolean.parseBoolean(getString(key));
  }

  /**
   * Gets the value for the given key as long.Convenience method for calling
   * serverConfig.getValue(key).asLong().
   * 
   * @param key The name of the parameter.
   * @return config value for the given key as long.
   */
  @NonNull
  public long getLong(@NonNull String key) {
    return Long.parseLong(getString(key));
  }

  /**
   * Gets the value for the given key as double.Convenience method for calling
   * serverConfig.getValue(key).asDouble().
   * 
   * @param key The name of the parameter.
   * @return config value for the given key as double.
   */
  @NonNull
  public double getDouble(@NonNull String key) {
    return Double.parseDouble(getString(key));
  }

}
