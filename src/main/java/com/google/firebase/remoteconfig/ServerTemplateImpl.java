/*
 * Copyright 2025 Google LLC
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

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import com.google.firebase.remoteconfig.internal.TemplateResponse.ParameterValueResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServerTemplateImpl implements ServerTemplate {

  private final KeysAndValues defaultConfig;
  private FirebaseRemoteConfigClient client;
  private ServerTemplateData cache;
  private final AtomicReference<String> cachedTemplate; // Added field for cached template
  private static final Logger logger = LoggerFactory.getLogger(ServerTemplate.class);

  public static class Builder implements ServerTemplate.Builder {
    private KeysAndValues defaultConfig;
    private String cachedTemplate;
    private FirebaseRemoteConfigClient client;
  
    Builder(FirebaseRemoteConfigClient remoteConfigClient) {
      this.client = remoteConfigClient;
    }

    @Override
    public Builder defaultConfig(KeysAndValues config) {
      this.defaultConfig = config;
      return this;
    }

    @Override
    public Builder cachedTemplate(String templateJson) {
      this.cachedTemplate = templateJson;
      return this;
    }

    @Override
    public ServerTemplate build() {
      return new ServerTemplateImpl(this);
    }
  }

  private ServerTemplateImpl(Builder builder) {
    this.defaultConfig = builder.defaultConfig;
    this.cachedTemplate = new AtomicReference<>(builder.cachedTemplate);
    this.client = builder.client;
    try {
      this.cache = ServerTemplateData.fromJSON(this.cachedTemplate.get());
    } catch (FirebaseRemoteConfigException e) {
      e.printStackTrace();
    }
  }

  @Override
  public ServerConfig evaluate(KeysAndValues context) throws FirebaseRemoteConfigException {
    if (this.cache == null) {
      throw new FirebaseRemoteConfigException(ErrorCode.FAILED_PRECONDITION,
          "No Remote Config Server template in cache. Call load() before calling evaluate().");
    }

    Map<String, Value> configValues = new HashMap<>();
    ImmutableMap<String, String> defaultConfigValues = defaultConfig.keysAndValues;
    // Initializes config Value objects with default values.
    for (String configName : defaultConfigValues.keySet()) {
      configValues.put(configName, new Value(ValueSource.DEFAULT,
          defaultConfigValues.get(configName)));
    }

    ConditionEvaluator conditionEvaluator = new ConditionEvaluator();
    ImmutableMap<String, Boolean> evaluatedCondition = ImmutableMap.copyOf(
        conditionEvaluator.evaluateConditions(cache.getServerConditions(), context));
    ImmutableMap<String, Parameter> parameters = ImmutableMap.copyOf(cache.getParameters());
    mergeDerivedConfigValues(evaluatedCondition, parameters, configValues);

    return new ServerConfig(configValues);
  }

  @Override
  public ServerConfig evaluate() throws FirebaseRemoteConfigException {
    KeysAndValues context = new KeysAndValues.Builder().build();
    return evaluate(context);
  }

  @Override
  public ApiFuture<Void> load() throws FirebaseRemoteConfigException {
    String serverTemplate = client.getServerTemplate();
    this.cachedTemplate.set(serverTemplate);
    this.cache = ServerTemplateData.fromJSON(serverTemplate);
    return ApiFutures.immediateFuture(null);
  }

  // Add getters or other methods as needed
  public KeysAndValues getDefaultConfig() {
    return defaultConfig;
  }

  public String getCachedTemplate() {
    return cachedTemplate.get();
  }

  @Override
  public String toJson() {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    return gson.toJson(this.cache);
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

      ImmutableMap<String, ParameterValue> conditionalValues = ImmutableMap.copyOf(
          parameter.getConditionalValues());
      ParameterValue derivedValue = null;

      // Iterates in order over condition list. If there is a value associated
      // with a condition, this checks if the condition is true.
      for (String conditionName : evaluatedCondition.keySet()) {
        boolean conditionEvaluation = evaluatedCondition.get(conditionName);
        if (conditionalValues.containsKey(conditionName) && conditionEvaluation) {
          derivedValue = conditionalValues.get(conditionName);
          break;
        }
      }

      if (derivedValue != null && derivedValue.toParameterValueResponse().isUseInAppDefault()) {
        logger.warn(
            String.format("Derived value found for %s but parameter is set to use in app default.",
                parameterName));
        continue;
      }

      if (derivedValue != null) {
        String parameterValue = derivedValue.toParameterValueResponse().getValue();
        Value value = new Value(ValueSource.REMOTE, parameterValue);
        configValues.put(parameterName, value);
        continue;
      }

      ParameterValue defaultValue = parameter.getDefaultValue();
      if (defaultValue == null) {
        logger.warn(String.format("Default parameter value for %s is not set.",
            parameterName));
        continue;
      }

      ParameterValueResponse defaultValueResponse = defaultValue.toParameterValueResponse();
      if (defaultValueResponse != null && defaultValueResponse.isUseInAppDefault()) {
        logger.info(String.format("Default value for %s is set to use in app default.",
            parameterName));
        continue;
      }

      String parameterDefaultValue = defaultValue.toParameterValueResponse().getValue();
      Value value = new Value(ValueSource.REMOTE, parameterDefaultValue);
      configValues.put(parameterName, value);
    }
  }
}
