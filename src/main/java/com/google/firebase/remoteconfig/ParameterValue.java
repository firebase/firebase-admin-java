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

import com.google.api.client.util.Key;
import com.google.firebase.internal.NonNull;
import com.google.firebase.remoteconfig.internal.TemplateResponse.ParameterValueResponse;
// Removed RolloutValueResponse and PersonalizationValueResponse imports from TemplateResponse
// as ParameterValue will now have its own versions of these.

import java.util.Objects;
// Removed Map and HashMap imports as they are no longer directly used here after this change.

/**
 * Represents a Remote Config parameter value that can be used in a {@link Template}.
 */
public abstract class ParameterValue {

  /**
   * Creates a new {@link ParameterValue.Explicit} instance with the given value.
   *
   * @param value The value of the {@link ParameterValue.Explicit}.
   * @return A {@link ParameterValue.Explicit} instance.
   */
  public static Explicit of(String value) {
    return new Explicit(value);
  }

  /**
   * Creates a new {@link ParameterValue.InAppDefault} instance.
   *
   * @return A {@link ParameterValue.InAppDefault} instance.
   */
  public static InAppDefault inAppDefault() {
    return new InAppDefault();
  }

  abstract ParameterValueResponse toParameterValueResponse();

  static ParameterValue fromParameterValueResponse(
          @NonNull ParameterValueResponse parameterValueResponse) {
    checkNotNull(parameterValueResponse);

    if (parameterValueResponse.getValue() != null) {
      return ParameterValue.of(parameterValueResponse.getValue());
    }
    if (parameterValueResponse.isUseInAppDefault()) {
      return ParameterValue.inAppDefault();
    }

    // The getters getRolloutValue() and getPersonalizationValue() in ParameterValueResponse
    // will now return ParameterValue.RolloutsValue and ParameterValue.PersonalizationValue
    // respectively (as per the next subtask for TemplateResponse.java).
    // So, we can directly return them.
    if (parameterValueResponse.getRolloutValue() != null) {
      return parameterValueResponse.getRolloutValue();
    }
    if (parameterValueResponse.getPersonalizationValue() != null) {
      return parameterValueResponse.getPersonalizationValue();
    }
    return ParameterValue.of(null); // Fallback
  }

  public static final class Explicit extends ParameterValue {
    private final String value;

    private Explicit(String value) {
      this.value = value;
    }

    public String getValue() {
      return this.value;
    }

    @Override
    ParameterValueResponse toParameterValueResponse() {
      return new ParameterValueResponse().setValue(this.value);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Explicit explicit = (Explicit) o;
      return Objects.equals(value, explicit.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }

  public static final class InAppDefault extends ParameterValue {
    InAppDefault() { // Package-private constructor
      // Constructor content (if any)
    }

    @Override
    ParameterValueResponse toParameterValueResponse() {
      return new ParameterValueResponse().setUseInAppDefault(true);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
      return 1; // Simple constant hashCode
    }
  }

  public static final class RolloutsValue extends ParameterValue {
    @Key("rolloutId")
    private String rolloutId;

    @Key("value")
    private String value;

    @Key("percent")
    private int percent;

    public RolloutsValue() {} // Public no-argument constructor

    // Package-private constructor for factory method
    RolloutsValue(String rolloutId, String value, int percent) {
      this.rolloutId = checkNotNull(rolloutId, "rolloutId must not be null");
      this.value = value;
      this.percent = percent;
    }

    public String getRolloutId() {
      return rolloutId;
    }

    public void setRolloutId(String rolloutId) {
      this.rolloutId = checkNotNull(rolloutId, "rolloutId must not be null");
    }

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }

    public int getPercent() {
      return percent;
    }

    public void setPercent(int percent) {
      this.percent = percent;
    }

    @Override
    ParameterValueResponse toParameterValueResponse() {
      ParameterValueResponse response = new ParameterValueResponse();
      response.setRolloutValue(this); // Pass this instance directly
      return response;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      RolloutsValue that = (RolloutsValue) o;
      return percent == that.percent
              && Objects.equals(rolloutId, that.rolloutId)
              && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(rolloutId, value, percent);
    }
  }

  public static final class PersonalizationValue extends ParameterValue {
    @Key("personalizationId")
    private String personalizationId;

    public PersonalizationValue() {} // Public no-argument constructor

    // Package-private constructor for factory method
    PersonalizationValue(String personalizationId) {
      this.personalizationId = checkNotNull(personalizationId,
          "personalizationId must not be null");
    }

    public String getPersonalizationId() {
      return personalizationId;
    }

    public void setPersonalizationId(String personalizationId) {
      this.personalizationId = checkNotNull(personalizationId,
          "personalizationId must not be null");
    }

    @Override
    ParameterValueResponse toParameterValueResponse() {
      ParameterValueResponse response = new ParameterValueResponse();
      response.setPersonalizationValue(this); // Pass this instance directly
      return response;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      PersonalizationValue that = (PersonalizationValue) o;
      return Objects.equals(personalizationId, that.personalizationId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(personalizationId);
    }
  }

  public static RolloutsValue ofRollout(String rolloutId, String value, int percent) {
    return new RolloutsValue(rolloutId, value, percent);
  }

  public static PersonalizationValue ofPersonalization(String personalizationId) {
    return new PersonalizationValue(personalizationId);
  }
}
