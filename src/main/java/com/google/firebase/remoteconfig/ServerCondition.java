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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.firebase.internal.NonNull;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.ServerConditionResponse;
import java.util.Objects;

/**
 * Represents a Remote Config condition that can be included in a {@link Template}. A condition
 * targets a specific group of users. A list of these conditions make up part of a Remote Config
 * template.
 */
public final class ServerCondition {

  private String name;
  private OneOfCondition serverCondition;

  /**
   * Creates a new {@link Condition}.
   *
   * @param name A non-null, non-empty, and unique name of this condition.
   * @param condition A non-null and non-empty condition.
   */
  public ServerCondition(@NonNull String name, @NonNull OneOfCondition condition) {
    checkArgument(!Strings.isNullOrEmpty(name), "condition name must not be null or empty");
    this.name = name;
    this.serverCondition = condition;
  }

  ServerCondition(@NonNull ServerConditionResponse serverConditionResponse) {
    checkNotNull(serverConditionResponse);
    this.name = serverConditionResponse.getName();
    this.serverCondition = serverConditionResponse.getCondition();
  }

  /**
   * Gets the name of the condition.
   *
   * @return The name of the condition.
   */
  @NonNull
  public String getName() {
    return name;
  }

  /**
   * Gets the expression of the condition.
   *
   * @return The expression of the condition.
   */
  @NonNull
  public OneOfCondition getCondition() {
    return serverCondition;
  }

  /**
   * Sets the name of the condition.
   *
   * @param name A non-empty and unique name of this condition.
   * @return This {@link Condition}.
   */
  public ServerCondition setName(@NonNull String name) {
    checkArgument(!Strings.isNullOrEmpty(name), "condition name must not be null or empty");
    this.name = name;
    return this;
  }

  /**
   * Sets the expression of the condition.
   *
   * <p>See <a href="https://firebase.google.com/docs/remote-config/condition-reference">condition
   * expressions</a> for the expected syntax of this field.
   *
   * @param condition The logic of this condition.
   * @return This {@link Condition}.
   */
  public ServerCondition setServerCondition(@NonNull OneOfCondition condition) {
    this.serverCondition = condition;
    return this;
  }

  ServerConditionResponse toServerConditionResponse() {
    return new ServerConditionResponse().setName(this.name).setCondtion(this.serverCondition);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ServerCondition condition = (ServerCondition) o;
    return Objects.equals(name, condition.name)
        && Objects.equals(serverCondition, condition.serverCondition);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, serverCondition);
  }
}
