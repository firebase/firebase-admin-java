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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.PercentConditionResponse;

/** Represents a condition that compares the instance pseudo-random percentile to a given limit. */
public final class PercentCondition {
  private int microPercent;
  private MicroPercentRange microPercentRange;
  private final PercentConditionOperator percentConditionOperator;
  private final String seed;

  /**
   * Create a percent condition for operator BETWEEN.
   *
   * @param microPercent The limit of percentiles to target in micro-percents when using the
   *     LESS_OR_EQUAL and GREATER_THAN operators. The value must be in the range [0 and 100000000].
   * @param percentConditionOperator The choice of percent operator to determine how to compare
   *     targets to percent(s).
   * @param seed The seed used when evaluating the hash function to map an instance to a value in
   *     the hash space. This is a string which can have 0 - 32 characters and can contain ASCII
   *     characters [-_.0-9a-zA-Z].The string is case-sensitive.
   */
  public PercentCondition(
      @Nullable Integer microPercent,
      @NonNull PercentConditionOperator percentConditionOperator,
      @NonNull String seed) {
    checkNotNull(percentConditionOperator, "Percentage operator must not be null.");
    checkArgument(!Strings.isNullOrEmpty(seed), "Seed must not be null or empty.");
    this.microPercent = microPercent != null ? microPercent : 0;
    this.percentConditionOperator = percentConditionOperator;
    this.seed = seed;
  }

  /**
   * Create a percent condition for operators GREATER_THAN and LESS_OR_EQUAL.
   *
   * @param microPercentRange The micro-percent interval to be used with the BETWEEN operator.
   * @param percentConditionOperator The choice of percent operator to determine how to compare
   *     targets to percent(s).
   * @param seed The seed used when evaluating the hash function to map an instance to a value in
   *     the hash space. This is a string which can have 0 - 32 characters and can contain ASCII
   *     characters [-_.0-9a-zA-Z].The string is case-sensitive.
   */
  public PercentCondition(
      @NonNull MicroPercentRange microPercentRange,
      @NonNull PercentConditionOperator percentConditionOperator,
      String seed) {
    checkNotNull(microPercentRange, "Percent range must not be null.");
    checkNotNull(percentConditionOperator, "Percentage operator must not be null.");
    this.microPercentRange = microPercentRange;
    this.percentConditionOperator = percentConditionOperator;
    this.seed = seed;
  }

  /**
   * Creates a new {@link PercentCondition} from API response.
   *
   * @param percentCondition the conditions obtained from server call.
   */
  PercentCondition(PercentConditionResponse percentCondition) {
    checkArgument(
        !Strings.isNullOrEmpty(percentCondition.getSeed()), "Seed must not be empty or null");
    this.microPercent = percentCondition.getMicroPercent();
    this.seed = percentCondition.getSeed();
    switch (percentCondition.getPercentOperator()) {
      case "BETWEEN":
        this.percentConditionOperator = PercentConditionOperator.BETWEEN;
        break;
      case "GREATER_THAN":
        this.percentConditionOperator = PercentConditionOperator.GREATER_THAN;
        break;
      case "LESS_OR_EQUAL":
        this.percentConditionOperator = PercentConditionOperator.LESS_OR_EQUAL;
        break;
      default:
        this.percentConditionOperator = PercentConditionOperator.UNSPECIFIED;
    }
    checkArgument(
        this.percentConditionOperator != PercentConditionOperator.UNSPECIFIED,
        "Percentage operator is invalid");
    if (percentCondition.getMicroPercentRange() != null) {
      this.microPercentRange =
          new MicroPercentRange(
              percentCondition.getMicroPercentRange().getMicroPercentLowerBound(),
              percentCondition.getMicroPercentRange().getMicroPercentUpperBound());
    }
  }

  /**
   * Gets the limit of percentiles to target in micro-percents when using the LESS_OR_EQUAL and
   * GREATER_THAN operators. The value must be in the range [0 and 100000000].
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
   * Gets choice of percent operator to determine how to compare targets to percent(s).
   *
   * @return operator.
   */
  @NonNull
  public PercentConditionOperator getPercentConditionOperator() {
    return percentConditionOperator;
  }

  /**
   * The seed used when evaluating the hash function to map an instance to a value in the hash
   * space. This is a string which can have 0 - 32 characters and can contain ASCII characters
   * [-_.0-9a-zA-Z].The string is case-sensitive.
   *
   * @return seed.
   */
  @NonNull
  public String getSeed() {
    return seed;
  }

  public PercentConditionResponse toPercentConditionResponse() {
    PercentConditionResponse percentConditionResponse = new PercentConditionResponse();
    percentConditionResponse.setMicroPercent(this.microPercent);
    percentConditionResponse.setMicroPercentRange(
        this.microPercentRange.toMicroPercentRangeResponse());
    percentConditionResponse.setPercentOperator(this.percentConditionOperator.getOperator());
    percentConditionResponse.setSeed(this.seed);
    return percentConditionResponse;
  }
}
