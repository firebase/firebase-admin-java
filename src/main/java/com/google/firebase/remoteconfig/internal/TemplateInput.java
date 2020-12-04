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

package com.google.firebase.remoteconfig.internal;

import com.google.api.client.util.Key;

import java.util.List;
import java.util.Map;

/**
 * The Data Transfer Object for parsing Remote Config template from input json strings.
 **/
public final class TemplateInput {

  @Key("parameters")
  private Map<String, TemplateResponse.ParameterResponse> parameters;

  @Key("conditions")
  private List<TemplateResponse.ConditionResponse> conditions;

  @Key("parameterGroups")
  private Map<String, TemplateResponse.ParameterGroupResponse> parameterGroups;

  @Key("version")
  private VersionInput version;

  @Key("etag")
  private String etag;

  public Map<String, TemplateResponse.ParameterResponse> getParameters() {
    return parameters;
  }

  public List<TemplateResponse.ConditionResponse> getConditions() {
    return conditions;
  }

  public Map<String, TemplateResponse.ParameterGroupResponse> getParameterGroups() {
    return parameterGroups;
  }

  public VersionInput getVersion() {
    return version;
  }

  public String getEtag() {
    return etag;
  }

  /**
   * The Data Transfer Object for parsing Remote Config version from input json strings.
   **/
  public static final class VersionInput {
    @Key("description")
    private String description;

    public String getDescription() {
      return description;
    }
  }
}
