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

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a condition that compares provided signals against a target value.
 */
public final class CustomSignalCondition {
  private final String customSignalKey;
  private final CustomSignalOperator customSignalOperator;
  private final ImmutableList<String> targetCustomSignalValues;

  /**
   * Creates new Custom signal condition.
   * 
   * @param customSignalKey The key of the signal set in the EvaluationContext
   * @param customSignalOperator The choice of custom signal operator to determine how to
   *        compare targets to value(s).
   * @param targetCustomSignalValues A list of at most 100 target custom signal values.
   *        For numeric operators, this will have exactly ONE target value.
   */
  public CustomSignalCondition(@NonNull String customSignalKey,
      @NonNull CustomSignalOperator customSignalOperator,
      @NonNull List<String> targetCustomSignalValues) {
    this.customSignalKey = customSignalKey;
    this.customSignalOperator = customSignalOperator;
    this.targetCustomSignalValues = ImmutableList.copyOf(targetCustomSignalValues);
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
   * Gets the list of at most 100 target custom signal values. For numeric operators, this will
   * have exactly ONE target value.
   * 
   * @return List of target values.
   */
  @NonNull
  public List<String> getTargetCustomSignalValues() {
    return new ArrayList<>(targetCustomSignalValues);
  }
}
