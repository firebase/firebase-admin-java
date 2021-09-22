/*
 * Copyright 2021 Google LLC
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

/**
 * Data types that are associated with parameter values.
 */
public enum ParameterValueType {
  STRING("STRING"),
  BOOLEAN("BOOLEAN"),
  NUMBER("NUMBER"),
  JSON("JSON");

  private final String valueType;

  ParameterValueType(String valueType) {
    this.valueType = valueType;
  }

  public String getValueType() {
    return valueType;
  }
}
