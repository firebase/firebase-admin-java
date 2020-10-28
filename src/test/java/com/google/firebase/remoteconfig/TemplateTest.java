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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class TemplateTest {

  @Test
  public void testConstructor() {
    Template t = new Template();

    assertNotNull(t.getParameters());
    assertNotNull(t.getConditions());
    assertNotNull(t.getParameterGroups());
    assertEquals(0, t.getParameters().size());
    assertEquals(0, t.getConditions().size());
    assertEquals(0, t.getParameterGroups().size());
    assertNull(t.getETag());
  }

  @Test(expected = NullPointerException.class)
  public void testConstructorWithNullTemplateResponse() {
    Template t = new Template(null);
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullParameters() {
    Template t = new Template();
    t.setParameters(null);
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullConditions() {
    Template t = new Template();
    t.setConditions(null);
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullParameterGroups() {
    Template t = new Template();
    t.setParameterGroups(null);
  }

  @Test
  public void testEquality() {
    final Template t1 = new Template();
    final Template t2 = new Template();

    assertEquals(t1, t2);

    final List<Condition> conditions = ImmutableList.<Condition>of(
            new Condition("ios_en", "exp ios")
                    .setTagColor(TagColor.INDIGO),
            new Condition("android_en", "exp android")
    );
    final Map<String, ParameterValue> conditionalValues = ImmutableMap.of(
            "ios", ParameterValue.of("hello ios"),
            "android", ParameterValue.of("hello android"),
            "promo", ParameterValue.inAppDefault()
    );
    final Map<String, Parameter> parameters = ImmutableMap.of(
            "greeting_header", new Parameter()
                    .setDefaultValue(ParameterValue.inAppDefault())
                    .setDescription("greeting header text")
                    .setConditionalValues(conditionalValues),
            "greeting_text", new Parameter()
                    .setDefaultValue(ParameterValue.inAppDefault())
                    .setDescription("greeting text")
                    .setConditionalValues(conditionalValues)
    );
    final Template t3 = new Template()
            .setConditions(conditions)
            .setParameters(parameters);
    final Template t4 = new Template()
            .setConditions(conditions)
            .setParameters(parameters);

    assertEquals(t3, t4);

    final Map<String, ParameterGroup> parameterGroups = ImmutableMap.of(
            "greetings_group", new ParameterGroup()
                    .setDescription("description")
                    .setParameters(parameters)
    );
    final Template t5 = new Template()
            .setConditions(conditions)
            .setParameters(parameters)
            .setParameterGroups(parameterGroups);
    final Template t6 = new Template()
            .setConditions(conditions)
            .setParameters(parameters)
            .setParameterGroups(parameterGroups);

    assertEquals(t5, t6);

    final Template t7 = new Template()
            .setETag("etag-123456789097-20");
    final Template t8 = new Template()
            .setETag("etag-123456789097-20");

    assertEquals(t7, t8);
    assertNotEquals(t1, t3);
    assertNotEquals(t1, t5);
    assertNotEquals(t1, t7);
    assertNotEquals(t3, t5);
    assertNotEquals(t3, t7);
    assertNotEquals(t5, t7);
  }
}
