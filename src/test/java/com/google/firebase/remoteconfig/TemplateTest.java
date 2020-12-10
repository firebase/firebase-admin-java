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
import com.google.firebase.remoteconfig.internal.TemplateResponse;

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

  @Test
  public void testConstructorWithETag() {
    Template template = new Template("etag-01-324324");

    assertNotNull(template.getParameters());
    assertNotNull(template.getConditions());
    assertNotNull(template.getParameterGroups());
    assertTrue(template.getParameters().isEmpty());
    assertTrue(template.getConditions().isEmpty());
    assertTrue(template.getParameterGroups().isEmpty());
    assertEquals("etag-01-324324", template.getETag());
  }

  @Test(expected = NullPointerException.class)
  public void testConstructorWithNullTemplateResponse() {
    new Template((TemplateResponse) null);
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

    final Template templateFour = new Template("etag-123456789097-20");

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

  @Test(expected = FirebaseRemoteConfigException.class)
  public void testFromJSONWithInvalidString() throws FirebaseRemoteConfigException {
    Template.fromJSON("abc");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFromJSONWithEmptyString() throws FirebaseRemoteConfigException {
    Template.fromJSON("");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFromJSONWithNullString() throws FirebaseRemoteConfigException {
    Template.fromJSON(null);
  }

  @Test
  public void testFromJSONWithEmptyTemplateString() throws FirebaseRemoteConfigException {
    Template template = Template.fromJSON("{}");

    assertNotNull(template.getParameters());
    assertNotNull(template.getConditions());
    assertNotNull(template.getParameterGroups());
    assertTrue(template.getParameters().isEmpty());
    assertTrue(template.getConditions().isEmpty());
    assertTrue(template.getParameterGroups().isEmpty());
    assertNull(template.getETag());
  }

  @Test
  public void testFromJSONWithNonEmptyTemplateString() throws FirebaseRemoteConfigException {
    Template template = Template.fromJSON("{"
            + "  \"etag\": \"etag-001234\","
            + "  \"conditions\": ["
            + "    {"
            + "      \"name\": \"ios_en\","
            + "      \"expression\": \"device.os == 'ios' && device.country in ['us', 'uk']\","
            + "      \"tagColor\": \"INDIGO\""
            + "    },"
            + "    {"
            + "      \"name\": \"android_en\","
            + "      \"expression\": \"device.os == 'android' && device.country in ['us', 'uk']\""
            + "    }"
            + "  ]"
            + "}");

    assertNotNull(template.getParameters());
    assertNotNull(template.getConditions());
    assertNotNull(template.getParameterGroups());
    assertTrue(template.getParameters().isEmpty());
    assertEquals(2, template.getConditions().size());
    assertEquals("ios_en", template.getConditions().get(0).getName());
    assertEquals("device.os == 'ios' && device.country in ['us', 'uk']",
            template.getConditions().get(0).getExpression());
    assertEquals(TagColor.INDIGO, template.getConditions().get(0).getTagColor());
    assertEquals("android_en", template.getConditions().get(1).getName());
    assertEquals("device.os == 'android' && device.country in ['us', 'uk']",
            template.getConditions().get(1).getExpression());
    assertNull(template.getConditions().get(1).getTagColor());
    assertTrue(template.getParameterGroups().isEmpty());
    assertEquals("etag-001234", template.getETag());
  }

  @Test
  public void testFromJSONWithVersion() throws FirebaseRemoteConfigException {
    final Version expectedVersion = new Version(new TemplateResponse.VersionResponse()
            .setDescription("template version")
            .setUpdateTime("2020-12-08T15:49:51.887878Z")
            .setUpdateUser(new TemplateResponse.UserResponse().setEmail("user@user.com"))
            .setLegacy(false)
            .setUpdateType("INCREMENTAL_UPDATE")
            .setRollbackSource("26")
            .setVersionNumber("34")
            .setUpdateOrigin("ADMIN_SDK_NODE")
    );
    String jsonString = "{\"parameters\":{},\"conditions\":[],\"parameterGroups\":{},"
            + "\"version\":{\"versionNumber\":\"34\","
            + "\"updateTime\":\"Tue, 08 Dec 2020 15:49:51 UTC\","
            + "\"updateOrigin\":\"ADMIN_SDK_NODE\",\"updateType\":\"INCREMENTAL_UPDATE\","
            + "\"updateUser\":{\"email\":\"user@user.com\"},\"rollbackSource\":\"26\","
            + "\"legacy\":false,\"description\":\"template version\"}}";
    Template template = Template.fromJSON(jsonString);

    assertNotNull(template.getParameters());
    assertNotNull(template.getConditions());
    assertNotNull(template.getParameterGroups());
    assertTrue(template.getParameters().isEmpty());
    assertTrue(template.getConditions().isEmpty());
    assertTrue(template.getParameterGroups().isEmpty());
    assertNull(template.getETag());
    // check version
    assertEquals(expectedVersion, template.getVersion());
    // update time should be correctly converted to milliseconds
    assertEquals(1607442591000L, template.getVersion().getUpdateTime());
  }

  @Test
  public void testToJSONWithEmptyTemplate() {
    String jsonString = new Template().toJSON();

    assertEquals("{\"conditions\":[],"
            + "\"parameterGroups\":{},\"parameters\":{}}", jsonString);
  }

  @Test
  public void testToJSONWithParameterValues() {
    Template t = new Template();
    t.getParameters()
            .put("with_value", new Parameter().setDefaultValue(ParameterValue.of("hello")));
    t.getParameters()
            .put("with_inApp", new Parameter().setDefaultValue(ParameterValue.inAppDefault()));
    String jsonString = t.toJSON();

    assertEquals("{\"conditions\":[],\"parameterGroups\":{},"
            + "\"parameters\":{\"with_value\":{\"conditionalValues\":{},"
            + "\"defaultValue\":{\"value\":\"hello\"}},\"with_inApp\":{\"conditionalValues\":{},"
            + "\"defaultValue\":{\"useInAppDefault\":true}}}}", jsonString);
  }

  @Test
  public void testToJSONWithEtag() {
    String jsonString = new Template("etag-12345").toJSON();

    assertEquals("{\"conditions\":[],\"etag\":\"etag-12345\",\"parameterGroups\":{},"
            + "\"parameters\":{}}", jsonString);
  }

  @Test
  public void testToJSONWithEtagAndConditions() {
    String jsonString = new Template("etag-0010201")
            .setConditions(CONDITIONS).toJSON();

    assertEquals("{\"conditions\":[{\"expression\":\"exp ios\",\"name\":\"ios_en\","
            + "\"tagColor\":\"INDIGO\"},{\"expression\":\"exp android\","
            + "\"name\":\"android_en\"}],\"etag\":\"etag-0010201\",\"parameterGroups\":{},"
            + "\"parameters\":{}}", jsonString);
  }

  @Test
  public void testToJSONWithVersion() {
    Version version = new Version(new TemplateResponse.VersionResponse()
            .setDescription("template version")
            .setUpdateTime("2020-12-08T15:49:51.887878Z")
            .setUpdateUser(new TemplateResponse.UserResponse().setEmail("user@user.com"))
            .setLegacy(false)
            .setUpdateType("INCREMENTAL_UPDATE")
            .setRollbackSource("26")
            .setVersionNumber("34")
            .setUpdateOrigin("ADMIN_SDK_NODE")
    );
    String jsonString = new Template().setVersion(version).toJSON();

    assertEquals("{\"conditions\":[],\"parameterGroups\":{},\"parameters\":{},"
            + "\"version\":{\"description\":\"template version\",\"legacy\":false,"
            + "\"rollbackSource\":\"26\",\"updateOrigin\":\"ADMIN_SDK_NODE\","
            + "\"updateTime\":\"Tue, 08 Dec 2020 15:49:51 UTC\","
            + "\"updateType\":\"INCREMENTAL_UPDATE\",\"updateUser\":{"
            + "\"email\":\"user@user.com\"},\"versionNumber\":\"34\"}}", jsonString);
  }

  @Test
  public void testToJSONAndFromJSON() throws FirebaseRemoteConfigException {
    Template originalTemplate = new Template();
    Template otherTemplate = Template.fromJSON(originalTemplate.toJSON());

    assertEquals(originalTemplate, otherTemplate);

    Version expectedVersion = Version.withDescription("promo version");
    Template expectedTemplate = new Template("etag-0010201")
            .setParameters(PARAMETERS)
            .setConditions(CONDITIONS)
            .setParameterGroups(PARAMETER_GROUPS)
            .setVersion(expectedVersion);
    Template actualTemplate = Template.fromJSON(expectedTemplate.toJSON());

    assertEquals(expectedTemplate, actualTemplate);
  }
}
