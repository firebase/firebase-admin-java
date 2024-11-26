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

/**
 * Represents a collection of conditions that evaluate to true if all are true.
 */
public class AndCondition {
    private final ImmutableList<OneOfCondition> conditions;

    /**
     * Creates AndCondition joining subconditions.
     */
    public AndCondition(ImmutableList<OneOfCondition> conditions) {
        this.conditions = conditions;
    }

    /**
     * Gets the list of {@link OneOfCondition}
     * 
     * @return List of conditions to evaluate.
     */
    public ImmutableList<OneOfCondition> getConditions() {
        return conditions;
    }
}
