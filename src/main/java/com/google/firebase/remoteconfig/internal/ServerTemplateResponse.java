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
import com.google.firebase.remoteconfig.OneOfCondition;
import com.google.firebase.remoteconfig.internal.TemplateResponse.ParameterGroupResponse;
import com.google.firebase.remoteconfig.internal.TemplateResponse.ParameterResponse;
import com.google.firebase.remoteconfig.internal.TemplateResponse.VersionResponse;
import java.util.List;
import java.util.Map;

/**
 * The Data Transfer Object for parsing Remote Config template responses from the Remote Config
 * service.
 */
public final class ServerTemplateResponse {
  @Key("parameters")
  private Map<String, ParameterResponse> parameters;

  @Key("conditions")
  private List<ServerConditionResponse> serverConditions;

  @Key("parameterGroups")
  private Map<String, ParameterGroupResponse> parameterGroups;

  @Key("version")
  private VersionResponse version;

  // For local JSON serialization and deserialization purposes only.
  // ETag in response type is never set by the HTTP response.
  @Key("etag")
  private String etag;

  public Map<String, ParameterResponse> getParameters() {
    return parameters;
  }

  public List<ServerConditionResponse> getServerConditions() {
    return serverConditions;
  }

  public Map<String, ParameterGroupResponse> getParameterGroups() {
    return parameterGroups;
  }

  public VersionResponse getVersion() {
    return version;
  }

  public String getEtag() {
    return etag;
  }

  public ServerTemplateResponse setParameters(Map<String, ParameterResponse> parameters) {
    this.parameters = parameters;
    return this;
  }

  public ServerTemplateResponse setServerConditions(
      List<ServerConditionResponse> serverConditions) {
    this.serverConditions = serverConditions;
    return this;
  }

  public ServerTemplateResponse setParameterGroups(
      Map<String, ParameterGroupResponse> parameterGroups) {
    this.parameterGroups = parameterGroups;
    return this;
  }

  public ServerTemplateResponse setVersion(VersionResponse version) {
    this.version = version;
    return this;
  }

  public ServerTemplateResponse setEtag(String etag) {
    this.etag = etag;
    return this;
  }

  /**
   * The Data Transfer Object for parsing Remote Config condition responses from the Remote Config
   * service.
   */
  public static final class ServerConditionResponse {

    @Key("name")
    private String name;

    @Key("condition")
    private OneOfCondition condition;

    public String getName() {
      return name;
    }

    public OneOfCondition getCondition() {
      return condition;
    }

    public ServerConditionResponse setName(String name) {
      this.name = name;
      return this;
    }

    public ServerConditionResponse setCondtion(OneOfCondition condition) {
      this.condition = condition;
      return this;
    }
  }
}