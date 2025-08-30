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
  public void testEquality() {
    ParameterValue.Explicit parameterValueOne = ParameterValue.of("value");
    ParameterValue.Explicit parameterValueTwo = ParameterValue.of("value");
    ParameterValue.Explicit parameterValueThree = ParameterValue.of("title");

    assertEquals(parameterValueOne, parameterValueTwo);
    assertNotEquals(parameterValueOne, parameterValueThree);

    ParameterValue.InAppDefault parameterValueFour = ParameterValue.inAppDefault();
    ParameterValue.InAppDefault parameterValueFive = ParameterValue.inAppDefault();

    assertEquals(parameterValueFour, parameterValueFive);
  }
}
