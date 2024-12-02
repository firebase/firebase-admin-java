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

import com.google.firebase.internal.NonNull;

/**
 * Defines supported operators for custom signal conditions.
 */
public enum CustomSignalOperator  {
    NUMERIC_EQUAL("NUMERIC_EQUAL"),
    NUMERIC_GREATER_EQUAL("NUMERIC_GREATER_EQUAL"),
    NUMERIC_GREATER_THAN("NUMERIC_GREATER_THAN"),
    NUMERIC_LESS_EQUAL("NUMERIC_LESS_EQUAL"),
    NUMERIC_LESS_THAN("NUMERIC_LESS_THAN"),
    NUMERIC_NOT_EQUAL("NUMERIC_NOT_EQUAL"),
    SEMANTIC_VERSION_EQUAL("SEMANTIC_VERSION_EQUAL"),
    SEMANTIC_VERSION_GREATER_EQUAL("SEMANTIC_VERSION_GREATER_EQUAL"),
    SEMANTIC_VERSION_GREATER_THAN("SEMANTIC_VERSION_GREATER_THAN"),
    SEMANTIC_VERSION_LESS_EQUAL("SEMANTIC_VERSION_LESS_EQUAL"),
    SEMANTIC_VERSION_LESS_THAN("SEMANTIC_VERSION_LESS_THAN"),
    SEMANTIC_VERSION_NOT_EQUAL("SEMANTIC_VERSION_NOT_EQUAL"),
    STRING_CONTAINS("STRING_CONTAINS"),
    STRING_CONTAINS_REGEX("STRING_CONTAINS_REGEX"),
    STRING_DOES_NOT_CONTAIN("STRING_DOES_NOT_CONTAIN"),
    STRING_EXACTLY_MATCHES("STRING_EXACTLY_MATCHES"),
    UNSPECIFIED("CUSTOM_SIGNAL_OPERATOR_UNSPECIFIED");

    private final String operator;
  
    CustomSignalOperator(@NonNull String operator) {
      this.operator = operator;
    }
  
    /**
     * Get the custom signal operator {@link CustomSignalOperator}.
     * 
     * @return operator.
     */
    @NonNull
    public String getOperator() {
      return operator;
    }
  }
