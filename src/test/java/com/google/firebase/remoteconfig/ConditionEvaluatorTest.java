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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;

public class ConditionEvaluatorTest {

  private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();

  @Test
  public void testEvaluateConditionsEmptyOrConditionToFalse() {
    OneOfCondition emptyOneOfConditionOr = createOneOfOrCondition(null);
    Map<String, OneOfCondition> conditions = new HashMap<>();
    conditions.put("is_enabled", emptyOneOfConditionOr);
    Map<String, Object> context = new HashMap<>();

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsEmptyOrAndConditionToTrue() {
    OneOfCondition emptyOneOfConditionAnd = createOneOfAndCondition(null);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(emptyOneOfConditionAnd);
    Map<String, OneOfCondition> conditions = new HashMap<>();
    conditions.put("is_enabled", oneOfConditionOr);
    Map<String, Object> context = new HashMap<>();

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsOrAndTrueToTrue() {
    OneOfCondition oneOfConditionTrue = createOneOfTrueCondition();
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionTrue);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    Map<String, OneOfCondition> conditions = new HashMap<>();
    conditions.put("is_enabled", oneOfConditionOr);
    Map<String, Object> context = new HashMap<>();

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsOrAndFalseToFalse() {
    OneOfCondition oneOfConditionFalse = createOneOfFalseCondition();
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionFalse);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    Map<String, OneOfCondition> conditions = new HashMap<>();
    conditions.put("is_enabled", oneOfConditionOr);
    Map<String, Object> context = new HashMap<>();

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsNonOrTopConditionToTrue() {
    OneOfCondition oneOfConditionTrue = createOneOfTrueCondition();
    Map<String, OneOfCondition> conditions = new HashMap<>();
    conditions.put("is_enabled", oneOfConditionTrue);
    Map<String, Object> context = new HashMap<>();

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsPercentConditionWithInvalidOperatorToFalse() {
    OneOfCondition oneOfConditionPercent = createPercentCondition(0,
        PercentConditionOperator.UNSPECIFIED, "seed");
    Map<String, OneOfCondition> conditions = new HashMap<>();
    conditions.put("is_enabled", oneOfConditionPercent);
    Map<String, Object> context = new HashMap<>();
    context.put("randomizationId", "abc");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsPercentConditionLessOrEqualMaxToTrue() {
    OneOfCondition oneOfConditionPercent = createPercentCondition(10_000_0000,
        PercentConditionOperator.LESS_OR_EQUAL, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    Map<String, OneOfCondition> conditions = new HashMap<>();
    conditions.put("is_enabled", oneOfConditionOr);
    Map<String, Object> context = new HashMap<>();
    context.put("randomizationId", "123");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsPercentConditionLessOrEqualMinToFalse() {
    OneOfCondition oneOfConditionPercent = createPercentCondition(0,
        PercentConditionOperator.LESS_OR_EQUAL, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    Map<String, OneOfCondition> conditions = new HashMap<>();
    conditions.put("is_enabled", oneOfConditionOr);
    Map<String, Object> context = new HashMap<>();
    context.put("randomizationId", "123");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsPercentConditionUndefinedMicroPercentToFalse() {
    OneOfCondition oneOfConditionPercent = createPercentCondition(null,
        PercentConditionOperator.LESS_OR_EQUAL, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    Map<String, OneOfCondition> conditions = new HashMap<>();
    conditions.put("is_enabled", oneOfConditionOr);
    Map<String, Object> context = new HashMap<>();
    context.put("randomizationId", "123");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsUseZeroForUndefinedPercentRange() {
    OneOfCondition oneOfConditionPercent = createBetweenPercentCondition(null, null, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    Map<String, OneOfCondition> conditions = new HashMap<>();
    conditions.put("is_enabled", oneOfConditionOr);
    Map<String, Object> context = new HashMap<>();
    context.put("randomizationId", "123");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsUseZeroForUndefinedUpperBound() {
    OneOfCondition oneOfConditionPercent = createBetweenPercentCondition(0, null, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    Map<String, OneOfCondition> conditions = new HashMap<>();
    conditions.put("is_enabled", oneOfConditionOr);
    Map<String, Object> context = new HashMap<>();
    context.put("randomizationId", "123");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsUseZeroForUndefinedLowerBound() {
    OneOfCondition oneOfConditionPercent = createBetweenPercentCondition(null, 10_000_0000, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    Map<String, OneOfCondition> conditions = new HashMap<>();
    conditions.put("is_enabled", oneOfConditionOr);
    Map<String, Object> context = new HashMap<>();
    context.put("randomizationId", "123");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("is_enabled"));
  }

  @Test
  public void testEvaluatedConditionsGreaterThanMinToTrue() {
    OneOfCondition oneOfConditionPercent = createPercentCondition(0,
        PercentConditionOperator.GREATER_THAN, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    Map<String, OneOfCondition> conditions = new HashMap<>();
    conditions.put("is_enabled", oneOfConditionOr);
    Map<String, Object> context = new HashMap<>();
    context.put("randomizationId", "123");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("is_enabled"));
  }

  @Test
  public void testEvaluatedConditionsGreaterThanMaxToFalse() {
    OneOfCondition oneOfConditionPercent = createPercentCondition(10_000_0000,
        PercentConditionOperator.GREATER_THAN, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    Map<String, OneOfCondition> conditions = new HashMap<>();
    conditions.put("is_enabled", oneOfConditionOr);
    Map<String, Object> context = new HashMap<>();
    context.put("randomizationId", "123");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("is_enabled"));
  }

  @Test
  public void testEvaluatedConditionsBetweenMinAndMaxToTrue() {
    OneOfCondition oneOfConditionPercent = createBetweenPercentCondition(0, 10_000_0000, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    Map<String, OneOfCondition> conditions = new HashMap<>();
    conditions.put("is_enabled", oneOfConditionOr);
    Map<String, Object> context = new HashMap<>();
    context.put("randomizationId", "123");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("is_enabled"));
  }

  @Test
  public void testEvaluatedConditionsBetweenEqualBoundsToFalse() {
    OneOfCondition oneOfConditionPercent = createBetweenPercentCondition(5_000_000,
        5_000_000, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    Map<String, OneOfCondition> conditions = new HashMap<>();
    conditions.put("is_enabled", oneOfConditionOr);
    Map<String, Object> context = new HashMap<>();
    context.put("randomizationId", "123");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsLessOrEqualToApprox() {
    OneOfCondition oneOfConditionPerCondition = createPercentCondition(10_000_000,
        PercentConditionOperator.LESS_OR_EQUAL, "seed");
    // 284 is 3 standard deviations for 100k trials with 10% probability.
    int tolerance = 284;

    int truthyAssignments = evaluateRandomAssignments(oneOfConditionPerCondition, 100000);

    // Evaluate less than or equal 10% to approx 10%
    assertTrue(truthyAssignments >= 10_000 - tolerance);
    assertTrue(truthyAssignments <= 10_000 + tolerance);
  }

  @Test
  public void testEvaluateConditionsBetweenApproximateToTrue() {
    // Micropercent range is 40% to 60%.
    OneOfCondition oneOfConditionPerCondition = createBetweenPercentCondition(40_000_000,
        60_000_000, "seed");
    // 379 is 3 standard deviations for 100k trials with 20% probability.
    int tolerance = 379;

    int truthyAssignments = evaluateRandomAssignments(oneOfConditionPerCondition, 100000);

    // Evaluate between 40% to 60% to approx 20%
    assertTrue(truthyAssignments >= 20_000 - tolerance);
    assertTrue(truthyAssignments <= 20_000 + tolerance);
  }

  @Test
  public void testEvaluateConditionsInterquartileToFiftyPercent() {
    // Micropercent range is 25% to 75%.
    OneOfCondition oneOfConditionPerCondition = createBetweenPercentCondition(25_000_000,
        75_000_000, "seed");
    // 474 is 3 standard deviations for 100k trials with 50% probability.
    int tolerance = 474;

    int truthyAssignments = evaluateRandomAssignments(oneOfConditionPerCondition, 100000);

    // Evaluate between 25% to 75 to approx 50%
    assertTrue(truthyAssignments >= 50_000 - tolerance);
    assertTrue(truthyAssignments <= 50_000 + tolerance);
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericLessThanToFalse() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.NUMERIC_LESS_THAN, ImmutableList.of("-50.0"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "-50.0");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericLessThanToTrue() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.NUMERIC_LESS_THAN, ImmutableList.of("-50.0"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "-50.01");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalInvalidValueNumericOperationToFalse() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.NUMERIC_LESS_THAN, ImmutableList.of("non-numeric"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "-50.01");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericLessEqualToFalse() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.NUMERIC_LESS_EQUAL, ImmutableList.of("-50.0"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "-50.0");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericLessEqualToTrue() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.NUMERIC_LESS_EQUAL, ImmutableList.of("-50.0"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "-49.9");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericEqualsToTrue() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.NUMERIC_EQUAL, ImmutableList.of("50.0"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "50.0");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericEqualsToFalse() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.NUMERIC_EQUAL, ImmutableList.of("50.0"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "50.000001");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericNotEqualsToTrue() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.NUMERIC_NOT_EQUAL, ImmutableList.of("50.0"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "50.0");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericNotEqualsToFalse() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.NUMERIC_NOT_EQUAL, ImmutableList.of("50.0"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "50.000001");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericGreaterEqualToFalse() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.NUMERIC_GREATER_EQUAL, ImmutableList.of("-50.0"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "-50.0");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericGreaterEqualToTrue() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.NUMERIC_GREATER_EQUAL, ImmutableList.of("-50.0"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "-50.01");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericgreaterThanToFalse() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.NUMERIC_GREATER_THAN, ImmutableList.of("-50.0"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "-50.0");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericGreaterThanToTrue() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.NUMERIC_GREATER_THAN, ImmutableList.of("-50.0"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "-49.09");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalStringContainsToTrue() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.STRING_CONTAINS, ImmutableList.of("One", "hundred"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "Two hundred");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalStringContainsToFalse() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.STRING_CONTAINS, ImmutableList.of("One", "hundred"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "Two hudred");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalStringDoesNotContainToTrue() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.STRING_DOES_NOT_CONTAIN, ImmutableList.of("One", "hundred"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "Two hudred");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalStringDoesNotContainToFalse() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.STRING_DOES_NOT_CONTAIN, ImmutableList.of("One", "hundred"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "Two hundred");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalStringExactlyMatchesToTrue() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.STRING_EXACTLY_MATCHES, ImmutableList.of("hundred"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "hundred");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalStringExactlyMatchesToFalse() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.STRING_EXACTLY_MATCHES, ImmutableList.of("hundred"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "Two hundred");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalStringContainsRegexToTrue() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.STRING_CONTAINS_REGEX, ImmutableList.of(".*hund.*"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "hundred");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalStringContainsRegexToFalse() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.STRING_CONTAINS_REGEX, ImmutableList.of("$hund.*"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "Two ahundred");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticLessThanToTrue() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.SEMANTIC_VERSION_LESS_THAN, ImmutableList.of("50.0.20"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "50.0.2.0.1");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticLessThanToFalse() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.SEMANTIC_VERSION_LESS_THAN, ImmutableList.of("50.0.20"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "50.0.20.0.0");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticLessThanInvalidVersionToFalse() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.SEMANTIC_VERSION_LESS_THAN, ImmutableList.of("50.0.-20"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "50.0.2.0.1");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticLessEqualToTrue() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.SEMANTIC_VERSION_LESS_EQUAL, ImmutableList.of("50.0.20.0.0"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "50.0.20.0.0");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticLessEqualToFalse() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.SEMANTIC_VERSION_LESS_EQUAL, ImmutableList.of("50.0.2"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "50.0.2.1.0");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticGreaterThanToTrue() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.SEMANTIC_VERSION_GREATER_THAN, ImmutableList.of("50.0.2"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "50.0.20.0.1");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticGreaterThanToFalse() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.SEMANTIC_VERSION_GREATER_THAN, ImmutableList.of("50.0.20"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "50.0.20.0.0");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticGreaterEqualToTrue() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.SEMANTIC_VERSION_GREATER_EQUAL, ImmutableList.of("50.0.20"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "50.0.20.0.0");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticGreaterEqualToFalse() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.SEMANTIC_VERSION_GREATER_EQUAL, ImmutableList.of("50.0.20.1"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "50.0.20.0.0");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticEqualToTrue() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.SEMANTIC_VERSION_EQUAL, ImmutableList.of("50.0.20"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "50.0.20.0.0");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticEqualToFalse() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.SEMANTIC_VERSION_EQUAL, ImmutableList.of("50.0.20.1"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "50.0.20.0.0");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticNotEqualToTrue() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.SEMANTIC_VERSION_NOT_EQUAL, ImmutableList.of("50.0.20.1"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "50.0.20.0.0");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticNotEqualToFalse() {
    Map<String, OneOfCondition> conditions = createCustomSignalNamedCondition(
        CustomSignalOperator.SEMANTIC_VERSION_NOT_EQUAL, ImmutableList.of("50.0.20"));
    Map<String, Object> context = new HashMap<>();
    context.put("signal_key", "50.0.20.0.0");

    ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
        context);

    assertFalse(result.get("signal_key"));
  }

  private Map<String, OneOfCondition> createCustomSignalNamedCondition(
      CustomSignalOperator operator,
      ImmutableList<String> targetCustomSignalValues) {
    CustomSignalCondition condition = new CustomSignalCondition("signal_key", operator,
        targetCustomSignalValues);
    OneOfCondition oneOfConditionCustomSignal = new OneOfCondition();
    oneOfConditionCustomSignal.setCustomSignal(condition);
    Map<String, OneOfCondition> conditions = new HashMap<>();
    conditions.put("signal_key", oneOfConditionCustomSignal);
    return conditions;
  }

  private int evaluateRandomAssignments(OneOfCondition condition, int numOfAssignments) {
    int evalTrueCount = 0;
    Map<String, OneOfCondition> conditions = new HashMap<>();
    conditions.put("is_enabled", condition);
    for (int i = 0; i < numOfAssignments; i++) {
      UUID randomizationId = UUID.randomUUID();
      Map<String, Object> context = new HashMap<>();
      context.put("randomizationId", randomizationId);

      ImmutableMap<String, Boolean> result = conditionEvaluator.evaluateConditions(conditions,
          context);

      if (result.get("is_enabled")) {
        evalTrueCount++;
      }
    }
    return evalTrueCount;
  }

  private OneOfCondition createOneOfOrCondition(OneOfCondition condition) {
    OrCondition orCondition = condition != null ? new OrCondition(ImmutableList.of(condition))
        : new OrCondition(ImmutableList.of());
    OneOfCondition oneOfCondition = new OneOfCondition();
    oneOfCondition.setOrCondition(orCondition);
    return oneOfCondition;
  }

  private OneOfCondition createOneOfAndCondition(OneOfCondition condition) {
    AndCondition andCondition = condition != null ? new AndCondition(ImmutableList.of(condition))
        : new AndCondition(ImmutableList.of());
    OneOfCondition oneOfCondition = new OneOfCondition();
    oneOfCondition.setAndCondition(andCondition);
    return oneOfCondition;
  }

  private OneOfCondition createOneOfTrueCondition() {
    OneOfCondition oneOfCondition = new OneOfCondition();
    oneOfCondition.setTrue();
    return oneOfCondition;
  }

  private OneOfCondition createOneOfFalseCondition() {
    OneOfCondition oneOfCondition = new OneOfCondition();
    oneOfCondition.setFalse();
    return oneOfCondition;
  }

  private OneOfCondition createPercentCondition(Integer microPercent,
      PercentConditionOperator operator, String seed) {
    PercentCondition percentCondition = new PercentCondition(microPercent, operator, seed);
    OneOfCondition oneOfCondition = new OneOfCondition();
    oneOfCondition.setPercent(percentCondition);
    return oneOfCondition;
  }

  private OneOfCondition createBetweenPercentCondition(Integer lowerBound, Integer upperBound,
      String seed) {
    MicroPercentRange microPercentRange = new MicroPercentRange(lowerBound, upperBound);
    PercentCondition percentCondition = new PercentCondition(microPercentRange,
        PercentConditionOperator.BETWEEN, seed);
    OneOfCondition oneOfCondition = new OneOfCondition();
    oneOfCondition.setPercent(percentCondition);
    return oneOfCondition;
  }
}
