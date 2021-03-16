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
import com.google.firebase.remoteconfig.internal.TemplateResponse;
import com.google.firebase.remoteconfig.internal.TemplateResponse.ParameterGroupResponse;
import com.google.firebase.remoteconfig.internal.TemplateResponse.ParameterResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a Remote Config parameter group that can be included in a {@link Template}.
 * Grouping parameters is only for management purposes and does not affect client-side
 * fetching of parameter values.
 */
public final class ParameterGroup {

  private String description;
  private Map<String, Parameter> parameters;

  /**
   * Creates a new {@link ParameterGroup}.
   */
  public ParameterGroup() {
    parameters = new HashMap<>();
  }

  ParameterGroup(@NonNull ParameterGroupResponse parameterGroupResponse) {
    checkNotNull(parameterGroupResponse);
    this.parameters = new HashMap<>();
    if (parameterGroupResponse.getParameters() != null) {
      for (Map.Entry<String, TemplateResponse.ParameterResponse> entry
              : parameterGroupResponse.getParameters().entrySet()) {
        this.parameters.put(entry.getKey(), new Parameter(entry.getValue()));
      }
    }
    this.description = parameterGroupResponse.getDescription();
  }

  /**
   * Gets the description of the parameter group.
   *
   * @return The description of the parameter or null.
   */
  @Nullable
  public String getDescription() {
    return description;
  }

  /**
   * Gets the map of parameters that belong to this group.
   *
   * @return A non-null map of parameter keys to their optional default values and optional
   *     conditional values.
   */
  @NonNull
  public Map<String, Parameter> getParameters() {
    return parameters;
  }

  /**
   * Sets the description of the parameter group.
   * Should not be over 256 characters and may contain any Unicode characters.
   *
   * @param description The description of the parameter group.
   * @return This {@link ParameterGroup}.
   */
  public ParameterGroup setDescription(@Nullable String description) {
    this.description = description;
    return this;
  }

  /**
   * Sets the map of parameters that belong to this group.
   *
   * <p>A parameter only appears once per Remote Config template.
   * An ungrouped parameter appears at the top level, whereas a
   * parameter organized within a group appears within its group's map of parameters.
   *
   * @param parameters A non-null map of parameter keys to their optional default values and
   *                   optional conditional values.
   * @return This {@link ParameterGroup} instance.
   */
  public ParameterGroup setParameters(
          @NonNull Map<String, Parameter> parameters) {
    checkNotNull(parameters, "parameters must not be null.");
    this.parameters = parameters;
    return this;
  }

  ParameterGroupResponse toParameterGroupResponse() {
    Map<String, ParameterResponse> parameterResponses = new HashMap<>();
    for (Map.Entry<String, Parameter> entry : this.parameters.entrySet()) {
      parameterResponses.put(entry.getKey(), entry.getValue().toParameterResponse());
    }
    return new ParameterGroupResponse()
            .setDescription(this.description)
            .setParameters(parameterResponses);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ParameterGroup that = (ParameterGroup) o;
    return Objects.equals(description, that.description)
            && Objects.equals(parameters, that.parameters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(description, parameters);
  }
}
