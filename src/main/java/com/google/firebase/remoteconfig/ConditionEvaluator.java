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
import com.google.firebase.internal.Nullable;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ConditionEvaluator {
  private static final int MAX_CONDITION_RECURSION_DEPTH = 10;
  private static final Logger logger = LoggerFactory.getLogger(ConditionEvaluator.class);

  @NonNull
  Map<String, Boolean> evaluateConditions(
      @NonNull List<ServerCondition> conditions,
      @Nullable KeysAndValues context) {
    checkNotNull(conditions, "List of conditions must not be null.");
    checkArgument(!conditions.isEmpty(), "List of conditions must not be empty.");
    KeysAndValues evaluationContext = context != null 
        ? context 
        : new KeysAndValues.Builder().build();
   
    Map<String, Boolean> evaluatedConditions = conditions.stream()
        .collect(Collectors.toMap(
            ServerCondition::getName,
            condition -> 
                evaluateCondition(condition.getCondition(), evaluationContext, /* nestingLevel= */0)
        ));
    
    return evaluatedConditions;
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
    logger.atWarn().log("Received invalid condition for evaluation.");
    return false;
  }


  private boolean evaluateOrCondition(OrCondition condition, KeysAndValues context,
      int nestingLevel) {
    return condition.getConditions().stream()
        .anyMatch(subCondition -> evaluateCondition(subCondition, context, nestingLevel + 1));
  }

  private boolean evaluateAndCondition(AndCondition condition, KeysAndValues context,
      int nestingLevel) {
    return condition.getConditions().stream()
        .allMatch(subCondition -> evaluateCondition(subCondition, context, nestingLevel + 1));
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

    String customSignalValue = context.get(customSignalKey);
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

  private BigInteger getMicroPercentile(String seed, String randomizationId) {
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

  private boolean compareStrings(ImmutableList<String> targetValues, String customSignal,
                               BiPredicate<String, String> compareFunction) {
    return targetValues.stream().anyMatch(targetValue -> 
              compareFunction.test(customSignal, targetValue));
  }

  private boolean compareNumbers(ImmutableList<String> targetValues, String customSignal,
                             IntPredicate compareFunction) {
    if (targetValues.size() != 1) {
      logger.warn(String.format(
          "Target values must contain 1 element for numeric operations. Target Value: %s",
          targetValues));
      return false;
    }

    try {
      double customSignalDouble = Double.parseDouble(customSignal);
      double targetValue = Double.parseDouble(targetValues.get(0));
      int comparisonResult = Double.compare(customSignalDouble, targetValue);
      return compareFunction.test(comparisonResult);
    } catch (NumberFormatException e) {
      logger.warn("Error parsing numeric values: customSignal=%s, targetValue=%s",
          customSignal, targetValues.get(0), e);
      return false;
    }
  }

  private boolean compareSemanticVersions(ImmutableList<String> targetValues,
                                      String customSignal,
                                      IntPredicate compareFunction) {
    if (targetValues.size() != 1) {
      logger.warn(String.format("Target values must contain 1 element for semantic operation."));
      return false;
    }

    String targetValueString = targetValues.get(0);
    if (!validateSemanticVersion(targetValueString)
        || !validateSemanticVersion(customSignal)) {
      return false;
    }

    List<Integer> targetVersion = parseSemanticVersion(targetValueString);
    List<Integer> customSignalVersion = parseSemanticVersion(customSignal);

    int maxLength = 5;
    if (targetVersion.size() > maxLength || customSignalVersion.size() > maxLength) {
      logger.warn("Semantic version max length(%s) exceeded. Target: %s, Custom Signal: %s",
          maxLength, targetValueString, customSignal);
      return false;
    }

    int comparison = compareSemanticVersions(customSignalVersion, targetVersion);
    return compareFunction.test(comparison);
  }

  private int compareSemanticVersions(List<Integer> version1, List<Integer> version2) {
    int maxLength = Math.max(version1.size(), version2.size());
    int version1Size = version1.size();
    int version2Size = version2.size();

    for (int i = 0; i < maxLength; i++) {
      // Default to 0 if segment is missing
      int v1 =  i < version1Size ? version1.get(i) : 0;
      int v2 =  i < version2Size ? version2.get(i) : 0;

      int comparison = Integer.compare(v1, v2);
      if (comparison != 0) {
        return comparison;
      }
    }
    // Versions are equal
    return 0;
  }

  private List<Integer> parseSemanticVersion(String versionString) {
    return Arrays.stream(versionString.split("\\."))
          .map(Integer::parseInt)
          .collect(Collectors.toList());
  }

  private boolean validateSemanticVersion(String version) {
    Pattern pattern = Pattern.compile("^[0-9]+(?:\\.[0-9]+){0,4}$");
    return pattern.matcher(version).matches();
  }
}
