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
import com.google.common.collect.ImmutableMap;
import com.google.firebase.internal.NonNull;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.management.RuntimeErrorException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ConditionEvaluator {
  private static final int MAX_CONDITION_RECURSION_DEPTH = 10;
  private static final Logger logger = LoggerFactory.getLogger(ConditionEvaluator.class);

  @FunctionalInterface
  interface CompareStringFunction {
    boolean apply(String signalVaue, String targetValue);
  }

  @FunctionalInterface
  interface CompareNumberFunction {
    boolean apply(Integer value);
  }

  @NonNull
  Map<String, Boolean> evaluateConditions(
      @NonNull List<ServerCondition> conditions,
      @NonNull KeysAndValues context) {
    checkNotNull(conditions, "List of conditions must not be null.");
    checkArgument(!conditions.isEmpty(), "List of conditions must not be empty.");
    checkNotNull(context, "Context must not be null.");

    ImmutableList<ServerCondition> serverConditions = ImmutableList.copyOf(conditions);
    ImmutableMap.Builder<String, Boolean> evaluatedConditions = ImmutableMap.builder();
    int nestingLevel = 0;

    for (ServerCondition condition : serverConditions) {
      evaluatedConditions.put(condition.getName(), evaluateCondition(condition.getCondition(),
          context, nestingLevel));
    }

    return new HashMap<>(evaluatedConditions.build());
  }

  private boolean evaluateCondition(OneOfCondition condition, KeysAndValues context,
      int nestingLevel) {
    if (nestingLevel > MAX_CONDITION_RECURSION_DEPTH) {
      logger.warn("Maximum condition recursion depth exceeded.");
      return false;
    }

    if (condition.getOrCondition() != null) {
      return evaluateOrCondition(condition.getOrCondition(), context, nestingLevel + 1);
    } else if (condition.getAndCondition() != null) {
      return evaluateAndCondition(condition.getAndCondition(), context, nestingLevel + 1);
    } else if (condition.isTrue() != null) {
      return true;
    } else if (condition.isFalse() != null) {
      return false;
    } else if (condition.getPercent() != null) {
      return evaluatePercentCondition(condition.getPercent(), context);
    } else if (condition.getCustomSignal() != null) {
      return evaluateCustomSignalCondition(condition.getCustomSignal(), context);
    }
    return false;
  }

  private boolean evaluateOrCondition(OrCondition condition, KeysAndValues context,
      int nestingLevel) {
    ImmutableList<OneOfCondition> subConditions = ImmutableList.copyOf(condition.getConditions());
    for (OneOfCondition subCondition : subConditions) {
      // Short-circuit the evaluation result for true.
      if (evaluateCondition(subCondition, context, nestingLevel + 1)) {
        return true;
      }
    }
    return false;
  }

  private boolean evaluateAndCondition(AndCondition condition,
      KeysAndValues context,
      int nestingLevel) {
    ImmutableList<OneOfCondition> subConditions = ImmutableList.copyOf(condition.getConditions());
    for (OneOfCondition subCondition : subConditions) {
      // Short-circuit the evaluation result for false.
      if (!evaluateCondition(subCondition, context, nestingLevel + 1)) {
        return false;
      }
    }
    return true;
  }

  private boolean evaluatePercentCondition(PercentCondition condition,
      KeysAndValues context) {
    if (!context.containsKey("randomizationId")) {
      logger.warn("Percentage operation must not be performed without randomizationId");
      return false;
    }

    PercentConditionOperator operator = condition.getPercentConditionOperator();

    // The micro-percent interval to be used with the BETWEEN operator.
    MicroPercentRange microPercentRange = condition.getMicroPercentRange();
    int microPercentUpperBound = microPercentRange != null
        ? microPercentRange.getMicroPercentUpperBound()
        : 0;
    int microPercentLowerBound = microPercentRange != null
        ? microPercentRange.getMicroPercentLowerBound()
        : 0;
    // The limit of percentiles to target in micro-percents when using the
    // LESS_OR_EQUAL and GREATER_THAN operators. The value must be in the range [0
    // and 100000000].
    int microPercent = condition.getMicroPercent();
    BigInteger microPercentile = getMicroPercentile(condition.getSeed(),
        context.get("randomizationId"));
    switch (operator) {
      case LESS_OR_EQUAL:
        return microPercentile.compareTo(BigInteger.valueOf(microPercent)) <= 0;
      case GREATER_THAN:
        return microPercentile.compareTo(BigInteger.valueOf(microPercent)) > 0;
      case BETWEEN:
        return microPercentile.compareTo(BigInteger.valueOf(microPercentLowerBound)) > 0
            && microPercentile.compareTo(BigInteger.valueOf(microPercentUpperBound)) <= 0;
      case UNSPECIFIED:
      default:
        return false;
    }
  }

  private boolean evaluateCustomSignalCondition(CustomSignalCondition condition,
      KeysAndValues context) {
    CustomSignalOperator customSignalOperator = condition.getCustomSignalOperator();
    String customSignalKey = condition.getCustomSignalKey();
    ImmutableList<String> targetCustomSignalValues = ImmutableList.copyOf(
        condition.getTargetCustomSignalValues());

    if (targetCustomSignalValues.isEmpty()) {
      logger.warn(String.format(
          "Values must be assigned to all custom signal fields. Operator:%s, Key:%s, Values:%s",
          customSignalOperator, customSignalKey, targetCustomSignalValues));
      return false;
    }

    Object customSignalValue = context.get(customSignalKey);
    if (customSignalValue == null) {
      return false;
    }

    switch (customSignalOperator) {
      // String operations.
      case STRING_CONTAINS:
        return compareStrings(targetCustomSignalValues, customSignalValue,
            (customSignal, targetSignal) -> customSignal.contains(targetSignal));
      case STRING_DOES_NOT_CONTAIN:
        return !compareStrings(targetCustomSignalValues, customSignalValue,
            (customSignal, targetSignal) -> customSignal.contains(targetSignal));
      case STRING_EXACTLY_MATCHES:
        return compareStrings(targetCustomSignalValues, customSignalValue,
            (customSignal, targetSignal) -> customSignal.equals(targetSignal));
      case STRING_CONTAINS_REGEX:
        return compareStrings(targetCustomSignalValues, customSignalValue,
            (customSignal, targetSignal) -> Pattern.compile(targetSignal)
                .matcher(customSignal).matches());

      // Numeric operations.
      case NUMERIC_LESS_THAN:
        return compareNumbers(targetCustomSignalValues, customSignalValue,
            (result) -> result < 0);
      case NUMERIC_LESS_EQUAL:
        return compareNumbers(targetCustomSignalValues, customSignalValue,
            (result) -> result <= 0);
      case NUMERIC_EQUAL:
        return compareNumbers(targetCustomSignalValues, customSignalValue,
            (result) -> result == 0);
      case NUMERIC_NOT_EQUAL:
        return compareNumbers(targetCustomSignalValues, customSignalValue,
            (result) -> result != 0);
      case NUMERIC_GREATER_THAN:
        return compareNumbers(targetCustomSignalValues, customSignalValue,
            (result) -> result > 0);
      case NUMERIC_GREATER_EQUAL:
        return compareNumbers(targetCustomSignalValues, customSignalValue,
            (result) -> result >= 0);

      // Semantic operations.
      case SEMANTIC_VERSION_EQUAL:
        return compareSemanticVersions(targetCustomSignalValues, customSignalValue,
            (result) -> result == 0);
      case SEMANTIC_VERSION_GREATER_EQUAL:
        return compareSemanticVersions(targetCustomSignalValues, customSignalValue,
            (result) -> result >= 0);
      case SEMANTIC_VERSION_GREATER_THAN:
        return compareSemanticVersions(targetCustomSignalValues, customSignalValue,
            (result) -> result > 0);
      case SEMANTIC_VERSION_LESS_EQUAL:
        return compareSemanticVersions(targetCustomSignalValues, customSignalValue,
            (result) -> result <= 0);
      case SEMANTIC_VERSION_LESS_THAN:
        return compareSemanticVersions(targetCustomSignalValues, customSignalValue,
            (result) -> result < 0);
      case SEMANTIC_VERSION_NOT_EQUAL:
        return compareSemanticVersions(targetCustomSignalValues, customSignalValue,
            (result) -> result != 0);
      default:
        return false;
    }
  }

  private BigInteger getMicroPercentile(String seed, Object randomizationId) {
    String seedPrefix = seed != null && !seed.isEmpty() ? seed + "." : "";
    String stringToHash = seedPrefix + randomizationId;
    BigInteger hash = hashSeededRandomizationId(stringToHash);
    BigInteger modValue = new BigInteger(Integer.toString(100 * 1_000_000));
    BigInteger microPercentile = hash.mod(modValue);

    return microPercentile;
  }

  private BigInteger hashSeededRandomizationId(String seededRandomizationId) {
    try {
      // Create a SHA-256 hash.
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(seededRandomizationId.getBytes(StandardCharsets.UTF_8));

      // Convert the hash bytes to a hexadecimal string.
      StringBuilder hexString = new StringBuilder();
      for (byte b : hashBytes) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }

      // Convert the hexadecimal string to a BigInteger
      return new BigInteger(hexString.toString(), 16);

    } catch (NoSuchAlgorithmException e) {
      logger.error("SHA-256 algorithm not found", e);
      throw new RuntimeException("SHA-256 algorithm not found", e);
    }
  }

  private boolean compareStrings(ImmutableList<String> targetValues, Object customSignal,
      CompareStringFunction compareFunction) {
    String customSignalValue = customSignal.toString();
    for (String targetValue : targetValues) {
      if (compareFunction.apply(customSignalValue, targetValue)) {
        return true;
      }
    }
    return false;
  }

  private boolean compareNumbers(ImmutableList<String> targetValues, Object customSignal,
      CompareNumberFunction compareFunction) {
    if (targetValues.size() != 1) {
      logger.warn(String.format(
          "Target values must contain 1 element for numeric operations. Target Value: %s",
          targetValues));
      return false;
    }

    double customSignalValue;
    double targetValue;
    try {
      customSignalValue = Double.parseDouble(customSignal.toString());
      targetValue = Double.parseDouble(targetValues.get(0));
    } catch (NumberFormatException e) {
      return false;
    }

    return compareFunction.apply(customSignalValue < targetValue ? -1
        : customSignalValue > targetValue ? 1 : 0);
  }

  private boolean compareSemanticVersions(ImmutableList<String> targetValues,
      Object customSignalValue,
      CompareNumberFunction compareFunction) throws RuntimeErrorException {
    if (targetValues.size() != 1) {
      logger.warn(String.format("Target values must contain 1 element for semantic operation."));
      return false;
    }

    String targetValueString = targetValues.get(0);
    String customSignalValueString = customSignalValue.toString();
    if (!validateSemanticVersion(targetValueString)
        || !validateSemanticVersion(customSignalValueString)) {
      return false;
    }

    // Max number of segments a numeric version can have. This is enforced by the
    // server as well.
    int maxLength = 5;
    List<Integer> targetVersion = Arrays.stream(targetValueString.split("\\."))
        .map(Integer::parseInt)
        .collect(Collectors.toList());
    List<Integer> customSignalVersion = Arrays.stream(customSignalValueString.split("\\."))
        .map(Integer::parseInt)
        .collect(Collectors.toList());

    int targetVersionSize = targetVersion.size();
    int customSignalVersionSize = customSignalVersion.size();
    for (int i = 0; i <= maxLength; i++) {
      // Check to see if segments are present.
      Boolean targetVersionHasSegment = (i < targetVersionSize);
      Boolean customSignalVersionHasSegment = (i < customSignalVersionSize);

      // If both are undefined, we've consumed everything and they're equal.
      if (!targetVersionHasSegment && !customSignalVersionHasSegment) {
        return compareFunction.apply(0);
      }

      // Insert zeros if undefined for easier comparison.
      if (!targetVersionHasSegment) {
        targetVersion.add(0);
      }
      if (!customSignalVersionHasSegment) {
        customSignalVersion.add(0);
      }

      // Check if we have a difference in segments. Otherwise continue to next
      // segment.
      if (customSignalVersion.get(i).compareTo(targetVersion.get(i)) < 0) {
        return compareFunction.apply(-1);
      } else if (customSignalVersion.get(i).compareTo(targetVersion.get(i)) > 0) {
        return compareFunction.apply(1);
      }
    }
    logger.warn(String.format(
        "Semantic version max length(5) exceeded. Target: %s, Custom Signal: %s",
        targetValueString, customSignalValueString));
    return false;
  }

  private boolean validateSemanticVersion(String version) {
    Pattern pattern = Pattern.compile("^[0-9]+(?:\\.[0-9]+){0,4}$");
    return pattern.matcher(version).matches();
  }
}
