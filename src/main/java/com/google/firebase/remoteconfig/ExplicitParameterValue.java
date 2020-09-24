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

import com.google.firebase.remoteconfig.internal.ParameterValueResponse;

public final class ExplicitParameterValue extends RemoteConfigParameterValue {

  private final String value;

  private ExplicitParameterValue(String value) {
    this.value = value;
  }

  public static ExplicitParameterValue of(String value) {
    return new ExplicitParameterValue(value);
  }

  public String getValue() {
    return this.value;
  }

  @Override
  public ParameterValueResponse toResponse() {
    return ParameterValueResponse.ofValue(this.value);
  }
}
