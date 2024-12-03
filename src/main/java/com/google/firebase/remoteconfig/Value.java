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

import com.google.common.collect.ImmutableList;
import com.google.firebase.internal.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a parameter value with metadata and type-safe getters. Type-safe
 * getters insulate application logic from remote changes to parameter names and
 * types.
 */
public class Value {
  private static final Logger logger = LoggerFactory.getLogger(ConditionEvaluator.class);
  private static final boolean DEFAULT_VALUE_FOR_BOOLEAN = false;
  private static final String DEFAULT_VALUE_FOR_STRING = "";
  private static final long DEFAULT_VALUE_FOR_LONG = 0;
  private static final double DEFAULT_VALUE_FOR_DOUBLE = 0;
  private static final ImmutableList<String> BOOLEAN_TRUTHY_VALUES = ImmutableList.of("1", "true",
      "t", "yes", "y", "on");

  public enum ValueSource {
    STATIC,
    REMOTE,
    DEFAULT
  }

  private final ValueSource source;
  private final String value;

  /**
   * Creates a new {@link Value} object.
   * 
   * @param source Indicates the source of a value.
   * @param value  Indicates a parameter value.
   */
  public Value(@NonNull ValueSource source, @NonNull String value) {
    this.source = source;
    this.value = value;
  }

  /**
   * Creates a new {@link Value} object with default value.
   * 
   * @param source Indicates the source of a value.
   */
  public Value(@NonNull ValueSource source) {
    this(source, DEFAULT_VALUE_FOR_STRING);
  }

  /**
   * Gets the value as a string.
   * 
   * @return value as string
   */
  @NonNull
  public String asString() {
    return this.value;
  }

  /**
   * Gets the value as a boolean.The following values (case
   * insensitive) are interpreted as true: "1", "true", "t", "yes", "y", "on".
   * Other values are interpreted as false.
   * 
   * @return value as boolean
   */
  @NonNull
  public boolean asBoolean() {
    if (source == ValueSource.STATIC) {
      return DEFAULT_VALUE_FOR_BOOLEAN;
    }
    return BOOLEAN_TRUTHY_VALUES.contains(value.toLowerCase());
  }

  /**
   * Gets the value as long. Comparable to calling Number(value) || 0.
   * 
   * @return value as long
   */
  @NonNull
  public long asLong() {
    if (source == ValueSource.STATIC) {
      return DEFAULT_VALUE_FOR_LONG;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      logger.warn("Unable to convert %s to long type.", value);
      return DEFAULT_VALUE_FOR_LONG;
    }
  }

  /**
   * Gets the value as double. Comparable to calling Number(value) || 0.
   * 
   * @return value as double
   */
  @NonNull
  public double asDouble() {
    if (source == ValueSource.STATIC) {
      return DEFAULT_VALUE_FOR_DOUBLE;
    }
    try {
      return Double.parseDouble(this.value);
    } catch (NumberFormatException e) {
      logger.warn("Unable to convert %s to double type.", value);
      return DEFAULT_VALUE_FOR_DOUBLE;
    }
  }

  /**
   * Gets the {@link ValueSource} for the given key.
   * 
   * @return source.
   */
  @NonNull
  public ValueSource getSource() {
    return source;
  }
}