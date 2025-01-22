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

import com.google.common.base.Strings;
import com.google.firebase.internal.NonNull;

/**
 * Defines supported operators for percent conditions.
 */
public enum PercentConditionOperator {
  BETWEEN("BETWEEN"),
  GREATER_THAN("GREATER_THAN"),
  LESS_OR_EQUAL("LESS_OR_EQUAL"),
  UNSPECIFIED("PERCENT_OPERATOR_UNSPECIFIED");

  private final String operator;

  /**
   * Creates percent condition operator.
   * 
   * @param operator The choice of percent operator to determine how to compare targets to
   *        percent(s).
   */
  PercentConditionOperator(@NonNull String operator) {
    checkArgument(!Strings.isNullOrEmpty(operator), "Operator must not be null or empty.");
    this.operator = operator;
  }

  /**
   * Gets percent condition operator.
   * 
   * @return operator.
   */
  @NonNull
  public String getOperator() {
    return operator;
  }
}
