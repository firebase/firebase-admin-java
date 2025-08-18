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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.json.JsonFactory;
import com.google.common.base.Strings;
import com.google.firebase.ErrorCode;
import com.google.firebase.internal.ApiClientUtils;
import com.google.firebase.internal.NonNull;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse;
import com.google.firebase.remoteconfig.internal.TemplateResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ServerTemplateData {

  private String etag;
  private Map<String, Parameter> parameters;
  private List<ServerCondition> serverConditions;
  private Map<String, ParameterGroup> parameterGroups;
  private Version version;


  ServerTemplateData(String etag) {
    this.parameters = new HashMap<>();
    this.serverConditions = new ArrayList<>();
    this.parameterGroups = new HashMap<>();
    this.etag = etag;
  }

  ServerTemplateData() {
    this((String) null);
  }

  ServerTemplateData(@NonNull ServerTemplateResponse serverTemplateResponse) {
    checkNotNull(serverTemplateResponse);
    this.parameters = new HashMap<>();
    this.serverConditions = new ArrayList<>();
    this.parameterGroups = new HashMap<>();
    if (serverTemplateResponse.getParameters() != null) {
      for (Map.Entry<String, TemplateResponse.ParameterResponse> entry :
          serverTemplateResponse.getParameters().entrySet()) {
        this.parameters.put(entry.getKey(), new Parameter(entry.getValue()));
      }
    }
    if (serverTemplateResponse.getServerConditions() != null) {
      for (ServerTemplateResponse.ServerConditionResponse conditionResponse :
          serverTemplateResponse.getServerConditions()) {
        this.serverConditions.add(new ServerCondition(conditionResponse));
      }
    }
    if (serverTemplateResponse.getParameterGroups() != null) {
      for (Map.Entry<String, TemplateResponse.ParameterGroupResponse> entry :
          serverTemplateResponse.getParameterGroups().entrySet()) {
        this.parameterGroups.put(entry.getKey(), new ParameterGroup(entry.getValue()));
      }
    }
    if (serverTemplateResponse.getVersion() != null) {
      this.version = new Version(serverTemplateResponse.getVersion());
    }
    this.etag = serverTemplateResponse.getEtag();
  }


  static ServerTemplateData fromJSON(@NonNull String json)
      throws FirebaseRemoteConfigException {
    checkArgument(!Strings.isNullOrEmpty(json), "JSON String must not be null or empty.");
    // using the default json factory as no rpc calls are made here
    JsonFactory jsonFactory = ApiClientUtils.getDefaultJsonFactory();
    try {
      ServerTemplateResponse serverTemplateResponse =
          jsonFactory.createJsonParser(json).parseAndClose(ServerTemplateResponse.class);
      return new ServerTemplateData(serverTemplateResponse);
    } catch (IOException e) {
      throw new FirebaseRemoteConfigException(
          ErrorCode.INVALID_ARGUMENT, "Unable to parse JSON string.");
    }
  }


  String getETag() {
    return this.etag;
  }


  @NonNull
  public Map<String, Parameter> getParameters() {
    return this.parameters;
  }

  @NonNull
  List<ServerCondition> getServerConditions() {
    return serverConditions;
  }

  @NonNull
  Map<String, ParameterGroup> getParameterGroups() {
    return parameterGroups;
  }

  Version getVersion() {
    return version;
  }

  ServerTemplateData setParameters(@NonNull Map<String, Parameter> parameters) {
    checkNotNull(parameters, "parameters must not be null.");
    this.parameters = parameters;
    return this;
  }


  ServerTemplateData setServerConditions(@NonNull List<ServerCondition> conditions) {
    checkNotNull(conditions, "conditions must not be null.");
    this.serverConditions = conditions;
    return this;
  }

  ServerTemplateData setParameterGroups(
      @NonNull Map<String, ParameterGroup> parameterGroups) {
    checkNotNull(parameterGroups, "parameter groups must not be null.");
    this.parameterGroups = parameterGroups;
    return this;
  }

  ServerTemplateData setVersion(Version serverVersion) {
    this.version = serverVersion;
    return this;
  }

  String toJSON() {
    JsonFactory jsonFactory = ApiClientUtils.getDefaultJsonFactory();
    try {
      return jsonFactory.toString(this.toServerTemplateResponse(true));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  ServerTemplateData setETag(String etag) {
    this.etag = etag;
    return this;
  }

  ServerTemplateResponse toServerTemplateResponse(boolean includeAll) {
    Map<String, TemplateResponse.ParameterResponse> parameterResponses = new HashMap<>();
    for (Map.Entry<String, Parameter> entry : this.parameters.entrySet()) {
      parameterResponses.put(entry.getKey(), entry.getValue().toParameterResponse());
    }
    List<ServerTemplateResponse.ServerConditionResponse> serverConditionResponses =
        new ArrayList<>();
    for (ServerCondition condition : this.serverConditions) {
      serverConditionResponses.add(condition.toServerConditionResponse());
    }
    Map<String, TemplateResponse.ParameterGroupResponse> parameterGroupResponse = new HashMap<>();
    for (Map.Entry<String, ParameterGroup> entry : this.parameterGroups.entrySet()) {
      parameterGroupResponse.put(entry.getKey(), entry.getValue().toParameterGroupResponse());
    }
    TemplateResponse.VersionResponse versionResponse =
        (this.version == null) ? null : this.version.toVersionResponse(includeAll);
    ServerTemplateResponse serverTemplateResponse =
        new ServerTemplateResponse()
            .setParameters(parameterResponses)
            .setServerConditions(serverConditionResponses)
            .setParameterGroups(parameterGroupResponse)
            .setVersion(versionResponse);
    if (includeAll) {
      return serverTemplateResponse.setEtag(this.etag);
    }
    return serverTemplateResponse;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ServerTemplateData template = (ServerTemplateData) o;
    return Objects.equals(etag, template.etag)
        && Objects.equals(parameters, template.parameters)
        && Objects.equals(serverConditions, template.serverConditions)
        && Objects.equals(parameterGroups, template.parameterGroups)
        && Objects.equals(version, template.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(etag, parameters, serverConditions, parameterGroups, version);
  }
}

