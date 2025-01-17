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
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.AndConditionResponse;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.CustomSignalConditionResponse;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.OneOfConditionResponse;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.OrConditionResponse;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.PercentConditionResponse;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.ServerConditionResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a Remote Config condition that can be included in a {@link ServerTemplate}. A
 * condition targets a specific group of users. A list of these conditions make up part of a Remote
 * Config template.
 */
public final class ServerCondition {

  private String name;
  private OneOfCondition serverCondition;
  private static final Logger logger = LoggerFactory.getLogger(ServerCondition.class);

  /**
   * Creates a new {@link ServerCondition}.
   *
   * @param name A non-null, non-empty, and unique name of this condition.
   * @param condition A non-null and non-empty condition.
   */
  public ServerCondition(@NonNull String name, @NonNull OneOfCondition condition) {
    checkArgument(!Strings.isNullOrEmpty(name), "condition name must not be null or empty");
    this.name = name;
    this.serverCondition = condition;
  }

  /**
   * Creates a new {@link ServerCondition} from API response.
   * 
   * @param serverConditionResponse the conditions obtained from server call.
   */
  ServerCondition(@NonNull ServerConditionResponse serverConditionResponse) {
    checkNotNull(serverConditionResponse);
    this.name = serverConditionResponse.getName();
    this.serverCondition = this.convertToOneOfCondition(serverConditionResponse.getCondition());
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
    return new ServerConditionResponse().setName(this.name);
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

  private OneOfCondition convertToOneOfCondition(OneOfConditionResponse response) {
    if (response.getCustomSignalCondition() != null) {
      return this.getCustomSignalOneOfCondition(response.getCustomSignalCondition());
    } else if (response.getPercentCondition() != null) {
      return this.getPercentOneOfCondition(response.getPercentCondition());
    } else if (response.getAndCondition() != null) {
      return this.getAndOneOfCondition(response.getAndCondition());
    } else if (response.getOrCondition() != null) {
      return this.getOrOneOfCondition(response.getOrCondition());
    }
    logger.atError().log("No valid condition found in response: %s", response);
    return null;
  }

  private OneOfCondition getOrOneOfCondition(OrConditionResponse orConditionResponse) {
    List<OneOfCondition> nestedConditions =  new ArrayList<>();
    for (OneOfConditionResponse nestedResponse: orConditionResponse.getConditions()) {
      OneOfCondition nestedCondition = this.convertToOneOfCondition(nestedResponse);
      nestedConditions.add(nestedCondition);
    }
    OrCondition orCondition = new OrCondition(nestedConditions);
    OneOfCondition condition = new OneOfCondition();
    condition.setOrCondition(orCondition);
    return condition;
  }

  private OneOfCondition getAndOneOfCondition(AndConditionResponse andConditionResponse) {
    List<OneOfCondition> nestedConditions =  new ArrayList<>();
    for (OneOfConditionResponse nestedResponse: andConditionResponse.getConditions()) {
      OneOfCondition nestedCondition = this.convertToOneOfCondition(nestedResponse);
      nestedConditions.add(nestedCondition);
    }
    AndCondition andCondition = new AndCondition(nestedConditions);
    OneOfCondition condition = new OneOfCondition();
    condition.setAndCondition(andCondition);
    return condition;
  }

  private OneOfCondition getCustomSignalOneOfCondition(
      CustomSignalConditionResponse customSignalResponse) {
    String customSignalKey = customSignalResponse.getKey();
    List<String> targetCustomSignalValues = customSignalResponse.getTargetValues();
    CustomSignalOperator operator;
    switch (customSignalResponse.getOperator()) {
      case "NUMERIC_EQUAL":
        operator = CustomSignalOperator.NUMERIC_EQUAL;
        break;
      case "NUMERIC_GREATER_EQUAL":
        operator = CustomSignalOperator.NUMERIC_GREATER_EQUAL;
        break;
      case "NUMERIC_GREATER_THAN":
        operator = CustomSignalOperator.NUMERIC_GREATER_THAN;
        break;
      case "NUMERIC_LESS_EQUAL":
        operator = CustomSignalOperator.NUMERIC_LESS_EQUAL;
        break;
      case "NUMERIC_LESS_THAN":
        operator = CustomSignalOperator.NUMERIC_LESS_THAN;
        break;
      case "NUMERIC_NOT_EQUAL":
        operator = CustomSignalOperator.NUMERIC_NOT_EQUAL;
        break;
      case "SEMANTIC_VERSION_EQUAL":
        operator = CustomSignalOperator.SEMANTIC_VERSION_EQUAL;
        break;
      case "SEMANTIC_VERSION_GREATER_EQUAL":
        operator = CustomSignalOperator.SEMANTIC_VERSION_GREATER_EQUAL;
        break;
      case "SEMANTIC_VERSION_GREATER_THAN":
        operator = CustomSignalOperator.SEMANTIC_VERSION_GREATER_THAN;
        break;
      case "SEMANTIC_VERSION_LESS_EQUAL":
        operator = CustomSignalOperator.SEMANTIC_VERSION_LESS_EQUAL;
        break;
      case "SEMANTIC_VERSION_LESS_THAN":
        operator = CustomSignalOperator.SEMANTIC_VERSION_LESS_THAN;
        break;
      case "SEMANTIC_VERSION_NOT_EQUAL":
        operator = CustomSignalOperator.SEMANTIC_VERSION_NOT_EQUAL;
        break;
      case "STRING_CONTAINS":
        operator = CustomSignalOperator.STRING_CONTAINS;
        break;
      case "STRING_CONTAINS_REGEX":
        operator = CustomSignalOperator.STRING_CONTAINS_REGEX;
        break;
      case "STRING_DOES_NOT_CONTAIN":
        operator = CustomSignalOperator.STRING_DOES_NOT_CONTAIN;
        break;
      case "STRING_EXACTLY_MATCHES":
        operator = CustomSignalOperator.STRING_EXACTLY_MATCHES;
        break;
      default:
        operator = CustomSignalOperator.UNSPECIFIED;
    }
    CustomSignalCondition customSignalCondition = new CustomSignalCondition(
        customSignalKey, operator, targetCustomSignalValues);
    OneOfCondition condition = new OneOfCondition();
    condition.setCustomSignal(customSignalCondition);
    return condition;
  }

  private OneOfCondition getPercentOneOfCondition(PercentConditionResponse percentResponse) {
    PercentConditionOperator operator;
    switch (percentResponse.getPercentOperator()) {
      case "BETWEEN":
        operator = PercentConditionOperator.BETWEEN;
        break;
      case "GREATER_THAN":
        operator = PercentConditionOperator.GREATER_THAN;
        break;
      case "LESS_OR_EQUAL":
        operator = PercentConditionOperator.LESS_OR_EQUAL;
        break;
      default:
        operator = PercentConditionOperator.UNSPECIFIED;
    }
    int microPercent = percentResponse.getMicroPercent();
    String seed = percentResponse.getSeed();
    MicroPercentRange microPercentRange = null;
    if (percentResponse.getMicroPercentRange() != null) {
      microPercentRange = new MicroPercentRange(
          percentResponse.getMicroPercentRange().getMicroPercentLowerBound(),
          percentResponse.getMicroPercentRange().getMicroPercentUpperBound());
    }
        
    PercentCondition percentCondition;
    if (microPercentRange != null) {
      percentCondition = new PercentCondition(microPercentRange, operator, seed);
    } else {
      percentCondition = new PercentCondition(microPercent, operator, seed);
    }
    OneOfCondition condition = new OneOfCondition();
    condition.setPercent(percentCondition);
    return condition;
  }
}
