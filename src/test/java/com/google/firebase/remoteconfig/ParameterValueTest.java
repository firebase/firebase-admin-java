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
    final ParameterValue.Explicit pv = ParameterValue.of("title text");

    assertEquals("title text", pv.getValue());
  }

  @Test
  public void testCreateInAppDefault() {
    final ParameterValue.InAppDefault pv = ParameterValue.inAppDefault();

    assertEquals(ParameterValue.InAppDefault.class, pv.getClass());
  }

  @Test
  public void testEquality() {
    ParameterValue.Explicit pv1 = ParameterValue.of("value");
    ParameterValue.Explicit pv2 = ParameterValue.of("value");
    ParameterValue.Explicit pv3 = ParameterValue.of("title");

    assertEquals(pv1, pv2);
    assertNotEquals(pv1, pv3);

    ParameterValue.InAppDefault pv4 = ParameterValue.inAppDefault();
    ParameterValue.InAppDefault pv5 = ParameterValue.inAppDefault();

    assertEquals(pv4, pv5);
  }
}
