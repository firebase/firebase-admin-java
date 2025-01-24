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

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;

public class ConditionEvaluatorTest {

  private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();

  @Test(expected = IllegalArgumentException.class)
  public void testEvaluateConditionsEmptyOrConditionThrowsException() {
    createOneOfOrCondition(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEvaluateConditionsEmptyOrAndConditionThrowsException() {
    createOneOfAndCondition(null);
  }

  @Test
  public void testEvaluateConditionsOrAndTrueToTrue() {
    OneOfCondition oneOfConditionTrue = createOneOfTrueCondition();
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionTrue);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    ServerCondition condition = new ServerCondition("is_enabled", oneOfConditionOr);
    KeysAndValues context = new KeysAndValues.Builder().build();

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        context);

    assertTrue(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsOrAndFalseToFalse() {
    OneOfCondition oneOfConditionFalse = createOneOfFalseCondition();
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionFalse);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    ServerCondition condition = new ServerCondition("is_enabled", oneOfConditionOr);
    KeysAndValues context = new KeysAndValues.Builder().build();

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        context);

    assertFalse(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsNonOrTopConditionToTrue() {
    OneOfCondition oneOfConditionTrue = createOneOfTrueCondition();
    ServerCondition condition = new ServerCondition("is_enabled", oneOfConditionTrue);
    KeysAndValues context = new KeysAndValues.Builder().build();

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        context);

    assertTrue(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsPercentConditionWithInvalidOperatorToFalse() {
    OneOfCondition oneOfConditionPercent = createPercentCondition(0,
        PercentConditionOperator.UNSPECIFIED, "seed");
    ServerCondition condition = new ServerCondition("is_enabled", oneOfConditionPercent);
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("randomizationId", "abc");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsPercentConditionLessOrEqualMaxToTrue() {
    OneOfCondition oneOfConditionPercent = createPercentCondition(10_000_0000,
        PercentConditionOperator.LESS_OR_EQUAL, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    ServerCondition condition = new ServerCondition("is_enabled", oneOfConditionOr);
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("randomizationId", "123");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsPercentConditionLessOrEqualMinToFalse() {
    OneOfCondition oneOfConditionPercent = createPercentCondition(0,
        PercentConditionOperator.LESS_OR_EQUAL, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    ServerCondition condition = new ServerCondition("is_enabled", oneOfConditionOr);
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("randomizationId", "123");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsPercentConditionUndefinedMicroPercentToFalse() {
    OneOfCondition oneOfConditionPercent = createPercentCondition(null,
        PercentConditionOperator.LESS_OR_EQUAL, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    ServerCondition condition = new ServerCondition("is_enabled", oneOfConditionOr);
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("randomizationId", "123");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsUseZeroForUndefinedPercentRange() {
    OneOfCondition oneOfConditionPercent = createBetweenPercentCondition(null, null, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    ServerCondition condition = new ServerCondition("is_enabled", oneOfConditionOr);
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("randomizationId", "123");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsUseZeroForUndefinedUpperBound() {
    OneOfCondition oneOfConditionPercent = createBetweenPercentCondition(0, null, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    ServerCondition condition = new ServerCondition("is_enabled", oneOfConditionOr);
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("randomizationId", "123");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("is_enabled"));
  }

  @Test
  public void testEvaluateConditionsUseZeroForUndefinedLowerBound() {
    OneOfCondition oneOfConditionPercent = createBetweenPercentCondition(null, 10_000_0000, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    ServerCondition condition = new ServerCondition("is_enabled", oneOfConditionOr);
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("randomizationId", "123");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("is_enabled"));
  }

  @Test
  public void testEvaluatedConditionsGreaterThanMinToTrue() {
    OneOfCondition oneOfConditionPercent = createPercentCondition(0,
        PercentConditionOperator.GREATER_THAN, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    ServerCondition condition = new ServerCondition("is_enabled", oneOfConditionOr);
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("randomizationId", "123");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("is_enabled"));
  }

  @Test
  public void testEvaluatedConditionsGreaterThanMaxToFalse() {
    OneOfCondition oneOfConditionPercent = createPercentCondition(10_000_0000,
        PercentConditionOperator.GREATER_THAN, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    ServerCondition condition = new ServerCondition("is_enabled", oneOfConditionOr);
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("randomizationId", "123");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("is_enabled"));
  }

  @Test
  public void testEvaluatedConditionsBetweenMinAndMaxToTrue() {
    OneOfCondition oneOfConditionPercent = createBetweenPercentCondition(0, 10_000_0000, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    ServerCondition condition = new ServerCondition("is_enabled", oneOfConditionOr);
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("randomizationId", "123");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("is_enabled"));
  }

  @Test
  public void testEvaluatedConditionsBetweenEqualBoundsToFalse() {
    OneOfCondition oneOfConditionPercent = createBetweenPercentCondition(5_000_000,
        5_000_000, "seed");
    OneOfCondition oneOfConditionAnd = createOneOfAndCondition(oneOfConditionPercent);
    OneOfCondition oneOfConditionOr = createOneOfOrCondition(oneOfConditionAnd);
    ServerCondition condition = new ServerCondition("is_enabled", oneOfConditionOr);
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("randomizationId", "123");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

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
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.NUMERIC_LESS_THAN, ImmutableList.of("-50.0"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "-50.0");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericLessThanToTrue() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.NUMERIC_LESS_THAN, ImmutableList.of("-50.0"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "-50.01");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalInvalidValueNumericOperationToFalse() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.NUMERIC_LESS_THAN, ImmutableList.of("non-numeric"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "-50.01");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericLessEqualToFalse() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.NUMERIC_LESS_EQUAL, ImmutableList.of("-50.0"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "-50.0");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericLessEqualToTrue() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.NUMERIC_LESS_EQUAL, ImmutableList.of("-50.0"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "-49.9");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericEqualsToTrue() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.NUMERIC_EQUAL, ImmutableList.of("50.0"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "50.0");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericEqualsToFalse() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.NUMERIC_EQUAL, ImmutableList.of("50.0"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "50.000001");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericNotEqualsToTrue() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.NUMERIC_NOT_EQUAL, ImmutableList.of("50.0"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "50.0");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericNotEqualsToFalse() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.NUMERIC_NOT_EQUAL, ImmutableList.of("50.0"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "50.000001");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericGreaterEqualToFalse() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.NUMERIC_GREATER_EQUAL, ImmutableList.of("-50.0"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "-50.0");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericGreaterEqualToTrue() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.NUMERIC_GREATER_EQUAL, ImmutableList.of("-50.0"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "-50.01");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericgreaterThanToFalse() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.NUMERIC_GREATER_THAN, ImmutableList.of("-50.0"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "-50.0");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalNumericGreaterThanToTrue() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.NUMERIC_GREATER_THAN, ImmutableList.of("-50.0"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "-49.09");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalStringContainsToTrue() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.STRING_CONTAINS, ImmutableList.of("One", "hundred"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "Two hundred");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalStringContainsToFalse() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.STRING_CONTAINS, ImmutableList.of("One", "hundred"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "Two hudred");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalStringDoesNotContainToTrue() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.STRING_DOES_NOT_CONTAIN, ImmutableList.of("One", "hundred"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "Two hudred");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalStringDoesNotContainToFalse() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.STRING_DOES_NOT_CONTAIN, ImmutableList.of("One", "hundred"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "Two hundred");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalStringExactlyMatchesToTrue() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.STRING_EXACTLY_MATCHES, ImmutableList.of("hundred"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "hundred");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalStringExactlyMatchesToFalse() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.STRING_EXACTLY_MATCHES, ImmutableList.of("hundred"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "Two hundred");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalStringContainsRegexToTrue() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.STRING_CONTAINS_REGEX, ImmutableList.of(".*hund.*"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "hundred");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionsCustomSignalStringContainsRegexToFalse() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.STRING_CONTAINS_REGEX, ImmutableList.of("$hund.*"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "Two ahundred");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticLessThanToTrue() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.SEMANTIC_VERSION_LESS_THAN, ImmutableList.of("50.0.20"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "50.0.2.0.1");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticLessThanToFalse() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.SEMANTIC_VERSION_LESS_THAN, ImmutableList.of("50.0.20"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "50.0.20.0.0");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticLessThanInvalidVersionToFalse() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.SEMANTIC_VERSION_LESS_THAN, ImmutableList.of("50.0.-20"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "50.0.2.0.1");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticLessEqualToTrue() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.SEMANTIC_VERSION_LESS_EQUAL, ImmutableList.of("50.0.20.0.0"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "50.0.20.0.0");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticLessEqualToFalse() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.SEMANTIC_VERSION_LESS_EQUAL, ImmutableList.of("50.0.2"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "50.0.2.1.0");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticGreaterThanToTrue() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.SEMANTIC_VERSION_GREATER_THAN, ImmutableList.of("50.0.2"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "50.0.20.0.1");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticGreaterThanToFalse() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.SEMANTIC_VERSION_GREATER_THAN, ImmutableList.of("50.0.20"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "50.0.20.0.0");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticGreaterEqualToTrue() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.SEMANTIC_VERSION_GREATER_EQUAL, ImmutableList.of("50.0.20"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "50.0.20.0.0");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticGreaterEqualToFalse() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.SEMANTIC_VERSION_GREATER_EQUAL, ImmutableList.of("50.0.20.1"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "50.0.20.0.0");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticEqualToTrue() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.SEMANTIC_VERSION_EQUAL, ImmutableList.of("50.0.20"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "50.0.20.0.0");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticEqualToFalse() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.SEMANTIC_VERSION_EQUAL, ImmutableList.of("50.0.20.1"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "50.0.20.0.0");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticNotEqualToTrue() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.SEMANTIC_VERSION_NOT_EQUAL, ImmutableList.of("50.0.20.1"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "50.0.20.0.0");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertTrue(result.get("signal_key"));
  }

  @Test
  public void testEvaluateConditionCustomSignalSemanticNotEqualToFalse() {
    ServerCondition condition = createCustomSignalServerCondition(
        CustomSignalOperator.SEMANTIC_VERSION_NOT_EQUAL, ImmutableList.of("50.0.20"));
    KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
    contextBuilder.put("signal_key", "50.0.20.0.0");

    Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
        contextBuilder.build());

    assertFalse(result.get("signal_key"));
  }

  private ServerCondition createCustomSignalServerCondition(
      CustomSignalOperator operator,
      ImmutableList<String> targetCustomSignalValues) {
    CustomSignalCondition condition = new CustomSignalCondition("signal_key", operator,
        targetCustomSignalValues);
    OneOfCondition oneOfConditionCustomSignal = new OneOfCondition();
    oneOfConditionCustomSignal.setCustomSignal(condition);
    return new ServerCondition("signal_key", oneOfConditionCustomSignal);
  }

  private int evaluateRandomAssignments(OneOfCondition percentCondition, int numOfAssignments) {
    int evalTrueCount = 0;
    ServerCondition condition = new ServerCondition("is_enabled", percentCondition);
    for (int i = 0; i < numOfAssignments; i++) {
      UUID randomizationId = UUID.randomUUID();
      KeysAndValues.Builder contextBuilder = new KeysAndValues.Builder();
      contextBuilder.put("randomizationId", randomizationId.toString());

      Map<String, Boolean> result = conditionEvaluator.evaluateConditions(Arrays.asList(condition),
          contextBuilder.build());

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
