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

  private static final List<Condition> CONDITIONS = ImmutableList.<Condition>of(
          new Condition("ios_en", "exp ios")
                  .setTagColor(TagColor.INDIGO),
          new Condition("android_en", "exp android")
  );

  private static final Map<String, ParameterValue> CONDITIONAL_VALUES = ImmutableMap.of(
          "ios", ParameterValue.of("hello ios"),
          "android", ParameterValue.of("hello android"),
          "promo", ParameterValue.inAppDefault()
  );

  private static final Map<String, Parameter> PARAMETERS = ImmutableMap.of(
          "greeting_header", new Parameter()
                  .setDefaultValue(ParameterValue.inAppDefault())
                  .setDescription("greeting header text")
                  .setConditionalValues(CONDITIONAL_VALUES),
          "greeting_text", new Parameter()
                  .setDefaultValue(ParameterValue.inAppDefault())
                  .setDescription("greeting text")
                  .setConditionalValues(CONDITIONAL_VALUES)
  );

  private static final Map<String, ParameterGroup> PARAMETER_GROUPS = ImmutableMap.of(
          "greetings_group", new ParameterGroup()
                  .setDescription("description")
                  .setParameters(PARAMETERS)
  );

  private static final Template EMPTY_TEMPLATE = new Template();

  private static final Template TEMPLATE_WITH_CONDITIONS_PARAMETERS = new Template()
          .setConditions(CONDITIONS)
          .setParameters(PARAMETERS);

  private static final Template TEMPLATE_WITH_CONDITIONS_PARAMETERS_GROUPS = new Template()
          .setConditions(CONDITIONS)
          .setParameters(PARAMETERS)
          .setParameterGroups(PARAMETER_GROUPS);

  private static final Template TEMPLATE_WITH_ETAG = new Template()
          .setETag("etag-123456789097-20");

  private static final Template TEMPLATE_WITH_VERSION = new Template()
          .setVersion(Version.withDescription("promo version"));

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

    assertEquals(EMPTY_TEMPLATE, templateOne);

    final Template templateTwo = new Template()
            .setConditions(CONDITIONS)
            .setParameters(PARAMETERS);

    assertEquals(TEMPLATE_WITH_CONDITIONS_PARAMETERS, templateTwo);

    final Template templateThree = new Template()
            .setConditions(CONDITIONS)
            .setParameters(PARAMETERS)
            .setParameterGroups(PARAMETER_GROUPS);

    assertEquals(TEMPLATE_WITH_CONDITIONS_PARAMETERS_GROUPS, templateThree);

    final Template templateFour = new Template()
            .setETag("etag-123456789097-20");

    assertEquals(TEMPLATE_WITH_ETAG, templateFour);

    final Template templateFive = new Template()
            .setVersion(Version.withDescription("promo version"));

    assertEquals(TEMPLATE_WITH_VERSION, templateFive);
    assertNotEquals(templateOne, templateTwo);
    assertNotEquals(templateOne, templateThree);
    assertNotEquals(templateOne, templateFour);
    assertNotEquals(templateOne, templateFive);
    assertNotEquals(templateTwo, templateThree);
    assertNotEquals(templateTwo, templateFour);
    assertNotEquals(templateTwo, templateFive);
    assertNotEquals(templateThree, templateFour);
    assertNotEquals(templateThree, templateFive);
    assertNotEquals(templateFour, templateFive);
  }
}
