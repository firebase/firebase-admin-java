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

import com.google.common.collect.ImmutableList;
import com.google.firebase.internal.NonNull;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.AndConditionResponse;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.OneOfConditionResponse;

import java.util.ArrayList;
import java.util.List;

/** Represents a collection of conditions that evaluate to true if all are true. */
public final class AndCondition {
  private final ImmutableList<OneOfCondition> conditions;

  /** Creates a new 
   * {@link AndCondition} 
   * joining subconditions. 
   * */
  public AndCondition(@NonNull List<OneOfCondition> conditions) {
    checkNotNull(conditions, "List of conditions for AND operation cannot be null.");
    checkArgument(!conditions.isEmpty(), "List of conditions for AND operation cannot be empty.");
    this.conditions = ImmutableList.copyOf(conditions);
  }

  AndCondition(AndConditionResponse andConditionResponse) {
    List<OneOfCondition> nestedConditions = new ArrayList<>();
    List<OneOfConditionResponse> conditionList = andConditionResponse.getConditions();
    checkNotNull(conditionList, "List of conditions for AND operation cannot be null.");
    checkArgument(!conditionList.isEmpty(), "List of conditions for AND operation cannot be empty");
    for (OneOfConditionResponse nestedResponse : conditionList) {
      OneOfCondition nestedCondition = new OneOfCondition(nestedResponse);
      nestedConditions.add(nestedCondition);
    }
    this.conditions = ImmutableList.copyOf(nestedConditions);
  }

  /**
   * Gets the list of {@link OneOfCondition}
   *
   * @return List of conditions to evaluate.
   */
  @NonNull
  public List<OneOfCondition> getConditions() {
    return new ArrayList<>(conditions);
  }

  AndConditionResponse toAndConditionResponse() {
    AndConditionResponse andConditionResponse = new AndConditionResponse();
    List<OneOfConditionResponse> nestedConditionResponses = new ArrayList<>();
    for (OneOfCondition condition : conditions) {
      nestedConditionResponses.add(condition.toOneOfConditionResponse());
    }
    andConditionResponse.setConditions(nestedConditionResponses);
    return andConditionResponse;
  }
}
