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

  private static final String TEST_RC_URL =
          "https://firebaseremoteconfig.googleapis.com/v1/projects/test-project/remoteConfig";

  private static final List<Integer> HTTP_ERRORS = ImmutableList.of(401, 404, 500);

  private static final Map<Integer, ErrorCode> HTTP_2_ERROR = ImmutableMap.of(
          401, ErrorCode.UNAUTHENTICATED,
          404, ErrorCode.NOT_FOUND,
          500, ErrorCode.INTERNAL);

  private static final String MOCK_TEMPLATE_RESPONSE = "{\"conditions\": [], \"parameters\": {}}";

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
    RemoteConfigTemplate template = client.getTemplate();

    assertEquals(TEST_ETAG, template.getETag());
    checkGetRequestHeader(interceptor.getLastRequest());
  }

  @Test
  public void testGetTemplateWithInvalidEtags() {
    // Empty ETag
    response.addHeader("etag", "");
    response.setContent(MOCK_TEMPLATE_RESPONSE);
    try {
      client.getTemplate();
      fail("No error thrown for invalid ETag");
    } catch (FirebaseRemoteConfigException error) {
      assertEquals(ErrorCode.INTERNAL, error.getErrorCode());
      assertEquals("ETag header is not available in the server response.", error.getMessage());
      assertEquals(RemoteConfigErrorCode.INTERNAL, error.getRemoteConfigErrorCode());
    }
    checkGetRequestHeader(interceptor.getLastRequest());

    // ETag does not exist
    response.setContent(MOCK_TEMPLATE_RESPONSE);
    try {
      client.getTemplate();
      fail("No error thrown for invalid ETag");
    } catch (FirebaseRemoteConfigException error) {
      assertEquals(ErrorCode.INTERNAL, error.getErrorCode());
      assertEquals("ETag header is not available in the server response.", error.getMessage());
      assertEquals(RemoteConfigErrorCode.INTERNAL, error.getRemoteConfigErrorCode());
    }
    checkGetRequestHeader(interceptor.getLastRequest());
  }

  @Test
  public void testGetTemplateHttpError() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent("{}");

      try {
        client.getTemplate();
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, HTTP_2_ERROR.get(code), null,
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
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setZeroContent();

      try {
        client.getTemplate();
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, HTTP_2_ERROR.get(code), null,
                "Unexpected HTTP response with status: " + code + "\nnull");
      }
      checkGetRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testGetTemplateErrorWithMalformedResponse() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent("not json");

      try {
        client.getTemplate();
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, HTTP_2_ERROR.get(code), null,
                "Unexpected HTTP response with status: " + code + "\nnot json");
      }
      checkGetRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testGetTemplateErrorWithDetails() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
              "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\"}}");

      try {
        client.getTemplate();
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT);
      }
      checkGetRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testGetTemplateErrorWithCanonicalCode() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
              "{\"error\": {\"status\": \"NOT_FOUND\", \"message\": \"test error\"}}");

      try {
        client.getTemplate();
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.NOT_FOUND);
      }
      checkGetRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testGetTemplateErrorWithRcError() {
    for (int code : HTTP_ERRORS) {
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

  @Test
  public void testGetTemplateErrorWithDetailsAndNoCode() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
              "{\"error\": {\"status\": \"INVALID_ARGUMENT\", "
                      + "\"message\": \"test error\"}}");

      try {
        client.getTemplate();
        fail("No error thrown for HTTP error");
      } catch (FirebaseRemoteConfigException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT);
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

  @Test(expected = NullPointerException.class)
  public void testBuilderNullChildRequestFactory() {
    fullyPopulatedBuilder().setChildRequestFactory(null).build();
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

      assertEquals(TEST_RC_URL, client.getRcSendUrl());
      assertSame(options.getJsonFactory(), client.getJsonFactory());

      HttpRequest request = client.getRequestFactory().buildGetRequest(
              new GenericUrl("https://example.com"));
      assertEquals("Bearer test-token", request.getHeaders().getAuthorization());

      request = client.getChildRequestFactory().buildGetRequest(
              new GenericUrl("https://example.com"));
      assertNull(request.getHeaders().getAuthorization());
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
            .setChildRequestFactory(Utils.getDefaultTransport().createRequestFactory())
            .setResponseInterceptor(interceptor)
            .build();
  }

  private FirebaseRemoteConfigClientImpl initClientWithFaultyTransport() {
    HttpTransport transport = TestUtils.createFaultyHttpTransport();
    return FirebaseRemoteConfigClientImpl.builder()
            .setProjectId("test-project")
            .setJsonFactory(Utils.getDefaultJsonFactory())
            .setRequestFactory(transport.createRequestFactory())
            .setChildRequestFactory(Utils.getDefaultTransport().createRequestFactory())
            .build();
  }

  private FirebaseRemoteConfigClientImpl.Builder fullyPopulatedBuilder() {
    return FirebaseRemoteConfigClientImpl.builder()
            .setProjectId("test-project")
            .setJsonFactory(Utils.getDefaultJsonFactory())
            .setRequestFactory(Utils.getDefaultTransport().createRequestFactory())
            .setChildRequestFactory(Utils.getDefaultTransport().createRequestFactory());
  }

  private void checkGetRequestHeader(HttpRequest request) {
    assertEquals("GET", request.getRequestMethod());
    assertEquals(TEST_RC_URL, request.getUrl().toString());
    HttpHeaders headers = request.getHeaders();
    assertEquals("2", headers.get("X-GOOG-API-FORMAT-VERSION"));
    assertEquals("fire-admin-java/" + SdkUtils.getVersion(), headers.get("X-Firebase-Client"));
    assertEquals("gzip", headers.getFirstHeaderStringValue("Accept-Encoding"));
  }

  private void checkExceptionFromHttpResponse(
          FirebaseRemoteConfigException error,
          ErrorCode expectedCode) {
    checkExceptionFromHttpResponse(error, expectedCode, null, "test error");
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
