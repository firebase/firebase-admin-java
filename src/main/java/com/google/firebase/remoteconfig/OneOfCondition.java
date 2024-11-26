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

/**
 * Represents a condition that may be one of several types. Only the first
 * defined field will be processed.
 */
public class OneOfCondition {
    private OrCondition orCondition;
    private AndCondition andCondition;
    private PercentCondition percent;
    private CustomSignalCondition customSignal;
    private String trueValue;
    private String falseValue;

    /**
     * Gets {@link OrCondition} with conditions on which OR operation will be applied.
     * 
     * @return list of conditions.
     */
    public OrCondition getOrCondition() {
        return orCondition;
    }

    /**
     * Gets {@link AndCondition} with conditions on which AND operation will be applied.
     * 
     * @return list of conditions.
     */
    public AndCondition getAndCondition() {
        return andCondition;
    }

    /**
     * Returns true indicating the expression has evaluated to true.
     * 
     * @return true.
     */
    public String isTrue() {
        return trueValue;
    }

    /**
     * Returns false indicating the expression has evaluated to false.
     * 
     * @return false.
     */
    public String isFalse() {
        return falseValue;
    }

    /**
     * Gets {@link PercentCondition}.
     * 
     * @return percent condition.
     */
    public PercentCondition getPercent() {
        return percent;
    }

    /**
     * Gets {@link CustomSignalCondition}.
     * 
     * @return custom condition.
     */
    public CustomSignalCondition getCustomSignal() {
        return customSignal;
    }

    /**
     * Sets list of conditions on which OR operation will be applied.
     * 
     * @param orCondition
     */
    public void setOrCondition(OrCondition orCondition) {
        this.orCondition = orCondition;
    }

    /**
     * Sets list of conditions on which AND operation will be applied.
     * 
     * @param andCondition
     */
    public void setAndCondition(AndCondition andCondition) {
        this.andCondition = andCondition;
    }

    /**
     * Sets {@link PercentCondition}.
     * 
     * @param percent
     */
    public void setPercent(PercentCondition percent) {
        this.percent = percent;
    }

    /**
     * Sets {@link CustomSignalCondition}.
     * 
     * @param customSignal
     */
    public void setCustomSignal(CustomSignalCondition customSignal) {
        this.customSignal = customSignal;
    }

    /**
     * Sets evaluation value to true.
     */
    public void setTrue() {
        this.trueValue = "true";
    }

    /**
     * Sets evaluation value to false.
     */
    public void setFalse() {
        this.falseValue = "false";
    }
}
