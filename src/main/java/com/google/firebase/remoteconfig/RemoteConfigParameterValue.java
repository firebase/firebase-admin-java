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

import com.google.firebase.remoteconfig.internal.TemplateResponse.ParameterValueResponse;

/**
 * Represents a Remote Config parameter value that can be used in a {@link RemoteConfigTemplate}.
 */
public abstract class RemoteConfigParameterValue {

  /**
   * Creates a new {@link RemoteConfigParameterValue.Explicit} instance with the given value.
   *
   * @param value The value of the {@link RemoteConfigParameterValue.Explicit}.
   * @return A {@link RemoteConfigParameterValue.Explicit} instance.
   */
  public static Explicit of(String value) {
    return new Explicit(value);
  }

  /**
   * Creates a new {@link RemoteConfigParameterValue.InAppDefault} instance.
   *
   * @return A {@link RemoteConfigParameterValue.InAppDefault} instance.
   */
  public static InAppDefault inAppDefault() {
    return new InAppDefault();
  }

  abstract ParameterValueResponse toParameterValueResponse();

  /**
   * Represents an explicit Remote Config parameter value with a {@link String} value that the
   * parameter is set to.
   */
  public static final class Explicit extends RemoteConfigParameterValue {

    private final String value;

    private Explicit(String value) {
      this.value = value;
    }

    /**
     * Gets the value of {@link RemoteConfigParameterValue.Explicit}.
     *
     * @return The {@link String} value.
     */
    public String getValue() {
      return this.value;
    }

    @Override
    ParameterValueResponse toParameterValueResponse() {
      return new ParameterValueResponse()
              .setValue(this.value)
              .setInAppDefaultValue(null);
    }
  }

  /**
   * Represents an in app default parameter value.
   */
  public static final class InAppDefault extends RemoteConfigParameterValue {

    @Override
    ParameterValueResponse toParameterValueResponse() {
      return new ParameterValueResponse()
              .setInAppDefaultValue(true)
              .setValue(null);
    }
  }
}
