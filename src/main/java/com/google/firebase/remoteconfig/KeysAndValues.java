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

import com.google.common.collect.ImmutableMap;
import com.google.firebase.internal.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents data stored in context passed to server-side Remote Config.
 */
public class KeysAndValues {
  final ImmutableMap<String, String> keysAndValues;

  private KeysAndValues(@NonNull Builder builder) {
    keysAndValues = keysAndValues.builder().putAll(builder.keysAndValues).build();
  }

  /**
   * Checks whether a key is present in the context.
   * 
   * @param key The key for data stored in context.
   * @return Boolean representing whether the key passed is present in context.
   */
  public boolean containsKey(String key) {
    return keysAndValues.containsKey(key);
  }

  /**
   * Gets the value of the data stored in context.
   * 
   * @param key The key for data stored in context.
   * @return Value assigned to the key in context.
   */
  public String get(String key) {
    return keysAndValues.get(key);
  }

  /**
   * Builder class for KeysAndValues using which values will be assigned to
   * private variables.
   */
  public static class Builder {
    // Holds the converted pairs of custom keys and values.
    private Map<String, String> keysAndValues;

    /**
     * Creates an empty Map to save context.
     */
    public Builder() {
      keysAndValues = new HashMap<>();
    }

    /**
     * Adds a context data with string value.
     * 
     * @param key   Identifies the value in context.
     * @param value Value assigned to the context.
     * @return Reference to class itself so that more data can be added.
     */
    @NonNull
    public Builder put(@NonNull String key, @NonNull String value) {
      keysAndValues.put(key, value);
      return this;
    }

    /**
     * Adds a context data with boolean value.
     * 
     * @param key   Identifies the value in context.
     * @param value Value assigned to the context.
     * @return Reference to class itself so that more data can be added.
     */
    @NonNull
    public Builder put(@NonNull String key, boolean value) {
      keysAndValues.put(key, Boolean.toString(value));
      return this;
    }

    /**
     * Adds a context data with double value.
     * 
     * @param key   Identifies the value in context.
     * @param value Value assigned to the context.
     * @return Reference to class itself so that more data can be added.
     */
    @NonNull
    public Builder put(@NonNull String key, double value) {
      keysAndValues.put(key, Double.toString(value));
      return this;
    }

    /**
     * Adds a context data with long value.
     * 
     * @param key   Identifies the value in context.
     * @param value Value assigned to the context.
     * @return Reference to class itself so that more data can be added.
     */
    @NonNull
    public Builder put(@NonNull String key, long value) {
      keysAndValues.put(key, Long.toString(value));
      return this;
    }

    /**
     * Creates an instance of KeysAndValues with the values assigned through
     * builder.
     * 
     * @return instance of KeysAndValues
     */
    @NonNull
    public KeysAndValues build() {
      return new KeysAndValues(this);
    }
  }
}
