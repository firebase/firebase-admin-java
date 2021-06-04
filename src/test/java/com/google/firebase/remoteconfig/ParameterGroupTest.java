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

public class ParameterGroupTest {

  @Test
  public void testConstructor() {
    final ParameterGroup parameterGroup = new ParameterGroup();

    assertNotNull(parameterGroup.getParameters());
    assertTrue(parameterGroup.getParameters().isEmpty());
    assertNull(parameterGroup.getDescription());
  }

  @Test(expected = NullPointerException.class)
  public void testConstructorWithNullParameterGroupResponse() {
    new ParameterGroup(null);
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullParameters() {
    ParameterGroup parameterGroup = new ParameterGroup();
    parameterGroup.setParameters(null);
  }

  @Test
  public void testEquality() {
    final ParameterGroup parameterGroupOne = new ParameterGroup();
    final ParameterGroup parameterGroupTwo = new ParameterGroup();

    assertEquals(parameterGroupOne, parameterGroupTwo);

    final ParameterGroup parameterGroupThree = new ParameterGroup()
            .setDescription("description");
    final ParameterGroup parameterGroupFour = new ParameterGroup()
            .setDescription("description");

    assertEquals(parameterGroupThree, parameterGroupFour);

    final Map<String, Parameter> parameters = ImmutableMap.of(
            "header_text", new Parameter().setDefaultValue(ParameterValue.of("Welcome")),
            "promo", new Parameter()
                    .setDefaultValue(ParameterValue.inAppDefault())
                    .setConditionalValues(ImmutableMap.<String, ParameterValue>of(
                            "ios", ParameterValue.of("ios header text")
                    ))
    );
    final ParameterGroup parameterGroupFive = new ParameterGroup()
            .setDescription("description")
            .setParameters(parameters);
    final ParameterGroup parameterGroupSix = new ParameterGroup()
            .setDescription("description")
            .setParameters(parameters);

    assertEquals(parameterGroupFive, parameterGroupSix);
    assertNotEquals(parameterGroupOne, parameterGroupThree);
    assertNotEquals(parameterGroupOne, parameterGroupFive);
    assertNotEquals(parameterGroupThree, parameterGroupFive);
  }
}
