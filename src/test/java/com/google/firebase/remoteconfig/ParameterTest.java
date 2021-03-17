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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

import org.junit.Test;

public class ParameterTest {

  @Test
  public void testConstructor() {
    final Parameter parameter = new Parameter();

    assertNotNull(parameter.getConditionalValues());
    assertTrue(parameter.getConditionalValues().isEmpty());
    assertNull(parameter.getDefaultValue());
    assertNull(parameter.getDescription());
  }

  @Test(expected = NullPointerException.class)
  public void testConstructorWithNullParameterResponse() {
    new Parameter(null);
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullConditionalValues() {
    Parameter parameter = new Parameter();
    parameter.setConditionalValues(null);
  }

  @Test
  public void testEquality() {
    final Parameter parameterOne = new Parameter()
            .setDefaultValue(ParameterValue.of("hello"));
    final Parameter parameterTwo = new Parameter()
            .setDefaultValue(ParameterValue.of("hello"));

    assertEquals(parameterOne, parameterTwo);

    final Parameter parameterThree = new Parameter()
            .setDefaultValue(ParameterValue.inAppDefault())
            .setDescription("greeting text");
    final Parameter parameterFour = new Parameter()
            .setDefaultValue(ParameterValue.inAppDefault())
            .setDescription("greeting text");

    assertEquals(parameterThree, parameterFour);

    final Map<String, ParameterValue> conditionalValues = ImmutableMap.of(
            "ios", ParameterValue.of("hello ios"),
            "android", ParameterValue.of("hello android"),
            "promo", ParameterValue.inAppDefault()
    );
    final Parameter parameterFive = new Parameter()
            .setDefaultValue(ParameterValue.inAppDefault())
            .setDescription("greeting text")
            .setConditionalValues(conditionalValues);
    final Parameter parameterSix = new Parameter()
            .setDefaultValue(ParameterValue.inAppDefault())
            .setDescription("greeting text")
            .setConditionalValues(conditionalValues);

    assertEquals(parameterFive, parameterSix);
    assertNotEquals(parameterOne, parameterThree);
    assertNotEquals(parameterOne, parameterFive);
    assertNotEquals(parameterThree, parameterFive);
  }
}
