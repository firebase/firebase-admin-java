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

import java.util.HashMap;
import java.util.Map;

/**
 * Represents data stored in a server side Remote Config template.
 */
public class ServerTemplateData {
  private String etag;
  private Map<String, Parameter> parameters;
  private Map<String, OneOfCondition> conditions;
  private Version version;

  /**
   * Creates a new {@link ServerTemplateData} object.
   * 
   * @param etag The ETag of this template.
   */
  public ServerTemplateData(@NonNull String etag) {
    this.parameters = new HashMap<>();
    this.conditions = new HashMap<>();
    this.etag = etag;
  }

  /**
   * Creates a new {@link ServerTemplateData} object with an empty etag.
   */
  public ServerTemplateData() {
    this((String) null);
  }

  /**
   * Gets the map of parameters of the server side template data.
   *
   * @return A non-null map of parameter keys to their optional default values and
   *         optional
   *         conditional values.
   */
  @NonNull
  public Map<String, Parameter> getParameters() {
    return this.parameters;
  }

  /**
   * Gets the map of parameters of the server side template data.
   *
   * @return A non-null map of condition keys to their conditions.
   */
  @NonNull
  public Map<String, OneOfCondition> getConditions() {
    return this.conditions;
  }

  /**
   * Gets the ETag of the server template data.
   *
   * @return The ETag of the server template data.
   */
  @NonNull
  public String getETag() {
    return this.etag;
  }

  /**
   * Gets the version information of the template.
   *
   * @return The version information of the template.
   */
  @NonNull
  public Version getVersion() {
    return version;
  }

  /**
   * Sets the map of parameters of the server side template data.
   *
   * @param parameters A non-null map of parameter keys to their optional default
   *                   values and
   *                   optional conditional values.
   * @return This {@link ServerTemplateData} instance.
   */
  @NonNull
  public ServerTemplateData setParameters(
      @NonNull Map<String, Parameter> parameters) {
    checkNotNull(parameters, "parameters must not be null.");
    this.parameters = parameters;
    return this;
  }

  /**
   * Sets the map of conditions of the server side template data.
   *
   * @param conditions A non-null map of conditions.
   * @return This {@link ServerTemplateData} instance.
   */
  @NonNull
  public ServerTemplateData setConditions(
      @NonNull Map<String, OneOfCondition> conditions) {
    checkNotNull(conditions, "conditions must not be null.");
    this.conditions = conditions;
    return this;
  }

  /**
   * Sets the version information of the template.
   * Only the version's description field can be specified here.
   *
   * @param version A {@link Version} instance.
   * @return This {@link Template} instance.
   */
  @NonNull
  public ServerTemplateData setVersion(Version version) {
    this.version = version;
    return this;
  }
}
