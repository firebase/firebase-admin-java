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
import static java.util.stream.Collectors.toList;

import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import com.google.firebase.remoteconfig.internal.TemplateResponse.ExperimentValueResponse;
import com.google.firebase.remoteconfig.internal.TemplateResponse.ExperimentVariantValueResponse;
import com.google.firebase.remoteconfig.internal.TemplateResponse.ParameterValueResponse;
import com.google.firebase.remoteconfig.internal.TemplateResponse.PersonalizationValueResponse;
import com.google.firebase.remoteconfig.internal.TemplateResponse.RolloutValueResponse;

import java.util.ArrayList;
import java.util.List;
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

  /**
   * Creates a new {@link ParameterValue.RolloutValue} instance.
   *
   * @param rolloutId The rollout ID.
   * @param value The value of the rollout.
   * @param percent The percentage of the rollout.
   * @return A {@link ParameterValue.RolloutValue} instance.
   */
  public static RolloutValue ofRollout(String rolloutId, String value, double percent) {
    return new RolloutValue(rolloutId, value, percent);
  }

  /**
   * Creates a new {@link ParameterValue.PersonalizationValue} instance.
   *
   * @param personalizationId The personalization ID.
   * @return A {@link ParameterValue.PersonalizationValue} instance.
   */
  public static PersonalizationValue ofPersonalization(String personalizationId) {
    return new PersonalizationValue(personalizationId);
  }

  /**
   * Creates a new {@link ParameterValue.ExperimentValue} instance.
   *
   * @param experimentId The experiment ID.
   * @param variantValues The list of experiment variant values.
   * @return A {@link ParameterValue.ExperimentValue} instance.
   */
  public static ExperimentValue ofExperiment(String experimentId,
                                   List<ExperimentVariantValue> variantValues) {
    return new ExperimentValue(experimentId, variantValues);
  }

  abstract ParameterValueResponse toParameterValueResponse();

  static ParameterValue fromParameterValueResponse(
          @NonNull ParameterValueResponse parameterValueResponse) {
    checkNotNull(parameterValueResponse);
    if (parameterValueResponse.isUseInAppDefault()) {
      return ParameterValue.inAppDefault();
    }
    if (parameterValueResponse.getRolloutValue() != null) {
      RolloutValueResponse rv = parameterValueResponse.getRolloutValue();
      // Protobuf serialization does not set values for fields on the wire when
      // they are equal to the default value for the field type. When deserializing,
      // can appear as the value not being set. Explicitly handle default value for
      // the percent field since 0 is a valid value. 
      double percent = 0;
      if (rv.getPercent() != null) {
        percent = rv.getPercent();
      }
      return ParameterValue.ofRollout(rv.getRolloutId(), rv.getValue(), percent);
    }
    if (parameterValueResponse.getPersonalizationValue() != null) {
      PersonalizationValueResponse pv = parameterValueResponse.getPersonalizationValue();
      return ParameterValue.ofPersonalization(pv.getPersonalizationId());
    }
    if (parameterValueResponse.getExperimentValue() != null) {
      ExperimentValueResponse ev = parameterValueResponse.getExperimentValue();
      List<ExperimentVariantValue> variantValues = ev.getExperimentVariantValues().stream()
          .map(evv -> new ExperimentVariantValue(
            evv.getVariantId(),  evv.getValue(), evv.getNoChange()))
          .collect(toList());
      return ParameterValue.ofExperiment(ev.getExperimentId(), variantValues);
    }
    return ParameterValue.of(parameterValueResponse.getValue());
  }

  /**
   * Represents an explicit Remote Config parameter value with a value that the
   * parameter is set to.
   */
  public static final class Explicit extends ParameterValue {

    private final String value;

    private Explicit(String value) {
      this.value = value;
    }

    /**
     * Gets the value of {@link ParameterValue.Explicit}.
     *
     * @return The value.
     */
    public String getValue() {
      return this.value;
    }

    @Override
    ParameterValueResponse toParameterValueResponse() {
      return new ParameterValueResponse()
              .setValue(this.value);
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

  /**
   * Represents an in app default parameter value.
   */
  public static final class InAppDefault extends ParameterValue {

    @Override
    ParameterValueResponse toParameterValueResponse() {
      return new ParameterValueResponse().setUseInAppDefault(true);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      return true;
    }
  }

  /**
   * Represents a Rollout value.
   */
  public static final class RolloutValue extends ParameterValue {
    private final String rolloutId;
    private final String value;
    private final double percent;

    private RolloutValue(String rolloutId, String value, double percent) {
      this.rolloutId = rolloutId;
      this.value = value;
      this.percent = percent;
    }

    /**
     * Gets the ID of the Rollout linked to this value.
     *
     * @return The Rollout ID
     */
    public String getRolloutId() {
      return rolloutId;
    }

    /**
     * Gets the value that is being rolled out.
     *
     * @return The rollout value
     */
    public String getValue() {
      return value;
    }

    /**
     * Gets the rollout percentage representing the exposure of rollout value
     * in the target audience.
     *
     * @return Percentage of audience exposed to the rollout
     */
    public double getPercent() {
      return percent;
    }

    @Override
    ParameterValueResponse toParameterValueResponse() {
      return new ParameterValueResponse().setRolloutValue(
              new RolloutValueResponse()
                      .setRolloutId(this.rolloutId)
                      .setValue(this.value)
                      .setPercent(this.percent));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      RolloutValue that = (RolloutValue) o;
      return Double.compare(that.percent, percent) == 0
              && Objects.equals(rolloutId, that.rolloutId)
              && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(rolloutId, value, percent);
    }
  }

  /**
   * Represents a Personalization value.
   */
  public static final class PersonalizationValue extends ParameterValue {
    private final String personalizationId;

    private PersonalizationValue(String personalizationId) {
      this.personalizationId = personalizationId;
    }

    /**
     * Gets the ID of the Personalization linked to this value.
     *
     * @return The Personalization ID
     */
    public String getPersonalizationId() {
      return personalizationId;
    }

    @Override
    ParameterValueResponse toParameterValueResponse() {
      return new ParameterValueResponse().setPersonalizationValue(
              new PersonalizationValueResponse()
                      .setPersonalizationId(this.personalizationId));
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

  /**
   * Represents a specific variant within an Experiment.
   */
  public static final class ExperimentVariantValue {
    private final String variantId;
    private final String value;
    private final Boolean noChange;

    ExperimentVariantValue(String variantId, String value, Boolean noChange) {
      this.variantId = variantId;
      this.value = value;
      this.noChange = noChange;
    }

    /**
     * Creates a new {@link ExperimentVariantValue} instance.
     *
     * @param variantId The variant ID.
     * @param value The value of the variant.
     * @return A {@link ExperimentVariantValue} instance.
     */
    public static ExperimentVariantValue of(String variantId, String value) {
      return new ExperimentVariantValue(variantId, value, null);
    }

    /**
     * Creates a new {@link ExperimentVariantValue} instance.
     *
     * @param variantId The variant ID.
     * @return A {@link ExperimentVariantValue} instance.
     */
    public static ExperimentVariantValue ofNoChange(String variantId) {
      return new ExperimentVariantValue(variantId, null, true);
    }

    /**
     * Gets the ID of the experiment variant.
     *
     * @return The variant ID
     */
    public String getVariantId() {
      return variantId;
    }

    /**
     * Gets the value of the experiment variant.
     *
     * @return The variant value
     */
    @Nullable
    public String getValue() {
      return value;
    }

    @Nullable
    Boolean getNoChange() {
      return noChange;
    }

    /**
     * Returns whether the experiment variant is a no-change variant.
     *
     * @return true if the experiment variant is a no-change variant, and false otherwise.
     */
    public boolean isNoChange() {
      return Boolean.TRUE.equals(noChange);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ExperimentVariantValue that = (ExperimentVariantValue) o;
      return noChange == that.noChange
              && Objects.equals(variantId, that.variantId)
              && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(variantId, value, noChange);
    }
  }

  /**
   * Represents an Experiment value.
   */
  public static final class ExperimentValue extends ParameterValue {
    private final String experimentId;
    private final List<ExperimentVariantValue> variantValues;

    private ExperimentValue(String experimentId, List<ExperimentVariantValue> variantValues) {
      this.experimentId = experimentId;
      this.variantValues = variantValues;
    }

    /**
     * Gets the ID of the experiment linked to this value.
     *
     * @return The Experiment ID
     */
    public String getExperimentId() {
      return experimentId;
    }

    /**
     * Gets a collection of variant values served by the experiment.
     *
     * @return List of {@link ExperimentVariantValue}
     */
    public List<ExperimentVariantValue> getExperimentVariantValues() {
      return variantValues;
    }

    @Override
    ParameterValueResponse toParameterValueResponse() {
      List<ExperimentVariantValueResponse> variantValueResponses = variantValues.stream()
          .map(variantValue -> new ExperimentVariantValueResponse()
              .setVariantId(variantValue.getVariantId())
              .setValue(variantValue.getValue())
              .setNoChange(variantValue.getNoChange()))
          .collect(toList());
      return new ParameterValueResponse().setExperimentValue(
              new ExperimentValueResponse()
                      .setExperimentId(this.experimentId)
                      .setExperimentVariantValues(variantValueResponses));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ExperimentValue that = (ExperimentValue) o;
      return Objects.equals(experimentId, that.experimentId)
              && Objects.equals(variantValues, that.variantValues);
    }

    @Override
    public int hashCode() {
      return Objects.hash(experimentId, variantValues);
    }
  }
}
