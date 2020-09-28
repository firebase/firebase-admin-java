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

import com.google.api.client.util.Key;
import com.google.firebase.remoteconfig.internal.ParameterValueResponse;

import java.util.HashMap;
import java.util.Map;

public final class RemoteConfigParameter {

  @Key("defaultValue")
  private ParameterValueResponse defaultValue;

  @Key("description")
  private String description;

  @Key("conditionalValues")
  private Map<String, ParameterValueResponse> conditionalResponseValues;

  private Map<String, RemoteConfigParameterValue> conditionalValues;

  public RemoteConfigParameter() {
    conditionalResponseValues = new HashMap<>();
    conditionalValues = null;
  }

  public RemoteConfigParameterValue getDefaultValue() {
    return defaultValue.toValue();
  }

  public String getDescription() {
    return description;
  }

  public Map<String, RemoteConfigParameterValue> getConditionalValues() {
    if (conditionalValues != null) {
      return conditionalValues;
    }
    conditionalValues = new HashMap<>();
    for (Map.Entry<String, ParameterValueResponse> entry : conditionalResponseValues.entrySet()) {
      conditionalValues.put(entry.getKey(), entry.getValue().toValue());
    }
    return conditionalValues;
  }

  public RemoteConfigParameter setDefaultValue(RemoteConfigParameterValue value) {
    defaultValue = value.toResponse();
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

  void wrapForTransport() {
    conditionalResponseValues.clear();
    if (conditionalValues == null) {
      return;
    }
    for (Map.Entry<String, RemoteConfigParameterValue> entry : conditionalValues.entrySet()) {
      conditionalResponseValues.put(entry.getKey(), entry.getValue().toResponse());
    }
  }
}
