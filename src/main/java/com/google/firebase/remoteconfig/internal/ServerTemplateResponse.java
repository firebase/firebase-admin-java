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

package com.google.firebase.remoteconfig.internal;

import com.google.api.client.util.Key;
import com.google.firebase.remoteconfig.internal.TemplateResponse.ParameterGroupResponse;
import com.google.firebase.remoteconfig.internal.TemplateResponse.ParameterResponse;
import com.google.firebase.remoteconfig.internal.TemplateResponse.VersionResponse;

import java.util.List;
import java.util.Map;

/**
 * The Data Transfer Object for parsing Remote Config template responses from
 * the Remote Config
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
   * The Data Transfer Object for parsing Remote Config condition responses from
   * the Remote Config
   * service.
   */
  public static final class ServerConditionResponse {

    @Key("name")
    private String name;

    @Key("condition")
    private OneOfConditionResponse condition;

    public String getName() {
      return name;
    }

    public OneOfConditionResponse getServerCondition() {
      return condition;
    }

    public ServerConditionResponse setName(String name) {
      this.name = name;
      return this;
    }

    public ServerConditionResponse setServerCondition(OneOfConditionResponse condition) {
      this.condition = condition;
      return this;
    }
  }

  public static final class OneOfConditionResponse {
    @Key("orCondition")
    private OrConditionResponse orCondition;

    @Key("andCondition")
    private AndConditionResponse andCondition;

    @Key("customSignal")
    private CustomSignalConditionResponse customSignalCondition;

    @Key("percent")
    private PercentConditionResponse percentCondition;

    public OrConditionResponse getOrCondition() {
      return orCondition;
    }

    public AndConditionResponse getAndCondition() {
      return andCondition;
    }

    public PercentConditionResponse getPercentCondition() {
      return percentCondition;
    }

    public CustomSignalConditionResponse getCustomSignalCondition() {
      return customSignalCondition;
    }

    public OneOfConditionResponse setOrCondition(OrConditionResponse orCondition) {
      this.orCondition = orCondition;
      return this;
    }

    public OneOfConditionResponse setAndCondition(AndConditionResponse andCondition) {
      this.andCondition = andCondition;
      return this;
    }

    public OneOfConditionResponse setCustomSignalCondition(
        CustomSignalConditionResponse customSignalCondition) {
      this.customSignalCondition = customSignalCondition;
      return this;
    }

    public OneOfConditionResponse setPercentCondition(PercentConditionResponse percentCondition) {
      this.percentCondition = percentCondition;
      return this;
    }
  }

  public static final class OrConditionResponse {
    @Key("conditions")
    private List<OneOfConditionResponse> conditions;

    public List<OneOfConditionResponse> getConditions() {
      return conditions;
    }

    public OrConditionResponse setConditions(List<OneOfConditionResponse> conditions) {
      this.conditions = conditions;
      return this;
    }
  }

  public static final class AndConditionResponse {
    @Key("conditions")
    private List<OneOfConditionResponse> conditions;

    public List<OneOfConditionResponse> getConditions() {
      return conditions;
    }

    public AndConditionResponse setConditions(List<OneOfConditionResponse> conditions) {
      this.conditions = conditions;
      return this;
    }
  }

  public static final class CustomSignalConditionResponse {
    @Key("customSignalOperator")
    private String operator;

    @Key("customSignalKey")
    private String key;

    @Key("targetCustomSignalValues")
    private List<String> targetValues;

    public String getOperator() {
      return operator;
    }

    public String getKey() {
      return key;
    }

    public List<String> getTargetValues() {
      return targetValues;
    }

    public CustomSignalConditionResponse setOperator(String operator) {
      this.operator = operator;
      return this;
    }

    public CustomSignalConditionResponse setKey(String key) {
      this.key = key;
      return this;
    }

    public CustomSignalConditionResponse setTargetValues(List<String> targetValues) {
      this.targetValues = targetValues;
      return this;
    }
  }

  public static final class PercentConditionResponse {
    @Key("microPercent")
    private int microPercent;

    @Key("microPercentRange")
    private MicroPercentRangeResponse microPercentRange;

    @Key("percentConditionOperator")
    private String percentOperator;

    @Key("seed")
    private String seed;

    public int getMicroPercent() {
      return microPercent;
    }

    public MicroPercentRangeResponse getMicroPercentRange() {
      return microPercentRange;
    }

    public String getPercentOperator() {
      return percentOperator;
    }

    public String getSeed() {
      return seed;
    }

    public PercentConditionResponse setMicroPercent(int microPercent) {
      this.microPercent = microPercent;
      return this;
    }

    public PercentConditionResponse setMicroPercentRange(
        MicroPercentRangeResponse microPercentRange) {
      this.microPercentRange = microPercentRange;
      return this;
    }

    public PercentConditionResponse setPercentOperator(String percentOperator) {
      this.percentOperator = percentOperator;
      return this;
    }

    public PercentConditionResponse setSeed(String seed) {
      this.seed = seed;
      return this;
    }
  }

  public static final class MicroPercentRangeResponse {
    @Key("microPercentLowerBound")
    private int microPercentLowerBound;

    @Key("microPercentUpperBound")
    private int microPercentUpperBound;

    public int getMicroPercentLowerBound() {
      return microPercentLowerBound;
    }

    public int getMicroPercentUpperBound() {
      return microPercentUpperBound;
    }

    public MicroPercentRangeResponse setMicroPercentLowerBound(int microPercentLowerBound) {
      this.microPercentLowerBound = microPercentLowerBound;
      return this;
    }

    public MicroPercentRangeResponse setMicroPercentUpperBound(int microPercentUpperBound) {
      this.microPercentUpperBound = microPercentUpperBound;
      return this;
    }
  }
}
