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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class TemplateTest {

  @Test
  public void testConstructor() {
    Template template = new Template();

    assertNotNull(template.getParameters());
    assertNotNull(template.getConditions());
    assertNotNull(template.getParameterGroups());
    assertTrue(template.getParameters().isEmpty());
    assertTrue(template.getConditions().isEmpty());
    assertTrue(template.getParameterGroups().isEmpty());
    assertNull(template.getETag());
  }

  @Test(expected = NullPointerException.class)
  public void testConstructorWithNullTemplateResponse() {
    new Template(null);
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullParameters() {
    Template template = new Template();
    template.setParameters(null);
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullConditions() {
    Template template = new Template();
    template.setConditions(null);
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullParameterGroups() {
    Template template = new Template();
    template.setParameterGroups(null);
  }

  @Test
  public void testEquality() {
    final Template templateOne = new Template();
    final Template templateTwo = new Template();

    assertEquals(templateOne, templateTwo);

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
    final Template templateThree = new Template()
            .setConditions(conditions)
            .setParameters(parameters);
    final Template templateFour = new Template()
            .setConditions(conditions)
            .setParameters(parameters);

    assertEquals(templateThree, templateFour);

    final Map<String, ParameterGroup> parameterGroups = ImmutableMap.of(
            "greetings_group", new ParameterGroup()
                    .setDescription("description")
                    .setParameters(parameters)
    );
    final Template templateFive = new Template()
            .setConditions(conditions)
            .setParameters(parameters)
            .setParameterGroups(parameterGroups);
    final Template templateSix = new Template()
            .setConditions(conditions)
            .setParameters(parameters)
            .setParameterGroups(parameterGroups);

    assertEquals(templateFive, templateSix);

    final Template templateSeven = new Template()
            .setETag("etag-123456789097-20");
    final Template templateEight = new Template()
            .setETag("etag-123456789097-20");

    assertEquals(templateSeven, templateEight);
    assertNotEquals(templateOne, templateThree);
    assertNotEquals(templateOne, templateFive);
    assertNotEquals(templateOne, templateSeven);
    assertNotEquals(templateThree, templateFive);
    assertNotEquals(templateThree, templateSeven);
    assertNotEquals(templateFive, templateSeven);
  }
}
