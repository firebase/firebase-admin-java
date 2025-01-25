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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ServerCondition {

  private String name;
  private OneOfCondition serverCondition;

  ServerCondition(@NonNull String name, @NonNull OneOfCondition condition) {
    checkArgument(!Strings.isNullOrEmpty(name), "condition name must not be null or empty");
    this.name = name;
    this.serverCondition = condition;
  }

  ServerCondition(@NonNull ServerConditionResponse serverConditionResponse) {
    checkNotNull(serverConditionResponse);
    this.name = serverConditionResponse.getName();
    this.serverCondition = new OneOfCondition(serverConditionResponse.getServerCondition());
  }

  @NonNull
  String getName() {
    return name;
  }

  @NonNull
  OneOfCondition getCondition() {
    return serverCondition;
  }

  ServerCondition setName(@NonNull String name) {
    checkArgument(!Strings.isNullOrEmpty(name), "condition name must not be null or empty");
    this.name = name;
    return this;
  }

  ServerCondition setServerCondition(@NonNull OneOfCondition condition) {
    checkNotNull(condition, "condition must not be null or empty");
    this.serverCondition = condition;
    return this;
  }

  ServerConditionResponse toServerConditionResponse() {
    return new ServerConditionResponse().setName(this.name)
        .setServerCondition(this.serverCondition.toOneOfConditionResponse());
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
