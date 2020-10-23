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

import com.google.common.collect.ImmutableMap;

import java.util.Map;

import org.junit.Test;

public class ParameterGroupTest {

  @Test
  public void testConstructor() {
    final ParameterGroup pg = new ParameterGroup();
    assertNotNull(pg.getParameters());
    assertEquals(0, pg.getParameters().size());
    assertNull(pg.getDescription());
  }

  @Test(expected = NullPointerException.class)
  public void testConstructorWithNullParameterGroupResponse() {
    ParameterGroup pg = new ParameterGroup(null);
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullParameters() {
    ParameterGroup pg = new ParameterGroup();
    pg.setParameters(null);
  }

  @Test
  public void testEquality() {
    final ParameterGroup p1 = new ParameterGroup();
    final ParameterGroup p2 = new ParameterGroup();
    assertEquals(p1, p2);

    final ParameterGroup p3 = new ParameterGroup()
            .setDescription("description");
    final ParameterGroup p4 = new ParameterGroup()
            .setDescription("description");
    assertEquals(p3, p4);

    final Map<String, Parameter> parameters = ImmutableMap.of(
            "header_text", new Parameter().setDefaultValue(ParameterValue.of("Welcome")),
            "promo", new Parameter()
                    .setDefaultValue(ParameterValue.inAppDefault())
                    .setConditionalValues(ImmutableMap.<String, ParameterValue>of(
                            "ios", ParameterValue.of("ios header text")
                    ))
    );
    final ParameterGroup p5 = new ParameterGroup()
            .setDescription("description")
            .setParameters(parameters);
    final ParameterGroup p6 = new ParameterGroup()
            .setDescription("description")
            .setParameters(parameters);
    assertEquals(p5, p6);
    assertNotEquals(p1, p3);
    assertNotEquals(p1, p5);
    assertNotEquals(p3, p5);
  }
}
