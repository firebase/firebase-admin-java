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
 * Represents a condition that compares the instance pseudo-random percentile to
 * a given limit.
 */
public class PercentCondition {
    private int microPercent;
    private MicroPercentRange microPercentRange;
    private final PercentConditionOperator percentConditionOperator;
    private final String seed;

    /**
     * Create a percent condition for {@link PercentConditionOperator.BETWEEN}.
     * 
     * @param microPercent
     * @param percentConditionOperator
     * @param seed
     */
    public PercentCondition(int microPercent, PercentConditionOperator percentConditionOperator, String seed) {
        this.microPercent = microPercent;
        this.percentConditionOperator = percentConditionOperator;
        this.seed = seed;
    }

    /**
     * Create a percent condition for {@link PercentConditionOperator.GREATER_THAN}
     * and {@link PercentConditionOperator.LESS_OR_EQUAL}.
     * 
     * @param microPercentRange
     * @param percentConditionOperator
     * @param seed
     */
    public PercentCondition(MicroPercentRange microPercentRange, PercentConditionOperator percentConditionOperator,
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
    public int getMicroPercent() {
        return microPercent;
    }

    /**
     * Gets micro-percent interval to be used with the BETWEEN operator.
     * 
     * @return micro percent range.
     */
    public MicroPercentRange getMicroPercentRange() {
        return microPercentRange;
    }

    /**
     * Gets choice of percent operator to determine how to compare targets to
     * percent(s).
     * 
     * @return operator.
     */
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
    public String getSeed() {
        return seed;
    }
}
