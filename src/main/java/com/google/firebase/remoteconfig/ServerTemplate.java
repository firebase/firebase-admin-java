package com.google.firebase.remoteconfig;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import com.google.firebase.internal.Nullable;
import com.google.firebase.remoteconfig.Value.ValueSource;

/**
 * Represents a stateful abstraction for a Remote Config server template.
 */
public class ServerTemplate {
    private ServerTemplateData cache;
    private Map<String, Value> stringifiedDefaultConfig;
    private final ConditionEvaluator conditionEvaluator;
    private static final Logger logger = LoggerFactory.getLogger(ConditionEvaluator.class);

    /**
     * Creates a new {@link ServerTemplate}.
     * 
     * @param conditionEvaluator
     * @param defaultConfig
     */
    public ServerTemplate(ConditionEvaluator conditionEvaluator, ImmutableMap<String, Value> defaultConfig) {
        for (String configName : defaultConfig.keySet()) {
            stringifiedDefaultConfig.put(configName, defaultConfig.get(configName));
        }
        this.conditionEvaluator = conditionEvaluator;
    }

    /**
     * Evaluates the current server template in cache to produce a
     * {@link ServerConfig}.
     * 
     * @param context
     * @return evaluated server config.
     * @throws FirebaseRemoteConfigException
     */
    public ServerConfig evaluate(@Nullable ImmutableMap<String, Object> context) throws FirebaseRemoteConfigException {
        if (cache == null) {
            throw new FirebaseRemoteConfigException(ErrorCode.FAILED_PRECONDITION,
                    "No Remote Config Server template in cache. Call load() before calling evaluate().");
        }

        ImmutableMap<String, Boolean> evaluatedCondition = conditionEvaluator
                .evaluateConditions(ImmutableMap.copyOf(cache.getConditions()), context);

        Map<String, Value> configValues = new HashMap<>();
        // Initializes config Value objects with default values.
        for (String configName : stringifiedDefaultConfig.keySet()) {
            configValues.put(configName, stringifiedDefaultConfig.get(configName));
        }

        ImmutableMap<String, Parameter> parameters = ImmutableMap.copyOf(cache.getParameters());
        mergeDerivedConfigValues(evaluatedCondition, parameters, configValues);
        return new ServerConfig(configValues);
    }

    private void mergeDerivedConfigValues(ImmutableMap<String, Boolean> evaluatedCondition,
            ImmutableMap<String, Parameter> parameters, Map<String, Value> configValues) {
        // Overlays config Value objects derived by evaluating the template.
        for (String parameterName : parameters.keySet()) {
            Parameter parameter = parameters.get(parameterName);
            if (parameter == null) {
                logger.warn(String.format("Parameter value is not assigned for %s", parameterName));
                continue;
            }

            Map<String, ParameterValue> conditionalValues = parameter.getConditionalValues();
            ParameterValue defaultValue = parameter.getDefaultValue();
            ParameterValue derivedValue = null;

            for (String conditionName : evaluatedCondition.keySet()) {
                boolean conditionEvaluation = evaluatedCondition.get(conditionName);
                if (conditionalValues.containsKey(conditionName) && conditionEvaluation) {
                    derivedValue = conditionalValues.get(conditionName);
                    break;
                }
            }

            if (derivedValue != null && derivedValue.toParameterValueResponse().isUseInAppDefault()) {
                logger.warn(
                        String.format("Derived value for %s is set to use in app default.", parameterName));
                continue;
            }

            if (derivedValue != null) {
                String parameterValue = derivedValue.toParameterValueResponse().getValue();
                Value value = new Value(ValueSource.REMOTE, parameterValue);
                configValues.put(parameterName, value);
                continue;
            }

            if (defaultValue == null) {
                logger.warn(String.format("Default parameter value for %s is set to null.", parameterName));
                continue;
            }

            if (defaultValue.toParameterValueResponse().isUseInAppDefault()) {
                logger.info(String.format("Default value for %s is set to use in app default.", parameterName));
                continue;
            }

            String parameterDefaultValue = defaultValue.toParameterValueResponse().getValue();
            Value value = new Value(ValueSource.REMOTE, parameterDefaultValue);
            configValues.put(parameterName, value);
        }
    }
}