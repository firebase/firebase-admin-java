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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.firebase.remoteconfig.ParameterValue.ExperimentVariantValue;
import org.junit.Test;

public class ParameterValueTest {

  @Test
  public void testCreateExplicitValue() {
    final ParameterValue.Explicit parameterValue = ParameterValue.of("title text");

    assertEquals("title text", parameterValue.getValue());
  }

  @Test
  public void testCreateInAppDefault() {
    final ParameterValue.InAppDefault parameterValue = ParameterValue.inAppDefault();

    assertEquals(ParameterValue.InAppDefault.class, parameterValue.getClass());
  }

  @Test
  public void testCreateRolloutValue() {
    final ParameterValue.RolloutValue parameterValue =
            ParameterValue.ofRollout("rollout_1", "value_1", 10.0);

    assertEquals("rollout_1", parameterValue.getRolloutId());
    assertEquals("value_1", parameterValue.getValue());
    assertEquals(10.0, parameterValue.getPercent(), 0.0);
  }

  @Test
  public void testCreatePersonalizationValue() {
    final ParameterValue.PersonalizationValue parameterValue =
            ParameterValue.ofPersonalization("personalization_1");

    assertEquals("personalization_1", parameterValue.getPersonalizationId());
  }

  @Test
  public void testCreateExperimentValue() {
    final ParameterValue.ExperimentValue parameterValue =
            ParameterValue.ofExperiment("experiment_1", ImmutableList.of(
                    ExperimentVariantValue.of("variant_1", "value_1"),
                    ExperimentVariantValue.ofNoChange("variant_2")
            ));

    assertEquals("experiment_1", parameterValue.getExperimentId());
    assertEquals(2, parameterValue.getExperimentVariantValues().size());
    ExperimentVariantValue variant1 = parameterValue.getExperimentVariantValues().get(0);
    assertEquals("variant_1", variant1.getVariantId());
    assertEquals("value_1", variant1.getValue());
    assertEquals(false, variant1.isNoChange());
    ExperimentVariantValue variant2 = parameterValue.getExperimentVariantValues().get(1);
    assertEquals("variant_2", variant2.getVariantId());
    assertEquals(null, variant2.getValue());
    assertEquals(true, variant2.isNoChange());
  }

  @Test
  public void testEquality() {
    ParameterValue.Explicit parameterValueOne = ParameterValue.of("value");
    ParameterValue.Explicit parameterValueTwo = ParameterValue.of("value");
    ParameterValue.Explicit parameterValueThree = ParameterValue.of("title");

    assertEquals(parameterValueOne, parameterValueTwo);
    assertNotEquals(parameterValueOne, parameterValueThree);

    ParameterValue.InAppDefault parameterValueFour = ParameterValue.inAppDefault();
    ParameterValue.InAppDefault parameterValueFive = ParameterValue.inAppDefault();

    assertEquals(parameterValueFour, parameterValueFive);

    ParameterValue.RolloutValue rolloutValueOne =
            ParameterValue.ofRollout("rollout_1", "value_1", 10.0);
    ParameterValue.RolloutValue rolloutValueTwo =
            ParameterValue.ofRollout("rollout_1", "value_1", 10.0);
    ParameterValue.RolloutValue rolloutValueThree =
            ParameterValue.ofRollout("rollout_2", "value_1", 10.0);

    assertEquals(rolloutValueOne, rolloutValueTwo);
    assertNotEquals(rolloutValueOne, rolloutValueThree);

    ParameterValue.PersonalizationValue personalizationValueOne =
            ParameterValue.ofPersonalization("personalization_1");
    ParameterValue.PersonalizationValue personalizationValueTwo =
            ParameterValue.ofPersonalization("personalization_1");
    ParameterValue.PersonalizationValue personalizationValueThree =
            ParameterValue.ofPersonalization("personalization_2");

    assertEquals(personalizationValueOne, personalizationValueTwo);
    assertNotEquals(personalizationValueOne, personalizationValueThree);

    ParameterValue.ExperimentValue experimentValueOne =
            ParameterValue.ofExperiment("experiment_1", ImmutableList.of(
                    ExperimentVariantValue.of("variant_1", "value_1")
            ));
    ParameterValue.ExperimentValue experimentValueTwo =
            ParameterValue.ofExperiment("experiment_1", ImmutableList.of(
                    ExperimentVariantValue.of("variant_1", "value_1")
            ));
    ParameterValue.ExperimentValue experimentValueThree =
            ParameterValue.ofExperiment("experiment_2", ImmutableList.of(
                    ExperimentVariantValue.of("variant_1", "value_1")
            ));
    ParameterValue.ExperimentValue experimentValueFour =
            ParameterValue.ofExperiment("experiment_1", ImmutableList.of(
                    ExperimentVariantValue.of("variant_2", "value_2")
            ));

    assertEquals(experimentValueOne, experimentValueTwo);
    assertNotEquals(experimentValueOne, experimentValueThree);
    assertNotEquals(experimentValueOne, experimentValueFour);
  }
}
