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
 * The Data Transfer Object for parsing Remote Config template responses from the
 * Remote Config service.
 **/
public final class TemplateResponse {

  @Key("parameters")
  private Map<String, ParameterResponse> parameters;

  @Key("conditions")
  private List<ConditionResponse> conditions;

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

  public List<ConditionResponse> getConditions() {
    return conditions;
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

  public TemplateResponse setParameters(
          Map<String, ParameterResponse> parameters) {
    this.parameters = parameters;
    return this;
  }

  public TemplateResponse setConditions(
          List<ConditionResponse> conditions) {
    this.conditions = conditions;
    return this;
  }

  public TemplateResponse setParameterGroups(
          Map<String, ParameterGroupResponse> parameterGroups) {
    this.parameterGroups = parameterGroups;
    return this;
  }

  public TemplateResponse setVersion(VersionResponse version) {
    this.version = version;
    return this;
  }

  public TemplateResponse setEtag(String etag) {
    this.etag = etag;
    return this;
  }

  /**
   * The Data Transfer Object for parsing Remote Config parameter responses from the
   * Remote Config service.
   **/
  public static final class ParameterResponse {

    @Key("defaultValue")
    private ParameterValueResponse defaultValue;

    @Key("description")
    private String description;

    @Key("conditionalValues")
    private Map<String, ParameterValueResponse> conditionalValues;

    public ParameterValueResponse getDefaultValue() {
      return defaultValue;
    }

    public String getDescription() {
      return description;
    }

    public Map<String, ParameterValueResponse> getConditionalValues() {
      return conditionalValues;
    }

    public ParameterResponse setDefaultValue(
            ParameterValueResponse defaultValue) {
      this.defaultValue = defaultValue;
      return this;
    }

    public ParameterResponse setDescription(String description) {
      this.description = description;
      return this;
    }

    public ParameterResponse setConditionalValues(
            Map<String, ParameterValueResponse> conditionalValues) {
      this.conditionalValues = conditionalValues;
      return this;
    }
  }

  /**
   * The Data Transfer Object for parsing Remote Config parameter value responses from the
   * Remote Config service.
   **/
  public static final class ParameterValueResponse {

    @Key("value")
    private String value;

    @Key("useInAppDefault")
    private Boolean useInAppDefault;

    public String getValue() {
      return value;
    }

    public boolean isUseInAppDefault() {
      return Boolean.TRUE.equals(this.useInAppDefault);
    }

    public ParameterValueResponse setValue(String value) {
      this.value = value;
      return this;
    }

    public ParameterValueResponse setUseInAppDefault(boolean useInAppDefault) {
      this.useInAppDefault = useInAppDefault;
      return this;
    }
  }

  /**
   * The Data Transfer Object for parsing Remote Config condition responses from the
   * Remote Config service.
   **/
  public static final class ConditionResponse {

    @Key("name")
    private String name;

    @Key("expression")
    private String expression;

    @Key("tagColor")
    private String tagColor;

    public String getName() {
      return name;
    }

    public String getExpression() {
      return expression;
    }

    public String getTagColor() {
      return tagColor;
    }

    public ConditionResponse setName(String name) {
      this.name = name;
      return this;
    }

    public ConditionResponse setExpression(String expression) {
      this.expression = expression;
      return this;
    }

    public ConditionResponse setTagColor(String tagColor) {
      this.tagColor = tagColor;
      return this;
    }
  }

  /**
   * The Data Transfer Object for parsing Remote Config parameter groups responses from the
   * Remote Config service.
   **/
  public static final class ParameterGroupResponse {

    @Key("description")
    private String description;

    @Key("parameters")
    private Map<String, ParameterResponse> parameters;

    public Map<String, ParameterResponse> getParameters() {
      return parameters;
    }

    public String getDescription() {
      return description;
    }

    public ParameterGroupResponse setParameters(
            Map<String, ParameterResponse> parameters) {
      this.parameters = parameters;
      return this;
    }

    public ParameterGroupResponse setDescription(String description) {
      this.description = description;
      return this;
    }
  }

  /**
   * The Data Transfer Object for parsing Remote Config version responses from the
   * Remote Config service.
   **/
  public static final class VersionResponse {
    @Key("versionNumber")
    private String versionNumber;

    @Key("updateTime")
    private String updateTime;

    @Key("updateOrigin")
    private String updateOrigin;

    @Key("updateType")
    private String updateType;

    @Key("updateUser")
    private UserResponse updateUser;

    @Key("description")
    private String description;

    @Key("rollbackSource")
    private String rollbackSource;

    @Key("legacy")
    private Boolean legacy;

    public String getVersionNumber() {
      return versionNumber;
    }

    public String getUpdateTime() {
      return updateTime;
    }

    public String getUpdateOrigin() {
      return updateOrigin;
    }

    public String getUpdateType() {
      return updateType;
    }

    public UserResponse getUpdateUser() {
      return updateUser;
    }

    public String getDescription() {
      return description;
    }

    public String getRollbackSource() {
      return rollbackSource;
    }

    public boolean isLegacy() {
      return Boolean.TRUE.equals(this.legacy);
    }

    public VersionResponse setDescription(String description) {
      this.description = description;
      return this;
    }

    public VersionResponse setVersionNumber(String versionNumber) {
      this.versionNumber = versionNumber;
      return this;
    }

    public VersionResponse setUpdateTime(String updateTime) {
      this.updateTime = updateTime;
      return this;
    }

    public VersionResponse setUpdateOrigin(String updateOrigin) {
      this.updateOrigin = updateOrigin;
      return this;
    }

    public VersionResponse setUpdateType(String updateType) {
      this.updateType = updateType;
      return this;
    }

    public VersionResponse setUpdateUser(UserResponse updateUser) {
      this.updateUser = updateUser;
      return this;
    }

    public VersionResponse setRollbackSource(String rollbackSource) {
      this.rollbackSource = rollbackSource;
      return this;
    }

    public VersionResponse setLegacy(Boolean legacy) {
      this.legacy = legacy;
      return this;
    }
  }

  /**
   * The Data Transfer Object for parsing Remote Config user responses from the
   * Remote Config service.
   **/
  public static final class UserResponse {
    @Key("email")
    private String email;

    @Key("name")
    private String name;

    @Key("imageUrl")
    private String imageUrl;

    public String getEmail() {
      return email;
    }

    public String getName() {
      return name;
    }

    public String getImageUrl() {
      return imageUrl;
    }

    public UserResponse setEmail(String email) {
      this.email = email;
      return this;
    }

    public UserResponse setName(String name) {
      this.name = name;
      return this;
    }

    public UserResponse setImageUrl(String imageUrl) {
      this.imageUrl = imageUrl;
      return this;
    }
  }

  /**
   * The Data Transfer Object for parsing Remote Config versions list responses from the
   * Remote Config service.
   **/
  public static final class ListVersionsResponse {
    @Key("versions")
    private List<VersionResponse> versions;

    @Key("nextPageToken")
    private String nextPageToken;

    public List<VersionResponse> getVersions() {
      return versions;
    }

    public boolean hasVersions() {
      return versions != null && !versions.isEmpty();
    }

    public String getNextPageToken() {
      return nextPageToken;
    }

    public ListVersionsResponse setNextPageToken(String nextPageToken) {
      this.nextPageToken = nextPageToken;
      return this;
    }

    public ListVersionsResponse setVersions(
            List<VersionResponse> versions) {
      this.versions = versions;
      return this;
    }
  }
}
