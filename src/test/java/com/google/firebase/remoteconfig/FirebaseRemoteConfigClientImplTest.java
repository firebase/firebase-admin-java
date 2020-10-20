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
import com.google.firebase.testing.TestResponseInterceptor;
import com.google.firebase.testing.TestUtils;

import java.io.IOException;
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

  private static final String TEST_ETAG = "etag-123456789012-1";

  private MockLowLevelHttpResponse response;
  private TestResponseInterceptor interceptor;
  private FirebaseRemoteConfigClient client;

  @Before
  public void setUp() {
    response = new MockLowLevelHttpResponse();
    interceptor = new TestResponseInterceptor();
    client = initRemoteConfigClient(response, interceptor);
  }

  @Test
  public void testGetTemplate() throws Exception {
    response.addHeader("etag", TEST_ETAG);
    response.setContent(MOCK_TEMPLATE_RESPONSE);

    Template template = client.getTemplate();

    // Check Parameters
    assertEquals(TEST_ETAG, template.getETag());
    Map<String, Parameter> parameters = template.getParameters();
    assertEquals(2, parameters.size());
    assertTrue(parameters.containsKey("welcome_message_text"));
    Parameter welcomeMessageParameter = parameters.get("welcome_message_text");
    assertEquals("text for welcome message!", welcomeMessageParameter.getDescription());
    ParameterValue.Explicit explicitDefaultValue =
            (ParameterValue.Explicit) welcomeMessageParameter.getDefaultValue();
    assertEquals("welcome to app", explicitDefaultValue.getValue());
    Map<String, ParameterValue> conditionalValues = welcomeMessageParameter
            .getConditionalValues();
    assertEquals(1, conditionalValues.size());
    assertTrue(conditionalValues.containsKey("ios_en"));
    ParameterValue.Explicit value =
            (ParameterValue.Explicit) conditionalValues.get("ios_en");
    assertEquals("welcome to app en", value.getValue());
    assertTrue(parameters.containsKey("header_text"));
    Parameter headerParameter = parameters.get("header_text");
    assertTrue(
            headerParameter.getDefaultValue() instanceof ParameterValue.InAppDefault);
    checkGetRequestHeader(interceptor.getLastRequest());

    // Check Conditions
    List<Condition> actualConditions = template.getConditions();
    List<Condition> expectedConditions = ImmutableList.of(
            new Condition("ios_en", "device.os == 'ios' && device.country in ['us', 'uk']")
                    .setTagColor(TagColor.INDIGO),
            new Condition("android_en",
                    "device.os == 'android' && device.country in ['us', 'uk']")
                    .setTagColor(TagColor.UNSPECIFIED)
    );
    assertEquals(expectedConditions.size(), actualConditions.size());
    for (int i = 0; i < expectedConditions.size(); i++) {
      assertEquals(expectedConditions.get(i), actualConditions.get(i));
    }
  }

  @Test
  public void testGetTemplateWithEmptyTemplateResponse() throws Exception {
    response.addHeader("etag", TEST_ETAG);
    response.setContent("{}");

    Template template = client.getTemplate();

    assertEquals(TEST_ETAG, template.getETag());
    assertEquals(0, template.getParameters().size());
    checkGetRequestHeader(interceptor.getLastRequest());
  }

  @Test(expected = IllegalStateException.class)
  public void testGetTemplateWithInvalidEtags() throws FirebaseRemoteConfigException {
    // ETag does not exist
    response.setContent(MOCK_TEMPLATE_RESPONSE);

    client.getTemplate();

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
                "Unexpected HTTP response with status: " + code + "\n{}");
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
                "Unexpected HTTP response with status: " + code + "\nnull");
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
                "Unexpected HTTP response with status: " + code + "\nnot json");
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
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT, null, "test error");
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
                RemoteConfigErrorCode.INVALID_ARGUMENT, "[INVALID_ARGUMENT]: test error");
      }
      checkGetRequestHeader(interceptor.getLastRequest());
    }
  }

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
    assertEquals("GET", request.getRequestMethod());
    assertEquals(TEST_REMOTE_CONFIG_URL, request.getUrl().toString());
    HttpHeaders headers = request.getHeaders();
    assertEquals("fire-admin-java/" + SdkUtils.getVersion(), headers.get("X-Firebase-Client"));
    assertEquals("gzip", headers.getAcceptEncoding());
  }

  private void checkExceptionFromHttpResponse(
          FirebaseRemoteConfigException error,
          ErrorCode expectedCode,
          RemoteConfigErrorCode expectedRemoteConfigCode,
          String expectedMessage) {
    assertEquals(expectedCode, error.getErrorCode());
    assertEquals(expectedMessage, error.getMessage());
    assertTrue(error.getCause() instanceof HttpResponseException);
    assertEquals(expectedRemoteConfigCode, error.getRemoteConfigErrorCode());

    assertNotNull(error.getHttpResponse());
    OutgoingHttpRequest request = error.getHttpResponse().getRequest();
    assertEquals(HttpMethods.GET, request.getMethod());
    assertTrue(request.getUrl().startsWith("https://firebaseremoteconfig.googleapis.com"));
  }
}
