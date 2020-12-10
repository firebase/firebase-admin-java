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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonParser;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.OutgoingHttpRequest;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.internal.SdkUtils;
import com.google.firebase.remoteconfig.internal.TemplateResponse;
import com.google.firebase.testing.TestResponseInterceptor;
import com.google.firebase.testing.TestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

public class FirebaseRemoteConfigClientImplTest {

  private static final String TEST_REMOTE_CONFIG_URL =
          "https://firebaseremoteconfig.googleapis.com/v1/projects/test-project/remoteConfig";

  private static final List<Integer> HTTP_STATUS_CODES = ImmutableList.of(401, 404, 500);

  private static final Map<Integer, ErrorCode> HTTP_STATUS_TO_ERROR_CODE = ImmutableMap.of(
          401, ErrorCode.UNAUTHENTICATED,
          404, ErrorCode.NOT_FOUND,
          500, ErrorCode.INTERNAL);

  private static final String MOCK_TEMPLATE_RESPONSE = TestUtils
          .loadResource("getRemoteConfig.json");

  private static final String MOCK_LIST_VERSIONS_RESPONSE = TestUtils
          .loadResource("listRemoteConfigVersions.json");

  private static final String TEST_ETAG = "etag-123456789012-1";

  private static final Map<String, Parameter> EXPECTED_PARAMETERS = ImmutableMap.of(
          "welcome_message_text", new Parameter()
                  .setDefaultValue(ParameterValue.of("welcome to app"))
                  .setConditionalValues(ImmutableMap.<String, ParameterValue>of(
                          "ios_en", ParameterValue.of("welcome to app en")
                  ))
                  .setDescription("text for welcome message!"),
          "header_text", new Parameter()
                  .setDefaultValue(ParameterValue.inAppDefault())
  );

  private static final Map<String, ParameterGroup> EXPECTED_PARAMETER_GROUPS = ImmutableMap.of(
          "new menu", new ParameterGroup()
                  .setDescription("New Menu")
                  .setParameters(ImmutableMap.of(
                          "pumpkin_spice_season", new Parameter()
                                  .setDefaultValue(ParameterValue.of("true"))
                                  .setDescription("Whether it's currently pumpkin spice season.")
                          )
                  )
  );

  private static final List<Condition> EXPECTED_CONDITIONS = ImmutableList.of(
          new Condition("ios_en", "device.os == 'ios' && device.country in ['us', 'uk']")
                  .setTagColor(TagColor.INDIGO),
          new Condition("android_en",
                  "device.os == 'android' && device.country in ['us', 'uk']")
  );

  private static final Version EXPECTED_VERSION = new Version(new TemplateResponse.VersionResponse()
          .setVersionNumber("17")
          .setUpdateOrigin("ADMIN_SDK_NODE")
          .setUpdateType("INCREMENTAL_UPDATE")
          .setUpdateUser(new TemplateResponse.UserResponse()
                  .setEmail("firebase-user@account.com")
                  .setName("dev-admin")
                  .setImageUrl("http://image.jpg"))
          .setUpdateTime("2020-11-15T06:57:26.342763941Z")
          .setDescription("promo config")
  );

  private static final Template EXPECTED_TEMPLATE = new Template()
          .setETag(TEST_ETAG)
          .setParameters(EXPECTED_PARAMETERS)
          .setConditions(EXPECTED_CONDITIONS)
          .setParameterGroups(EXPECTED_PARAMETER_GROUPS)
          .setVersion(EXPECTED_VERSION);

  private MockLowLevelHttpResponse response;
  private TestResponseInterceptor interceptor;
  private FirebaseRemoteConfigClient client;

  @Before
  public void setUp() {
    response = new MockLowLevelHttpResponse();
    interceptor = new TestResponseInterceptor();
    client = initRemoteConfigClient(response, interceptor);
  }

  // Get template tests

  @Test
  public void testGetTemplate() throws Exception {
    response.addHeader("etag", TEST_ETAG);
    response.setContent(MOCK_TEMPLATE_RESPONSE);

    Template receivedTemplate = client.getTemplate();

    assertEquals(TEST_ETAG, receivedTemplate.getETag());
    assertEquals(EXPECTED_TEMPLATE, receivedTemplate);
    assertEquals(1605423446000L, receivedTemplate.getVersion().getUpdateTime());
    checkGetRequestHeader(interceptor.getLastRequest());
  }

  @Test
  public void testGetTemplateWithTimestampUpToNanosecondPrecision() throws Exception {
    List<String> timestamps = ImmutableList.of(
            "2020-11-15T06:57:26.342Z",
            "2020-11-15T06:57:26.342763Z",
            "2020-11-15T06:57:26.342763941Z"
    );
    for (String timestamp : timestamps) {
      response.addHeader("etag", TEST_ETAG);
      String templateResponse = "{\"version\": {"
              + "    \"versionNumber\": \"17\","
              + "    \"updateTime\": \"" + timestamp + "\""
              + "  }}";
      response.setContent(templateResponse);

      Template receivedTemplate = client.getTemplate();

      assertEquals(TEST_ETAG, receivedTemplate.getETag());
      assertEquals("17", receivedTemplate.getVersion().getVersionNumber());
      assertEquals(1605423446000L, receivedTemplate.getVersion().getUpdateTime());
      checkGetRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testGetTemplateWithEmptyTemplateResponse() throws Exception {
    response.addHeader("etag", TEST_ETAG);
    response.setContent("{}");

    Template template = client.getTemplate();

    assertEquals(TEST_ETAG, template.getETag());
    assertEquals(0, template.getParameters().size());
    assertEquals(0, template.getConditions().size());
    assertEquals(0, template.getParameterGroups().size());
    assertNull(template.getVersion());
    checkGetRequestHeader(interceptor.getLastRequest());
  }

  @Test(expected = IllegalStateException.class)
  public void testGetTemplateWithNoEtag() throws FirebaseRemoteConfigException {
    // ETag does not exist
    response.setContent(MOCK_TEMPLATE_RESPONSE);

    client.getTemplate();
  }

  @Test(expected = IllegalStateException.class)
  public void testGetTemplateWithEmptyEtag() throws FirebaseRemoteConfigException {
    // Empty ETag
    response.addHeader("etag", "");
    response.setContent(MOCK_TEMPLATE_RESPONSE);

    client.getTemplate();
  }

  @Test
  public void testGetTemplateHttpError() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent("{}");

      try {
        client.getTemplate();
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, HTTP_STATUS_TO_ERROR_CODE.get(code), null,
                "Unexpected HTTP response with status: " + code + "\n{}", HttpMethods.GET);
      }
      checkGetRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testGetTemplateTransportError() {
    client = initClientWithFaultyTransport();

    try {
      client.getTemplate();
      fail("No error thrown for HTTP error");
    } catch (FirebaseRemoteConfigException error) {
      assertEquals(ErrorCode.UNKNOWN, error.getErrorCode());
      assertEquals("Unknown error while making a remote service call: transport error",
              error.getMessage());
      assertTrue(error.getCause() instanceof IOException);
      assertNull(error.getHttpResponse());
      assertNull(error.getRemoteConfigErrorCode());
    }
  }

  @Test
  public void testGetTemplateSuccessResponseWithUnexpectedPayload() {
    response.setContent("not valid json");

    try {
      client.getTemplate();
      fail("No error thrown for malformed response");
    } catch (FirebaseRemoteConfigException error) {
      assertEquals(ErrorCode.UNKNOWN, error.getErrorCode());
      assertTrue(error.getMessage().startsWith("Error while parsing HTTP response: "));
      assertNotNull(error.getCause());
      assertNotNull(error.getHttpResponse());
      assertNull(error.getRemoteConfigErrorCode());
    }
    checkGetRequestHeader(interceptor.getLastRequest());
  }

  @Test
  public void testGetTemplateErrorWithZeroContentResponse() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setZeroContent();

      try {
        client.getTemplate();
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, HTTP_STATUS_TO_ERROR_CODE.get(code), null,
                "Unexpected HTTP response with status: " + code + "\nnull", HttpMethods.GET);
      }
      checkGetRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testGetTemplateErrorWithMalformedResponse() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent("not json");

      try {
        client.getTemplate();
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, HTTP_STATUS_TO_ERROR_CODE.get(code), null,
                "Unexpected HTTP response with status: " + code + "\nnot json", HttpMethods.GET);
      }
      checkGetRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testGetTemplateErrorWithDetails() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent(
              "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\"}}");

      try {
        client.getTemplate();
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT, null, "test error",
                HttpMethods.GET);
      }
      checkGetRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testGetTemplateErrorWithRcError() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent(
              "{\"error\": {\"status\": \"INVALID_ARGUMENT\", "
                      + "\"message\": \"[INVALID_ARGUMENT]: test error\"}}");

      try {
        client.getTemplate();
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT,
                RemoteConfigErrorCode.INVALID_ARGUMENT, "[INVALID_ARGUMENT]: test error",
                HttpMethods.GET);
      }
      checkGetRequestHeader(interceptor.getLastRequest());
    }
  }

  // Test getTemplateAtVersion

  @Test(expected = IllegalArgumentException.class)
  public void testGetTemplateAtVersionWithNullString() throws Exception {
    client.getTemplateAtVersion(null);
  }

  @Test
  public void testGetTemplateAtVersionWithInvalidString() throws Exception {
    List<String> invalidVersionStrings = ImmutableList
            .of("", " ", "abc", "t123", "123t", "t123t", "12t3", "#$*&^", "-123", "+123", "123.4");

    for (String version : invalidVersionStrings) {
      try {
        client.getTemplateAtVersion(version);
        fail("No error thrown for invalid version number");
      } catch (IllegalArgumentException expected) {
        String message = "Version number must be a non-empty string in int64 format.";
        assertEquals(message, expected.getMessage());
      }
    }
  }

  @Test
  public void testGetTemplateAtVersionWithValidString() throws Exception {
    response.addHeader("etag", TEST_ETAG);
    response.setContent(MOCK_TEMPLATE_RESPONSE);

    Template receivedTemplate = client.getTemplateAtVersion("24");

    assertEquals(TEST_ETAG, receivedTemplate.getETag());
    assertEquals(EXPECTED_TEMPLATE, receivedTemplate);
    assertEquals(1605423446000L, receivedTemplate.getVersion().getUpdateTime());
    checkGetRequestHeader(interceptor.getLastRequest(), "?versionNumber=24");
  }

  @Test
  public void testGetTemplateAtVersionWithEmptyTemplateResponse() throws Exception {
    response.addHeader("etag", TEST_ETAG);
    response.setContent("{}");

    Template template = client.getTemplateAtVersion("24");

    assertEquals(TEST_ETAG, template.getETag());
    assertEquals(0, template.getParameters().size());
    assertEquals(0, template.getConditions().size());
    assertEquals(0, template.getParameterGroups().size());
    assertNull(template.getVersion());
    checkGetRequestHeader(interceptor.getLastRequest(), "?versionNumber=24");
  }

  @Test(expected = IllegalStateException.class)
  public void testGetTemplateAtVersionWithNoEtag() throws FirebaseRemoteConfigException {
    // ETag does not exist
    response.setContent(MOCK_TEMPLATE_RESPONSE);

    client.getTemplateAtVersion("24");
  }

  @Test(expected = IllegalStateException.class)
  public void testGetTemplateAtVersionWithEmptyEtag() throws FirebaseRemoteConfigException {
    // Empty ETag
    response.addHeader("etag", "");
    response.setContent(MOCK_TEMPLATE_RESPONSE);

    client.getTemplateAtVersion("24");
  }

  @Test
  public void testGetTemplateAtVersionHttpError() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent("{}");

      try {
        client.getTemplateAtVersion("24");
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, HTTP_STATUS_TO_ERROR_CODE.get(code), null,
                "Unexpected HTTP response with status: " + code + "\n{}", HttpMethods.GET);
      }
      checkGetRequestHeader(interceptor.getLastRequest(), "?versionNumber=24");
    }
  }

  @Test
  public void testGetTemplateAtVersionTransportError() {
    client = initClientWithFaultyTransport();

    try {
      client.getTemplateAtVersion("24");
      fail("No error thrown for HTTP error");
    } catch (FirebaseRemoteConfigException error) {
      assertEquals(ErrorCode.UNKNOWN, error.getErrorCode());
      assertEquals("Unknown error while making a remote service call: transport error",
              error.getMessage());
      assertTrue(error.getCause() instanceof IOException);
      assertNull(error.getHttpResponse());
      assertNull(error.getRemoteConfigErrorCode());
    }
  }

  @Test
  public void testGetTemplateAtVersionSuccessResponseWithUnexpectedPayload() {
    response.setContent("not valid json");

    try {
      client.getTemplateAtVersion("24");
      fail("No error thrown for malformed response");
    } catch (FirebaseRemoteConfigException error) {
      assertEquals(ErrorCode.UNKNOWN, error.getErrorCode());
      assertTrue(error.getMessage().startsWith("Error while parsing HTTP response: "));
      assertNotNull(error.getCause());
      assertNotNull(error.getHttpResponse());
      assertNull(error.getRemoteConfigErrorCode());
    }
    checkGetRequestHeader(interceptor.getLastRequest(), "?versionNumber=24");
  }

  @Test
  public void testGetTemplateAtVersionErrorWithZeroContentResponse() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setZeroContent();

      try {
        client.getTemplateAtVersion("24");
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, HTTP_STATUS_TO_ERROR_CODE.get(code), null,
                "Unexpected HTTP response with status: " + code + "\nnull", HttpMethods.GET);
      }
      checkGetRequestHeader(interceptor.getLastRequest(), "?versionNumber=24");
    }
  }

  @Test
  public void testGetTemplateAtVersionErrorWithMalformedResponse() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent("not json");

      try {
        client.getTemplateAtVersion("24");
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, HTTP_STATUS_TO_ERROR_CODE.get(code), null,
                "Unexpected HTTP response with status: " + code + "\nnot json", HttpMethods.GET);
      }
      checkGetRequestHeader(interceptor.getLastRequest(), "?versionNumber=24");
    }
  }

  @Test
  public void testGetTemplateAtVersionErrorWithDetails() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent(
              "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\"}}");

      try {
        client.getTemplateAtVersion("24");
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT, null, "test error",
                HttpMethods.GET);
      }
      checkGetRequestHeader(interceptor.getLastRequest(), "?versionNumber=24");
    }
  }

  @Test
  public void testGetTemplateAtVersionErrorWithRcError() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent(
              "{\"error\": {\"status\": \"INVALID_ARGUMENT\", "
                      + "\"message\": \"[INVALID_ARGUMENT]: test error\"}}");

      try {
        client.getTemplateAtVersion("24");
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT,
                RemoteConfigErrorCode.INVALID_ARGUMENT, "[INVALID_ARGUMENT]: test error",
                HttpMethods.GET);
      }
      checkGetRequestHeader(interceptor.getLastRequest(), "?versionNumber=24");
    }
  }

  // Test publishTemplate

  @Test(expected = IllegalArgumentException.class)
  public void testPublishTemplateWithNullTemplate() throws Exception {
    client.publishTemplate(null, false, false);
  }

  @Test
  public void testPublishTemplateWithValidTemplate() throws Exception {
    response.addHeader("etag", TEST_ETAG);
    response.setContent(MOCK_TEMPLATE_RESPONSE);

    Template publishedTemplate = client.publishTemplate(EXPECTED_TEMPLATE, false, false);

    assertEquals(TEST_ETAG, publishedTemplate.getETag());
    assertEquals(EXPECTED_TEMPLATE, publishedTemplate);
    assertEquals(1605423446000L, publishedTemplate.getVersion().getUpdateTime());
    checkPutRequestHeader(interceptor.getLastRequest());
  }

  @Test
  public void testPublishTemplateWithValidTemplateAndForceTrue() throws Exception {
    response.addHeader("etag", TEST_ETAG);
    response.setContent(MOCK_TEMPLATE_RESPONSE);

    Template publishedTemplate = client.publishTemplate(EXPECTED_TEMPLATE, false, true);

    assertEquals(TEST_ETAG, publishedTemplate.getETag());
    assertEquals(EXPECTED_TEMPLATE, publishedTemplate);
    assertEquals(1605423446000L, publishedTemplate.getVersion().getUpdateTime());
    checkPutRequestHeader(interceptor.getLastRequest(), "", "*");
  }

  @Test
  public void testPublishTemplateWithValidTemplateAndValidateOnlyTrue() throws Exception {
    response.addHeader("etag", TEST_ETAG);
    response.setContent(MOCK_TEMPLATE_RESPONSE);
    Template expectedTemplate = new Template()
            .setETag("etag-123456789012-45")
            .setParameters(EXPECTED_PARAMETERS)
            .setConditions(EXPECTED_CONDITIONS)
            .setParameterGroups(EXPECTED_PARAMETER_GROUPS)
            .setVersion(EXPECTED_VERSION);

    Template validatedTemplate = client.publishTemplate(expectedTemplate, true, false);

    // check if the etag matches the input template's etag and not the etag from the server response
    assertNotEquals(TEST_ETAG, validatedTemplate.getETag());
    assertEquals("etag-123456789012-45", validatedTemplate.getETag());
    assertEquals(expectedTemplate, validatedTemplate);
    checkPutRequestHeader(interceptor.getLastRequest(), "?validateOnly=true",
            "etag-123456789012-45");
  }

  @Test
  public void testPublishTemplateWithEmptyTemplateResponse() throws Exception {
    response.addHeader("etag", TEST_ETAG);
    response.setContent("{}");

    Template template = client.publishTemplate(new Template().setETag(TEST_ETAG), false, false);

    assertEquals(TEST_ETAG, template.getETag());
    assertEquals(0, template.getParameters().size());
    assertEquals(0, template.getConditions().size());
    assertEquals(0, template.getParameterGroups().size());
    assertNull(template.getVersion());
    checkPutRequestHeader(interceptor.getLastRequest());
  }

  @Test(expected = IllegalStateException.class)
  public void testPublishTemplateWithNoEtag() throws FirebaseRemoteConfigException {
    // ETag does not exist
    response.setContent(MOCK_TEMPLATE_RESPONSE);

    client.publishTemplate(new Template(), false, false);
  }

  @Test(expected = IllegalStateException.class)
  public void testPublishTemplateWithEmptyEtag() throws FirebaseRemoteConfigException {
    // Empty ETag
    response.addHeader("etag", "");
    response.setContent(MOCK_TEMPLATE_RESPONSE);

    client.publishTemplate(new Template(), false, false);
  }

  @Test
  public void testPublishTemplateHttpError() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent("{}");

      try {
        client.publishTemplate(new Template().setETag(TEST_ETAG), false, false);
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, HTTP_STATUS_TO_ERROR_CODE.get(code), null,
                "Unexpected HTTP response with status: " + code + "\n{}", HttpMethods.PUT);
      }
      checkPutRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testPublishTemplateTransportError() {
    client = initClientWithFaultyTransport();

    try {
      client.publishTemplate(new Template(), false, false);
      fail("No error thrown for HTTP error");
    } catch (FirebaseRemoteConfigException error) {
      assertEquals(ErrorCode.UNKNOWN, error.getErrorCode());
      assertEquals("Unknown error while making a remote service call: transport error",
              error.getMessage());
      assertTrue(error.getCause() instanceof IOException);
      assertNull(error.getHttpResponse());
      assertNull(error.getRemoteConfigErrorCode());
    }
  }

  @Test
  public void testPublishTemplateSuccessResponseWithUnexpectedPayload() {
    response.setContent("not valid json");

    try {
      client.publishTemplate(new Template().setETag(TEST_ETAG), false, false);
      fail("No error thrown for malformed response");
    } catch (FirebaseRemoteConfigException error) {
      assertEquals(ErrorCode.UNKNOWN, error.getErrorCode());
      assertTrue(error.getMessage().startsWith("Error while parsing HTTP response: "));
      assertNotNull(error.getCause());
      assertNotNull(error.getHttpResponse());
      assertNull(error.getRemoteConfigErrorCode());
    }
    checkPutRequestHeader(interceptor.getLastRequest());
  }

  @Test
  public void testPublishTemplateErrorWithZeroContentResponse() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setZeroContent();

      try {
        client.publishTemplate(new Template().setETag(TEST_ETAG), false, false);
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, HTTP_STATUS_TO_ERROR_CODE.get(code), null,
                "Unexpected HTTP response with status: " + code + "\nnull", HttpMethods.PUT);
      }
      checkPutRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testPublishTemplateErrorWithMalformedResponse() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent("not json");

      try {
        client.publishTemplate(new Template().setETag(TEST_ETAG), false, false);
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, HTTP_STATUS_TO_ERROR_CODE.get(code), null,
                "Unexpected HTTP response with status: " + code + "\nnot json", HttpMethods.PUT);
      }
      checkPutRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testPublishTemplateErrorWithDetails() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent(
              "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\"}}");

      try {
        client.publishTemplate(new Template().setETag(TEST_ETAG), false, false);
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT, null, "test error",
                HttpMethods.PUT);
      }
      checkPutRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testPublishTemplateErrorWithRcError() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent(
              "{\"error\": {\"status\": \"INVALID_ARGUMENT\", "
                      + "\"message\": \"[INVALID_ARGUMENT]: test error\"}}");

      try {
        client.publishTemplate(new Template().setETag(TEST_ETAG), false, false);
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT,
                RemoteConfigErrorCode.INVALID_ARGUMENT, "[INVALID_ARGUMENT]: test error",
                HttpMethods.PUT);
      }
      checkPutRequestHeader(interceptor.getLastRequest());
    }
  }

  // Test rollback

  @Test(expected = IllegalArgumentException.class)
  public void testRollbackWithNullString() throws Exception {
    client.rollback(null);
  }

  @Test
  public void testRollbackWithInvalidString() throws Exception {
    List<String> invalidVersionStrings = ImmutableList
            .of("", " ", "abc", "t123", "123t", "t123t", "12t3", "#$*&^", "-123", "+123", "123.4");

    for (String version : invalidVersionStrings) {
      try {
        client.rollback(version);
        fail("No error thrown for invalid version number");
      } catch (IllegalArgumentException expected) {
        String message = "Version number must be a non-empty string in int64 format.";
        assertEquals(message, expected.getMessage());
      }
    }
  }

  @Test
  public void testRollbackWithValidString() throws Exception {
    response.addHeader("etag", TEST_ETAG);
    response.setContent(MOCK_TEMPLATE_RESPONSE);

    Template rolledBackTemplate = client.rollback("24");

    assertEquals(TEST_ETAG, rolledBackTemplate.getETag());
    assertEquals(EXPECTED_TEMPLATE, rolledBackTemplate);
    assertEquals(1605423446000L, rolledBackTemplate.getVersion().getUpdateTime());
    checkPostRequestHeader(interceptor.getLastRequest(), ":rollback");
    checkRequestContent(interceptor.getLastRequest(),
            ImmutableMap.<String, Object>of("versionNumber", "24"));
  }

  @Test
  public void testRollbackWithEmptyTemplateResponse() throws Exception {
    response.addHeader("etag", TEST_ETAG);
    response.setContent("{}");

    Template template = client.rollback("24");

    assertEquals(TEST_ETAG, template.getETag());
    assertEquals(0, template.getParameters().size());
    assertEquals(0, template.getConditions().size());
    assertEquals(0, template.getParameterGroups().size());
    assertNull(template.getVersion());
    checkPostRequestHeader(interceptor.getLastRequest(), ":rollback");
    checkRequestContent(interceptor.getLastRequest(),
            ImmutableMap.<String, Object>of("versionNumber", "24"));
  }

  @Test(expected = IllegalStateException.class)
  public void testRollbackWithNoEtag() throws FirebaseRemoteConfigException {
    // ETag does not exist
    response.setContent(MOCK_TEMPLATE_RESPONSE);

    client.rollback("24");
  }

  @Test(expected = IllegalStateException.class)
  public void testRollbackWithEmptyEtag() throws FirebaseRemoteConfigException {
    // Empty ETag
    response.addHeader("etag", "");
    response.setContent(MOCK_TEMPLATE_RESPONSE);

    client.rollback("24");
  }

  @Test
  public void testRollbackHttpError() throws IOException {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent("{}");

      try {
        client.rollback("24");
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, HTTP_STATUS_TO_ERROR_CODE.get(code), null,
                "Unexpected HTTP response with status: " + code + "\n{}", HttpMethods.POST);
      }
      checkPostRequestHeader(interceptor.getLastRequest(), ":rollback");
      checkRequestContent(interceptor.getLastRequest(),
              ImmutableMap.<String, Object>of("versionNumber", "24"));
    }
  }

  @Test
  public void testRollbackTransportError() {
    client = initClientWithFaultyTransport();

    try {
      client.rollback("24");
      fail("No error thrown for HTTP error");
    } catch (FirebaseRemoteConfigException error) {
      assertEquals(ErrorCode.UNKNOWN, error.getErrorCode());
      assertEquals("Unknown error while making a remote service call: transport error",
              error.getMessage());
      assertTrue(error.getCause() instanceof IOException);
      assertNull(error.getHttpResponse());
      assertNull(error.getRemoteConfigErrorCode());
    }
  }

  @Test
  public void testRollbackSuccessResponseWithUnexpectedPayload() throws IOException {
    response.setContent("not valid json");

    try {
      client.rollback("24");
      fail("No error thrown for malformed response");
    } catch (FirebaseRemoteConfigException error) {
      assertEquals(ErrorCode.UNKNOWN, error.getErrorCode());
      assertTrue(error.getMessage().startsWith("Error while parsing HTTP response: "));
      assertNotNull(error.getCause());
      assertNotNull(error.getHttpResponse());
      assertNull(error.getRemoteConfigErrorCode());
    }
    checkPostRequestHeader(interceptor.getLastRequest(), ":rollback");
    checkRequestContent(interceptor.getLastRequest(),
            ImmutableMap.<String, Object>of("versionNumber", "24"));
  }

  @Test
  public void testRollbackErrorWithZeroContentResponse() throws IOException {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setZeroContent();

      try {
        client.rollback("24");
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, HTTP_STATUS_TO_ERROR_CODE.get(code), null,
                "Unexpected HTTP response with status: " + code + "\nnull", HttpMethods.POST);
      }
      checkPostRequestHeader(interceptor.getLastRequest(), ":rollback");
      checkRequestContent(interceptor.getLastRequest(),
              ImmutableMap.<String, Object>of("versionNumber", "24"));
    }
  }

  @Test
  public void testRollbackErrorWithMalformedResponse() throws IOException {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent("not json");

      try {
        client.rollback("24");
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, HTTP_STATUS_TO_ERROR_CODE.get(code), null,
                "Unexpected HTTP response with status: " + code + "\nnot json", HttpMethods.POST);
      }
      checkPostRequestHeader(interceptor.getLastRequest(), ":rollback");
      checkRequestContent(interceptor.getLastRequest(),
              ImmutableMap.<String, Object>of("versionNumber", "24"));
    }
  }

  @Test
  public void testRollbackErrorWithDetails() throws IOException {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent(
              "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\"}}");

      try {
        client.rollback("24");
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT, null, "test error",
                HttpMethods.POST);
      }
      checkPostRequestHeader(interceptor.getLastRequest(), ":rollback");
      checkRequestContent(interceptor.getLastRequest(),
              ImmutableMap.<String, Object>of("versionNumber", "24"));
    }
  }

  @Test
  public void testRollbackErrorWithRcError() throws IOException {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent(
              "{\"error\": {\"status\": \"INVALID_ARGUMENT\", "
                      + "\"message\": \"[INVALID_ARGUMENT]: test error\"}}");

      try {
        client.rollback("24");
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT,
                RemoteConfigErrorCode.INVALID_ARGUMENT, "[INVALID_ARGUMENT]: test error",
                HttpMethods.POST);
      }
      checkPostRequestHeader(interceptor.getLastRequest(), ":rollback");
      checkRequestContent(interceptor.getLastRequest(),
              ImmutableMap.<String, Object>of("versionNumber", "24"));
    }
  }

  // Test listVersions

  @Test
  public void testListVersionsWithNullOptions() throws Exception {
    response.setContent(MOCK_LIST_VERSIONS_RESPONSE);

    TemplateResponse.ListVersionsResponse versionsList = client.listVersions(null);

    assertTrue(versionsList.hasVersions());
    assertEquals("28", versionsList.getNextPageToken());
    assertEquals(4, versionsList.getVersions().size());
    checkGetRequestHeader(interceptor.getLastRequest(), ":listVersions");
  }

  @Test
  public void testListVersionsWithOptions() throws Exception {
    response.setContent(MOCK_LIST_VERSIONS_RESPONSE);

    TemplateResponse.ListVersionsResponse versionsList = client.listVersions(
            ListVersionsOptions.builder()
                    .setPageSize(10)
                    .setPageToken("token")
                    .setStartTimeMillis(1605219122000L)
                    .setEndTimeMillis(1606245035000L)
                    .setEndVersionNumber("29").build());

    assertTrue(versionsList.hasVersions());

    HttpRequest request = interceptor.getLastRequest();
    String urlWithoutParameters = request.getUrl().toString()
            .substring(0, request.getUrl().toString().lastIndexOf('?'));
    final Map<String, String> expectedQuery = ImmutableMap.of(
            "endVersionNumber", "29",
            "pageSize", "10",
            "pageToken", "token",
            "startTime", "2020-11-12T22:12:02.000000000Z",
            "endTime", "2020-11-24T19:10:35.000000000Z"
    );
    Map<String, String> actualQuery = new HashMap<>();
    String query = request.getUrl().toURI().getQuery();
    String[] pairs = query.split("&");
    for (String pair : pairs) {
      int idx = pair.indexOf("=");
      actualQuery.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
              URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
    }

    assertEquals("GET", request.getRequestMethod());
    assertEquals(TEST_REMOTE_CONFIG_URL + ":listVersions", urlWithoutParameters);
    HttpHeaders headers = request.getHeaders();
    assertEquals("fire-admin-java/" + SdkUtils.getVersion(), headers.get("X-Firebase-Client"));
    assertEquals("gzip", headers.getAcceptEncoding());
    assertEquals(expectedQuery, actualQuery);
  }

  @Test
  public void testListVersionsWithEmptyResponse() throws Exception {
    response.setContent("{}");

    TemplateResponse.ListVersionsResponse versionsList = client.listVersions(null);

    assertFalse(versionsList.hasVersions());
    assertNull(versionsList.getNextPageToken());
    assertNull(versionsList.getVersions());
    checkGetRequestHeader(interceptor.getLastRequest(), ":listVersions");
  }

  @Test
  public void testListVersionsHttpError() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent("{}");

      try {
        client.listVersions(null);
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, HTTP_STATUS_TO_ERROR_CODE.get(code), null,
                "Unexpected HTTP response with status: " + code + "\n{}", HttpMethods.GET);
      }
      checkGetRequestHeader(interceptor.getLastRequest(), ":listVersions");
    }
  }

  @Test
  public void testListVersionsTransportError() {
    client = initClientWithFaultyTransport();

    try {
      client.listVersions(null);
      fail("No error thrown for HTTP error");
    } catch (FirebaseRemoteConfigException error) {
      assertEquals(ErrorCode.UNKNOWN, error.getErrorCode());
      assertEquals("Unknown error while making a remote service call: transport error",
              error.getMessage());
      assertTrue(error.getCause() instanceof IOException);
      assertNull(error.getHttpResponse());
      assertNull(error.getRemoteConfigErrorCode());
    }
  }

  @Test
  public void testListVersionsSuccessResponseWithUnexpectedPayload() {
    response.setContent("not valid json");

    try {
      client.listVersions(null);
      fail("No error thrown for malformed response");
    } catch (FirebaseRemoteConfigException error) {
      assertEquals(ErrorCode.UNKNOWN, error.getErrorCode());
      assertTrue(error.getMessage().startsWith("Error while parsing HTTP response: "));
      assertNotNull(error.getCause());
      assertNotNull(error.getHttpResponse());
      assertNull(error.getRemoteConfigErrorCode());
    }
    checkGetRequestHeader(interceptor.getLastRequest(), ":listVersions");
  }

  @Test
  public void testListVersionsErrorWithZeroContentResponse() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setZeroContent();

      try {
        client.listVersions(null);
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, HTTP_STATUS_TO_ERROR_CODE.get(code), null,
                "Unexpected HTTP response with status: " + code + "\nnull", HttpMethods.GET);
      }
      checkGetRequestHeader(interceptor.getLastRequest(), ":listVersions");
    }
  }

  @Test
  public void testListVersionsErrorWithMalformedResponse() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent("not json");

      try {
        client.listVersions(null);
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, HTTP_STATUS_TO_ERROR_CODE.get(code), null,
                "Unexpected HTTP response with status: " + code + "\nnot json", HttpMethods.GET);
      }
      checkGetRequestHeader(interceptor.getLastRequest(), ":listVersions");
    }
  }

  @Test
  public void testListVersionsErrorWithDetails() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent(
              "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\"}}");

      try {
        client.listVersions(null);
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT, null, "test error",
                HttpMethods.GET);
      }
      checkGetRequestHeader(interceptor.getLastRequest(), ":listVersions");
    }
  }

  @Test
  public void testListVersionsErrorWithRcError() {
    for (int code : HTTP_STATUS_CODES) {
      response.setStatusCode(code).setContent(
              "{\"error\": {\"status\": \"INVALID_ARGUMENT\", "
                      + "\"message\": \"[INVALID_ARGUMENT]: test error\"}}");

      try {
        client.listVersions(null);
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT,
                RemoteConfigErrorCode.INVALID_ARGUMENT, "[INVALID_ARGUMENT]: test error",
                HttpMethods.GET);
      }
      checkGetRequestHeader(interceptor.getLastRequest(), ":listVersions");
    }
  }

  // App related tests

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderNullProjectId() {
    fullyPopulatedBuilder().setProjectId(null).build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderEmptyProjectId() {
    fullyPopulatedBuilder().setProjectId("").build();
  }

  @Test(expected = NullPointerException.class)
  public void testBuilderNullRequestFactory() {
    fullyPopulatedBuilder().setRequestFactory(null).build();
  }

  @Test
  public void testFromApp() throws IOException {
    FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(new MockGoogleCredentials("test-token"))
            .setProjectId("test-project")
            .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    try {
      FirebaseRemoteConfigClientImpl client = FirebaseRemoteConfigClientImpl.fromApp(app);

      assertEquals(TEST_REMOTE_CONFIG_URL, client.getRemoteConfigUrl());
      assertSame(options.getJsonFactory(), client.getJsonFactory());

      HttpRequest request = client.getRequestFactory().buildGetRequest(
              new GenericUrl("https://example.com"));
      assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
    } finally {
      app.delete();
    }
  }

  private FirebaseRemoteConfigClientImpl initRemoteConfigClient(
          MockLowLevelHttpResponse mockResponse, HttpResponseInterceptor interceptor) {
    MockHttpTransport transport = new MockHttpTransport.Builder()
            .setLowLevelHttpResponse(mockResponse)
            .build();

    return FirebaseRemoteConfigClientImpl.builder()
            .setProjectId("test-project")
            .setJsonFactory(Utils.getDefaultJsonFactory())
            .setRequestFactory(transport.createRequestFactory())
            .setResponseInterceptor(interceptor)
            .build();
  }

  private FirebaseRemoteConfigClientImpl initClientWithFaultyTransport() {
    HttpTransport transport = TestUtils.createFaultyHttpTransport();
    return FirebaseRemoteConfigClientImpl.builder()
            .setProjectId("test-project")
            .setJsonFactory(Utils.getDefaultJsonFactory())
            .setRequestFactory(transport.createRequestFactory())
            .build();
  }

  private FirebaseRemoteConfigClientImpl.Builder fullyPopulatedBuilder() {
    return FirebaseRemoteConfigClientImpl.builder()
            .setProjectId("test-project")
            .setJsonFactory(Utils.getDefaultJsonFactory())
            .setRequestFactory(Utils.getDefaultTransport().createRequestFactory());
  }

  private void checkGetRequestHeader(HttpRequest request) {
    checkGetRequestHeader(request, "");
  }

  private void checkGetRequestHeader(HttpRequest request, String urlSuffix) {
    assertEquals("GET", request.getRequestMethod());
    assertEquals(TEST_REMOTE_CONFIG_URL + urlSuffix, request.getUrl().toString());
    HttpHeaders headers = request.getHeaders();
    assertEquals("fire-admin-java/" + SdkUtils.getVersion(), headers.get("X-Firebase-Client"));
    assertEquals("gzip", headers.getAcceptEncoding());
  }

  private void checkPutRequestHeader(HttpRequest request) {
    checkPutRequestHeader(request, "", TEST_ETAG);
  }

  private void checkPutRequestHeader(HttpRequest request, String urlSuffix, String ifMatch) {
    assertEquals("PUT", request.getRequestMethod());
    assertEquals(TEST_REMOTE_CONFIG_URL + urlSuffix, request.getUrl().toString());
    HttpHeaders headers = request.getHeaders();
    assertEquals("fire-admin-java/" + SdkUtils.getVersion(), headers.get("X-Firebase-Client"));
    assertEquals("gzip", headers.getAcceptEncoding());
    assertEquals(ifMatch, headers.getIfMatch());
  }

  private void checkPostRequestHeader(HttpRequest request, String urlSuffix) {
    assertEquals("POST", request.getRequestMethod());
    assertEquals(TEST_REMOTE_CONFIG_URL + urlSuffix, request.getUrl().toString());
    HttpHeaders headers = request.getHeaders();
    assertEquals("fire-admin-java/" + SdkUtils.getVersion(), headers.get("X-Firebase-Client"));
    assertEquals("gzip", headers.getAcceptEncoding());
  }

  private void checkRequestContent(
          HttpRequest request, Map<String, Object> expected) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    request.getContent().writeTo(out);
    JsonParser parser = Utils.getDefaultJsonFactory().createJsonParser(out.toString());
    Map<String, Object> parsed = new HashMap<>();
    parser.parseAndClose(parsed);
    assertEquals(expected, parsed);
  }

  private void checkExceptionFromHttpResponse(
          FirebaseRemoteConfigException error,
          ErrorCode expectedCode,
          RemoteConfigErrorCode expectedRemoteConfigCode,
          String expectedMessage,
          String httpMethod) {
    assertEquals(expectedCode, error.getErrorCode());
    assertEquals(expectedMessage, error.getMessage());
    assertTrue(error.getCause() instanceof HttpResponseException);
    assertEquals(expectedRemoteConfigCode, error.getRemoteConfigErrorCode());

    assertNotNull(error.getHttpResponse());
    OutgoingHttpRequest request = error.getHttpResponse().getRequest();
    assertEquals(httpMethod, request.getMethod());
    assertTrue(request.getUrl().startsWith("https://firebaseremoteconfig.googleapis.com"));
  }
}
