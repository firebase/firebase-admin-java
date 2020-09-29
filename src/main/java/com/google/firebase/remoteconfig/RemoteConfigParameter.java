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

import com.google.firebase.remoteconfig.internal.TemplateResponse.ParameterResponse;
import com.google.firebase.remoteconfig.internal.TemplateResponse.ParameterValueResponse;

import java.util.HashMap;
import java.util.Map;

public final class RemoteConfigParameter {

  private RemoteConfigParameterValue defaultValue;
  private String description;
  private Map<String, RemoteConfigParameterValue> conditionalValues;

  public RemoteConfigParameter() {
    conditionalValues = new HashMap<>();
    defaultValue = ExplicitParameterValue.of("");
  }

  public RemoteConfigParameterValue getDefaultValue() {
    return defaultValue;
  }

  public String getDescription() {
    return description;
  }

  public Map<String, RemoteConfigParameterValue> getConditionalValues() {
    return conditionalValues;
  }

  public RemoteConfigParameter setDefaultValue(RemoteConfigParameterValue value) {
    defaultValue = value;
    return this;
  }

  public RemoteConfigParameter setDescription(String description) {
    this.description = description;
    return this;
  }

  public RemoteConfigParameter setConditionalValues(
          Map<String, RemoteConfigParameterValue> conditionalValues) {
    this.conditionalValues = conditionalValues;
    return this;
  }

  ParameterResponse toResponseType() {
    Map<String, ParameterValueResponse> conditionalResponseValues = new HashMap<>();
    for (Map.Entry<String, RemoteConfigParameterValue> entry : conditionalValues.entrySet()) {
      conditionalResponseValues.put(entry.getKey(), entry.getValue().toResponseType());
    }
    return new ParameterResponse(defaultValue.toResponseType(), description,
            conditionalResponseValues);
  }
}
