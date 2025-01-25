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
import java.util.stream.Collectors;

final class AndCondition {
  private final ImmutableList<OneOfCondition> conditions;

  AndCondition(@NonNull List<OneOfCondition> conditions) {
    checkNotNull(conditions, "List of conditions for AND operation cannot be null.");
    checkArgument(!conditions.isEmpty(), "List of conditions for AND operation cannot be empty.");
    this.conditions = ImmutableList.copyOf(conditions);
  }

  AndCondition(AndConditionResponse andConditionResponse) {
    List<OneOfConditionResponse> conditionList = andConditionResponse.getConditions();
    checkNotNull(conditionList, "List of conditions for AND operation cannot be null.");
    checkArgument(!conditionList.isEmpty(), "List of conditions for AND operation cannot be empty");
    this.conditions = conditionList.stream()
                                  .map(OneOfCondition::new) 
                                  .collect(ImmutableList.toImmutableList());
  }

  @NonNull
  List<OneOfCondition> getConditions() {
    return new ArrayList<>(conditions);
  }

  AndConditionResponse toAndConditionResponse() {
    return new AndConditionResponse()
                   .setConditions(this.conditions.stream()
                                                 .map(OneOfCondition::toOneOfConditionResponse)
                                                 .collect(Collectors.toList()));    
  }
}
