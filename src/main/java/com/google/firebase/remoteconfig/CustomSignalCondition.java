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
 * Represents a condition that compares provided signals against a target value.
 */
public class CustomSignalCondition {
    private final String customSignalKey;
    private final CustomSignalOperator customSignalOperator;
    private final ImmutableList<String> targetCustomSignalValues;

    /**
     * Creates new Custom signal condition.
     * 
     * @param customSignalKey
     * @param customSignalOperator
     * @param targetCustomSignalValues
     */
    public CustomSignalCondition(String customSignalKey, CustomSignalOperator customSignalOperator, ImmutableList<String> targetCustomSignalValues){
        this.customSignalKey = customSignalKey;
        this.customSignalOperator = customSignalOperator;
        this.targetCustomSignalValues = targetCustomSignalValues;
    }

    /**
     * Gets the key of the signal set in the EvaluationContext
     * {@link CustomSignalCondition.customSignalKey}.
     * 
     * @return Custom signal key.
     */
    public String getCustomSignalKey() {
        return customSignalKey;
    }

    /**
     * Gets the choice of custom signal operator
     * {@link CustomSignalCondition.customSignalOperator} to determine how to
     * compare targets to value(s).
     * 
     * @return Custom signal operator.
     */
    public CustomSignalOperator getCustomSignalOperator() {
        return customSignalOperator;
    }

    /**
     * Gets the list of at most 100 target custom signal values
     * {@link CustomSignalCondition.targetCustomSignalValues}. For numeric
     * operators, this will have exactly ONE target value.
     * 
     * @return List of target values.
     */
    public ImmutableList<String> getTargetCustomSignalValues() {
        return targetCustomSignalValues;
    }
}
