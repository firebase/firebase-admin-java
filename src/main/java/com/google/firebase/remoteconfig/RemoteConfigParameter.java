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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import com.google.firebase.remoteconfig.internal.TemplateResponse.ParameterResponse;
import com.google.firebase.remoteconfig.internal.TemplateResponse.ParameterValueResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a Remote Config parameter that can be included in a {@link RemoteConfigTemplate}.
 * At minimum, a default value or a conditional value must be present for the
 * parameter to have any effect.
 */
public final class RemoteConfigParameter {

  private RemoteConfigParameterValue defaultValue;
  private String description;
  private Map<String, RemoteConfigParameterValue> conditionalValues;

  /**
   * Creates a new {@link RemoteConfigParameter}.
   */
  public RemoteConfigParameter() {
    conditionalValues = new HashMap<>();
  }

  protected RemoteConfigParameter(@NonNull ParameterResponse parameterResponse) {
    checkNotNull(parameterResponse);
    conditionalValues = new HashMap<>();
    if (parameterResponse.getConditionalValues() != null) {
      for (Map.Entry<String, ParameterValueResponse> entry :
              parameterResponse.getConditionalValues().entrySet()) {
        conditionalValues.put(entry.getKey(), fromParameterValueResponse(entry.getValue()));
      }
    }
    ParameterValueResponse responseDefaultValue = parameterResponse.getDefaultValue();
    defaultValue = (responseDefaultValue == null) ? null :
            fromParameterValueResponse(responseDefaultValue);
    description = parameterResponse.getDescription();
  }

  /**
   * Gets the default value of the parameter.
   *
   * @return A {@link RemoteConfigParameterValue} instance or null.
   */
  @Nullable
  public RemoteConfigParameterValue getDefaultValue() {
    return defaultValue;
  }

  /**
   * Gets the description of the parameter.
   *
   * @return The {@link String} description of the parameter or null.
   */
  @Nullable
  public String getDescription() {
    return description;
  }

  /**
   * Gets the conditional values of the parameter.
   * The condition name of the highest priority (the one listed first in the
   * {@link RemoteConfigTemplate}'s conditions list) determines the value of this parameter.
   *
   * @return A non-null map of conditional values.
   */
  @NonNull
  public Map<String, RemoteConfigParameterValue> getConditionalValues() {
    return conditionalValues;
  }

  /**
   * Sets the default value of the parameter.
   * This is the value to set the parameter to, when none of the named conditions
   * evaluate to true.
   *
   * @param value An {@link RemoteConfigParameterValue} instance.
   * @return This {@link RemoteConfigParameter}.
   */
  public RemoteConfigParameter setDefaultValue(@Nullable RemoteConfigParameterValue value) {
    defaultValue = value;
    return this;
  }

  /**
   * Sets the description of the parameter.
   * Should not be over 100 characters and may contain any Unicode characters.
   *
   * @param description The description of the parameter.
   * @return This {@link RemoteConfigParameter}.
   */
  public RemoteConfigParameter setDescription(@Nullable String description) {
    this.description = description;
    return this;
  }

  /**
   * Sets the conditional values of the parameter.
   * The condition name of the highest priority (the one listed first in the
   * {@link RemoteConfigTemplate}'s conditions list) determines the value of this parameter.
   *
   * @param conditionalValues A non-null map of conditional values.
   * @return This {@link RemoteConfigParameter}.
   */
  public RemoteConfigParameter setConditionalValues(
          @NonNull Map<String, RemoteConfigParameterValue> conditionalValues) {
    checkNotNull(conditionalValues, "conditional values must not be null.");
    this.conditionalValues = conditionalValues;
    return this;
  }

  ParameterResponse toParameterResponse() {
    Map<String, ParameterValueResponse> conditionalResponseValues = new HashMap<>();
    for (Map.Entry<String, RemoteConfigParameterValue> entry : conditionalValues.entrySet()) {
      conditionalResponseValues.put(entry.getKey(), entry.getValue().toParameterValueResponse());
    }
    ParameterValueResponse defaultValueResponse = (defaultValue == null) ? null :
            defaultValue.toParameterValueResponse();
    return new ParameterResponse()
            .setDefaultValue(defaultValueResponse)
            .setDescription(description)
            .setConditionalValues(conditionalResponseValues);
  }

  private RemoteConfigParameterValue fromParameterValueResponse(
          @NonNull ParameterValueResponse parameterValueResponse) {
    checkNotNull(parameterValueResponse);
    if (parameterValueResponse.isInAppDefaultValue() != null
            && parameterValueResponse.isInAppDefaultValue()) {
      return RemoteConfigParameterValue.inAppDefault();
    }
    return RemoteConfigParameterValue.of(parameterValueResponse.getValue());
  }
}
