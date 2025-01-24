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
import com.google.common.collect.ImmutableList;
import com.google.firebase.internal.NonNull;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.CustomSignalConditionResponse;

import java.util.ArrayList;
import java.util.List;

/** Represents a condition that compares provided signals against a target value. */
public final class CustomSignalCondition {
  private final String customSignalKey;
  private final CustomSignalOperator customSignalOperator;
  private final ImmutableList<String> targetCustomSignalValues;

  /**
   * Creates new Custom signal condition.
   *
   * @param customSignalKey The key of the signal set in the EvaluationContext
   * @param customSignalOperator The choice of custom signal operator to determine how to compare
   *     targets to value(s).
   * @param targetCustomSignalValues A list of at most 100 target custom signal values. For numeric
   *     operators, this will have exactly ONE target value.
   */
  public CustomSignalCondition(
      @NonNull String customSignalKey,
      @NonNull CustomSignalOperator customSignalOperator,
      @NonNull List<String> targetCustomSignalValues) {
    checkArgument(
        !Strings.isNullOrEmpty(customSignalKey), "Custom signal key must not be null or empty.");
    checkNotNull(customSignalOperator);
    checkNotNull(targetCustomSignalValues);
    checkArgument(
        !targetCustomSignalValues.isEmpty(), "Target custom signal values must not be empty.");
    this.customSignalKey = customSignalKey.trim();
    this.customSignalOperator = customSignalOperator;
    this.targetCustomSignalValues = ImmutableList.copyOf(targetCustomSignalValues);
  }

  /**
   * Creates a new {@link CustomSignalCondition} from API response.
   *
   * @param customSignalCondition the conditions obtained from server call.
   */
  CustomSignalCondition(CustomSignalConditionResponse customSignalCondition) {
    checkArgument(
        !Strings.isNullOrEmpty(customSignalCondition.getKey()),
        "Custom signal key must not be null or empty.");
    checkArgument(
        !customSignalCondition.getTargetValues().isEmpty(),
        "Target custom signal values must not be empty.");
    this.customSignalKey = customSignalCondition.getKey().trim();
    List<String> targetCustomSignalValuesList = customSignalCondition.getTargetValues();
    this.targetCustomSignalValues = ImmutableList.copyOf(targetCustomSignalValuesList);
    switch (customSignalCondition.getOperator()) {
      case "NUMERIC_EQUAL":
        this.customSignalOperator = CustomSignalOperator.NUMERIC_EQUAL;
        break;
      case "NUMERIC_GREATER_EQUAL":
        this.customSignalOperator = CustomSignalOperator.NUMERIC_GREATER_EQUAL;
        break;
      case "NUMERIC_GREATER_THAN":
        this.customSignalOperator = CustomSignalOperator.NUMERIC_GREATER_THAN;
        break;
      case "NUMERIC_LESS_EQUAL":
        this.customSignalOperator = CustomSignalOperator.NUMERIC_LESS_EQUAL;
        break;
      case "NUMERIC_LESS_THAN":
        this.customSignalOperator = CustomSignalOperator.NUMERIC_LESS_THAN;
        break;
      case "NUMERIC_NOT_EQUAL":
        this.customSignalOperator = CustomSignalOperator.NUMERIC_NOT_EQUAL;
        break;
      case "SEMANTIC_VERSION_EQUAL":
        this.customSignalOperator = CustomSignalOperator.SEMANTIC_VERSION_EQUAL;
        break;
      case "SEMANTIC_VERSION_GREATER_EQUAL":
        this.customSignalOperator = CustomSignalOperator.SEMANTIC_VERSION_GREATER_EQUAL;
        break;
      case "SEMANTIC_VERSION_GREATER_THAN":
        this.customSignalOperator = CustomSignalOperator.SEMANTIC_VERSION_GREATER_THAN;
        break;
      case "SEMANTIC_VERSION_LESS_EQUAL":
        this.customSignalOperator = CustomSignalOperator.SEMANTIC_VERSION_LESS_EQUAL;
        break;
      case "SEMANTIC_VERSION_LESS_THAN":
        this.customSignalOperator = CustomSignalOperator.SEMANTIC_VERSION_LESS_THAN;
        break;
      case "SEMANTIC_VERSION_NOT_EQUAL":
        this.customSignalOperator = CustomSignalOperator.SEMANTIC_VERSION_NOT_EQUAL;
        break;
      case "STRING_CONTAINS":
        this.customSignalOperator = CustomSignalOperator.STRING_CONTAINS;
        break;
      case "STRING_CONTAINS_REGEX":
        this.customSignalOperator = CustomSignalOperator.STRING_CONTAINS_REGEX;
        break;
      case "STRING_DOES_NOT_CONTAIN":
        this.customSignalOperator = CustomSignalOperator.STRING_DOES_NOT_CONTAIN;
        break;
      case "STRING_EXACTLY_MATCHES":
        this.customSignalOperator = CustomSignalOperator.STRING_EXACTLY_MATCHES;
        break;
      default:
        this.customSignalOperator = CustomSignalOperator.UNSPECIFIED;
    }
    checkArgument(
        this.customSignalOperator != CustomSignalOperator.UNSPECIFIED,
        "Custom signal operator passed is invalid");
  }

  /**
   * Gets the key of the signal set in the EvaluationContext.
   *
   * @return Custom signal key.
   */
  @NonNull
  public String getCustomSignalKey() {
    return customSignalKey;
  }

  /**
   * Gets the choice of custom signal operator to determine how to compare targets to value(s).
   *
   * @return Custom signal operator.
   */
  @NonNull
  public CustomSignalOperator getCustomSignalOperator() {
    return customSignalOperator;
  }

  /**
   * Gets the list of at most 100 target custom signal values. For numeric operators, this will have
   * exactly ONE target value.
   *
   * @return List of target values.
   */
  @NonNull
  public List<String> getTargetCustomSignalValues() {
    return new ArrayList<>(targetCustomSignalValues);
  }

  CustomSignalConditionResponse toCustomConditonResponse() {
    CustomSignalConditionResponse customSignalConditionResponse =
        new CustomSignalConditionResponse();
    customSignalConditionResponse.setKey(this.customSignalKey);
    customSignalConditionResponse.setOperator(this.customSignalOperator.getOperator());
    customSignalConditionResponse.setTargetValues(this.targetCustomSignalValues);
    return customSignalConditionResponse;
  }
}
