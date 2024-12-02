package com.google.firebase.remoteconfig;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.management.RuntimeErrorException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.internal.NonNull;

/**
 * Encapsulates condition evaluation logic to simplify organization and
 * facilitate testing.
 */
public class ConditionEvaluator {
    private static final int MAX_CONDITION_RECURSION_DEPTH = 10;
    private static final Logger logger = LoggerFactory.getLogger(ConditionEvaluator.class);

    /**
     * @param conditions A named condition map of string to {@link OneOfCondition}.
     * @param context    Represents template evaluation input signals which can be
     *                   user provided or predefined.
     * @return Evaluated conditions represented as map of condition name to boolean.
     */
    @NonNull
    public ImmutableMap<String, Boolean> evaluateConditions(@NonNull ImmutableMap<String, OneOfCondition> conditions,
            @NonNull ImmutableMap<String, Object> context) {
        ImmutableMap.Builder<String, Boolean> evaluatedConditions = ImmutableMap.builder();
        int nestingLevel = 0;

        for (ImmutableMap.Entry<String, OneOfCondition> condition : conditions.entrySet()) {
            evaluatedConditions.put(condition.getKey(), evaluateCondition(condition.getValue(), context, nestingLevel));
        }

        return evaluatedConditions.build();
    }

    private boolean evaluateCondition(OneOfCondition condition, ImmutableMap<String, Object> context,
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

    private boolean evaluateOrCondition(OrCondition condition, ImmutableMap<String, Object> context, int nestingLevel) {
        ImmutableList<OneOfCondition> subConditions = condition.getConditions();
        for (OneOfCondition subCondition : subConditions) {
            // Short-circuit the evaluation result for true.
            if (evaluateCondition(subCondition, context, nestingLevel + 1)) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateAndCondition(AndCondition condition, ImmutableMap<String, Object> context,
            int nestingLevel) {
        ImmutableList<OneOfCondition> subConditions = condition.getConditions();
        for (OneOfCondition subCondition : subConditions) {
            // Short-circuit the evaluation result for false.
            if (!evaluateCondition(subCondition, context, nestingLevel + 1)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluatePercentCondition(PercentCondition condition, ImmutableMap<String, Object> context) {
        if (!context.containsKey("randomizationId")) {
            logger.warn("Percentage operation cannot be performed without randomizationId");
            return false;
        }

        PercentConditionOperator operator = condition.getPercentConditionOperator();
        if (operator == null) {
            logger.warn("Percentage operator is not set.");
            return false;
        }

        // The micro-percent interval to be used with the BETWEEN operator.
        MicroPercentRange microPercentRange = condition.getMicroPercentRange();
        int microPercentUpperBound = microPercentRange != null ? microPercentRange.getMicroPercentUpperBound() : 0;
        int microPercentLowerBound = microPercentRange != null ? microPercentRange.getMicroPercentLowerBound() : 0;
        // The limit of percentiles to target in micro-percents when using the
        // LESS_OR_EQUAL and GREATER_THAN operators. The value must be in the range [0
        // and 100000000].
        int microPercent = condition.getMicroPercent();
        BigInteger microPercentile = getMicroPercentile(condition.getSeed(), context.get("randomizationId"));
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
            ImmutableMap<String, Object> context) {
        CustomSignalOperator customSignalOperator = condition.getCustomSignalOperator();
        String customSignalKey = condition.getCustomSignalKey();
        ImmutableList<String> targetCustomSignalValues = condition.getTargetCustomSignalValues();

        if (customSignalOperator == null || customSignalKey == null || targetCustomSignalValues == null
                || targetCustomSignalValues.isEmpty()) {
            logger.warn(
                    String.format("Values must be assigned to all custom signal fields. Operator:%s, Key:%s, Values:%s",
                            customSignalOperator, customSignalKey, targetCustomSignalValues));
            return false;
        }

        Object customSignalValue = context.get(customSignalKey);
        if (customSignalValue == null) {
            return false;
        }

        switch (customSignalOperator) {
            case STRING_CONTAINS:
            case STRING_DOES_NOT_CONTAIN:
            case STRING_EXACTLY_MATCHES:
            case STRING_CONTAINS_REGEX:
                return compareStrings(targetCustomSignalValues, customSignalValue, customSignalOperator);
            case NUMERIC_LESS_THAN:
            case NUMERIC_LESS_EQUAL:
            case NUMERIC_EQUAL:
            case NUMERIC_NOT_EQUAL:
            case NUMERIC_GREATER_THAN:
            case NUMERIC_GREATER_EQUAL:
                return compareNumbers(targetCustomSignalValues, customSignalValue, customSignalOperator);
            case SEMANTIC_VERSION_EQUAL:
            case SEMANTIC_VERSION_GREATER_EQUAL:
            case SEMANTIC_VERSION_GREATER_THAN:
            case SEMANTIC_VERSION_LESS_EQUAL:
            case SEMANTIC_VERSION_LESS_THAN:
            case SEMANTIC_VERSION_NOT_EQUAL:
                return compareSemanticVersions(targetCustomSignalValues, customSignalValue, customSignalOperator);
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
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }

            // Convert the hexadecimal string to a BigInteger
            return new BigInteger(hexString.toString(), 16);

        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not found", e);
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    private boolean compareStrings(ImmutableList<String> targetValues, Object customSignalValue,
            CustomSignalOperator operator) {
        String value = customSignalValue.toString();

        for (String target : targetValues) {
            if (operator == CustomSignalOperator.STRING_CONTAINS && value.contains(target)) {
                return true;
            } else if (operator == CustomSignalOperator.STRING_DOES_NOT_CONTAIN && !value.contains(target)) {
                return true;
            } else if (operator == CustomSignalOperator.STRING_EXACTLY_MATCHES && value.equals(target)) {
                return true;
            } else if (operator == CustomSignalOperator.STRING_CONTAINS_REGEX
                    && Pattern.compile(value).matcher(target).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean compareNumbers(ImmutableList<String> targetValues, Object customSignalValue,
            CustomSignalOperator operator) {
        if (targetValues.size() != 1) {
            logger.warn(String.format("Target values must contain 1 element for operation %s", operator));
            return false;
        }

        double value = (Double) customSignalValue;
        double target = Double.parseDouble(targetValues.get(0));

        switch (operator) {
            case NUMERIC_EQUAL:
                return value == target;
            case NUMERIC_GREATER_THAN:
                return value > target;
            case NUMERIC_GREATER_EQUAL:
                return value >= target;
            case NUMERIC_LESS_EQUAL:
                return value <= target;
            case NUMERIC_LESS_THAN:
                return value < target;
            case NUMERIC_NOT_EQUAL:
                return value != target;
            default:
                return false;
        }
    }

    private boolean compareSemanticVersions(ImmutableList<String> targetValues, Object customSignalValue,
            CustomSignalOperator operator) throws RuntimeErrorException {
        if (targetValues.size() != 1) {
            logger.warn(String.format("Target values must contain 1 element for operation %s", operator));
            return false;
        }

        // Max number of segments a numeric version can have. This is enforced by the
        // server as well.
        int maxLength = 5;

        List<Integer> version1 = Arrays.stream(customSignalValue.toString().split("\\."))
                .<Integer>map(s -> Integer.valueOf(s)).collect(Collectors.toList());
        List<Integer> version2 = Arrays.stream(targetValues.get(0).split("\\.")).<Integer>map(s -> Integer.valueOf(s))
                .collect(Collectors.toList());
        for (int i = 0; i < maxLength; i++) {
            // Check to see if segments are present.
            Boolean version1HasSegment = version1.get(i) != null;
            Boolean version2HasSegment = version2.get(i) != null;

            // If both are undefined, we've consumed everything and they're equal.
            if (!version1HasSegment && !version2HasSegment) {
                return operator == CustomSignalOperator.SEMANTIC_VERSION_EQUAL;
            }

            // Insert zeros if undefined for easier comparison.
            if (!version1HasSegment)
                version1.add(i, 0);
            if (!version2HasSegment)
                version2.add(i, 0);

            switch (operator) {
                case SEMANTIC_VERSION_GREATER_EQUAL:
                    if (version1.get(i).compareTo(version2.get(i)) >= 0)
                        return true;
                    break;
                case SEMANTIC_VERSION_GREATER_THAN:
                    if (version1.get(i).compareTo(version2.get(i)) > 0)
                        return true;
                    break;
                case SEMANTIC_VERSION_LESS_EQUAL:
                    if (version1.get(i).compareTo(version2.get(i)) <= 0)
                        return true;
                    break;
                case SEMANTIC_VERSION_LESS_THAN:
                    if (version1.get(i).compareTo(version2.get(i)) < 0)
                        return true;
                    break;
                case SEMANTIC_VERSION_EQUAL:
                    if (version1.get(i).compareTo(version2.get(i)) != 0)
                        return false;
                    break;
                case SEMANTIC_VERSION_NOT_EQUAL:
                    if (version1.get(i).compareTo(version2.get(i)) != 0)
                        return true;
                    break;
                default:
                    return false;
            }
        }
        logger.warn(String.format("Semantic version max length(5) exceeded. Target: %s, Custom Signal: %s", version1,
                version2));
        return false;
    }
}
