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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

public class ConditionEvaluatorTest {

  private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();

  @Test
  public void testEvaluateConditionsEmptyOrConditionThrowsException() {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> createOneOfOrCondition(null));
    assertEquals("List of conditions for OR operation must not be empty.", error.getMessage());
  }

  @Test
  public void testEvaluateConditionsEmptyOrAndConditionThrowsException() {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> createOneOfAndCondition(null));
    assertEquals("List of conditions for AND operation must not be empty.", error.getMessage());
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
}
