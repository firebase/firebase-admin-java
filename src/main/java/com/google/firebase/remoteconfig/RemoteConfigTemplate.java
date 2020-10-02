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
import com.google.firebase.remoteconfig.internal.TemplateResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a Remote Config template.
 */
public final class RemoteConfigTemplate {

  private String etag;
  private Map<String, RemoteConfigParameter> parameters;

  /**
   * Creates a new {@link RemoteConfigTemplate}.
   */
  public RemoteConfigTemplate() {
    parameters = new HashMap<>();
  }

  protected RemoteConfigTemplate(@NonNull TemplateResponse templateResponse) {
    checkNotNull(templateResponse);
    parameters = new HashMap<>();
    if (templateResponse.getParameters() != null) {
      for (Map.Entry<String, TemplateResponse.ParameterResponse> entry :
              templateResponse.getParameters().entrySet()) {
        parameters.put(entry.getKey(), new RemoteConfigParameter(entry.getValue()));
      }
    }
  }

  /**
   * Gets the ETag of the template.
   *
   * @return The ETag of the template.
   */
  public String getETag() {
    return this.etag;
  }

  /**
   * Gets the map of parameters of the template.
   *
   * @return A non-null map of parameter keys to their optional default values and optional
   *     conditional values.
   */
  @NonNull
  public Map<String, RemoteConfigParameter> getParameters() {
    return this.parameters;
  }

  /**
   * Sets the map of parameters of the template.
   *
   * @param parameters A non-null map of parameter keys to their optional default values and
   *                   optional conditional values.
   * @return This {@link RemoteConfigTemplate} instance.
   */
  public RemoteConfigTemplate setParameters(
          @NonNull Map<String, RemoteConfigParameter> parameters) {
    checkNotNull(parameters, "parameters must not be null.");
    this.parameters = parameters;
    return this;
  }

  RemoteConfigTemplate setETag(String etag) {
    this.etag = etag;
    return this;
  }

  TemplateResponse toTemplateResponse() {
    Map<String, TemplateResponse.ParameterResponse> parameterResponses = new HashMap<>();
    for (Map.Entry<String, RemoteConfigParameter> entry : parameters.entrySet()) {
      parameterResponses.put(entry.getKey(), entry.getValue().toParameterResponse());
    }
    return new TemplateResponse().setParameters(parameterResponses);
  }
}
