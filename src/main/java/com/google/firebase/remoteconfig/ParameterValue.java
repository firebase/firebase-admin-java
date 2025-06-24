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

// com.google.api.client.util.Key is no longer needed here.
import com.google.firebase.internal.NonNull;
import com.google.firebase.remoteconfig.internal.TemplateResponse.ParameterValueResponse;
// Ensured these specific DTO imports are present
import com.google.firebase.remoteconfig.internal.TemplateResponse.PersonalizationValueResponse;
import com.google.firebase.remoteconfig.internal.TemplateResponse.RolloutValueResponse;

import java.util.Objects;

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

    // Updated to use DTOs from TemplateResponse
    TemplateResponse.RolloutValueResponse rolloutDto = parameterValueResponse.getRolloutValue();
    if (rolloutDto != null) {
      int percent = (rolloutDto.getPercent() != null) ? rolloutDto.getPercent() : 0;
      return new RolloutsValue(rolloutDto.getRolloutId(), rolloutDto.getValue(), percent);
    }

    TemplateResponse.PersonalizationValueResponse personalizationDto =
            parameterValueResponse.getPersonalizationValue();
    if (personalizationDto != null) {
      return new PersonalizationValue(personalizationDto.getPersonalizationId());
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
    private final String rolloutId;
    private final String value;
    private final int percent;

    // Package-private constructor
    RolloutsValue(String rolloutId, String value, int percent) {
      this.rolloutId = checkNotNull(rolloutId, "rolloutId must not be null");
      this.value = value; // Value can be null
      this.percent = percent;
    }

    public String getRolloutId() {
      return rolloutId;
    }

    public String getValue() {
      return value;
    }

    public int getPercent() {
      return percent;
    }

    @Override
    ParameterValueResponse toParameterValueResponse() {
      TemplateResponse.RolloutValueResponse rolloutDto = new TemplateResponse.RolloutValueResponse();
      rolloutDto.setRolloutId(this.rolloutId);
      rolloutDto.setValue(this.value);
      rolloutDto.setPercent(this.percent); // int to Integer is fine

      ParameterValueResponse response = new ParameterValueResponse();
      response.setRolloutValue(rolloutDto);
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
    private final String personalizationId;

    // Package-private constructor
    PersonalizationValue(String personalizationId) {
      this.personalizationId = checkNotNull(personalizationId,
          "personalizationId must not be null");
    }

    public String getPersonalizationId() {
      return personalizationId;
    }

    @Override
    ParameterValueResponse toParameterValueResponse() {
      TemplateResponse.PersonalizationValueResponse personalizationDto =
          new TemplateResponse.PersonalizationValueResponse();
      personalizationDto.setPersonalizationId(this.personalizationId);

      ParameterValueResponse response = new ParameterValueResponse();
      response.setPersonalizationValue(personalizationDto);
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
