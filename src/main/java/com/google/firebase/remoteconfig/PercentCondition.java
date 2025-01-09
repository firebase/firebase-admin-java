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
import com.google.firebase.internal.Nullable;

/**
 * Represents a condition that compares the instance pseudo-random percentile to
 * a given limit.
 */
public final class PercentCondition {
  private int microPercent;
  private MicroPercentRange microPercentRange;
  private final PercentConditionOperator percentConditionOperator;
  private final String seed;

  /**
   * Create a percent condition for operator BETWEEN.
   * 
   * @param microPercent The limit of percentiles to target in micro-percents when using the
   *        LESS_OR_EQUAL and GREATER_THAN operators. The value must be in the range
   *        [0 and 100000000].
   * @param percentConditionOperator The choice of percent operator to determine how to compare
   *        targets to percent(s).
   * @param seed The seed used when evaluating the hash function to map an instance to a value in
   *        the hash space. This is a string which can have 0 - 32 characters and can contain ASCII
   *        characters [-_.0-9a-zA-Z].The string is case-sensitive.
   */
  public PercentCondition(@Nullable Integer microPercent,
      @NonNull PercentConditionOperator percentConditionOperator,
      @NonNull String seed) {
    this.microPercent = microPercent != null ? microPercent : 0;
    this.percentConditionOperator = percentConditionOperator;
    this.seed = seed;
  }

  /**
   * Create a percent condition for operators GREATER_THAN and LESS_OR_EQUAL.
   * 
   * @param microPercentRange The micro-percent interval to be used with the BETWEEN operator.
   * @param percentConditionOperator The choice of percent operator to determine how to compare
   *        targets to percent(s).
   * @param seed The seed used when evaluating the hash function to map an instance to a value
   *        in the hash space. This is a string which can have 0 - 32 characters and can contain
   *        ASCII characters [-_.0-9a-zA-Z].The string is case-sensitive.
   */
  public PercentCondition(@NonNull MicroPercentRange microPercentRange,
      @NonNull PercentConditionOperator percentConditionOperator,
      String seed) {
    this.microPercentRange = microPercentRange;
    this.percentConditionOperator = percentConditionOperator;
    this.seed = seed;
  }

  /**
   * Gets the limit of percentiles to target in micro-percents when using the
   * LESS_OR_EQUAL and GREATER_THAN operators. The value must be in the range [0
   * and 100000000].
   * 
   * @return micro percent.
   */
  @Nullable
  public int getMicroPercent() {
    return microPercent;
  }

  /**
   * Gets micro-percent interval to be used with the BETWEEN operator.
   * 
   * @return micro percent range.
   */
  @Nullable
  public MicroPercentRange getMicroPercentRange() {
    return microPercentRange;
  }

  /**
   * Gets choice of percent operator to determine how to compare targets to
   * percent(s).
   * 
   * @return operator.
   */
  @NonNull
  public PercentConditionOperator getPercentConditionOperator() {
    return percentConditionOperator;
  }

  /**
   * The seed used when evaluating the hash function to map an instance to a value
   * in the hash space. This is a string which can have 0 - 32 characters and can
   * contain ASCII characters [-_.0-9a-zA-Z].The string is case-sensitive.
   * 
   * @return seed.
   */
  @NonNull
  public String getSeed() {
    return seed;
  }
}