/*
 * Copyright 2020 Google Inc.
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

package com.google.firebase.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.testing.util.MockSleeper;
import com.google.api.client.util.GenericData;
import com.google.auth.oauth2.AccessToken;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.testing.TestResponseInterceptor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.Test;

public class ErrorHandlingHttpClientTest {

  private static final JsonFactory DEFAULT_JSON_FACTORY = Utils.getDefaultJsonFactory();

  private static final HttpRequestInfo TEST_REQUEST = HttpRequestInfo.buildGetRequest(
      "https://firebase.google.com");

  @Test(expected = NullPointerException.class)
  public void testNullRequestFactory() {
    new ErrorHandlingHttpClient<>(
        null,
        DEFAULT_JSON_FACTORY,
        new TestHttpErrorHandler());
  }

  @Test(expected = NullPointerException.class)
  public void testNullJsonFactory() {
    new ErrorHandlingHttpClient<>(
        Utils.getDefaultTransport().createRequestFactory(),
        null,
        new TestHttpErrorHandler());
  }

  @Test(expected = NullPointerException.class)
  public void testNullErrorHandler() {
    new ErrorHandlingHttpClient<>(
        Utils.getDefaultTransport().createRequestFactory(),
        DEFAULT_JSON_FACTORY,
        null);
  }

  @Test
  public void testSuccessfulRequest() throws FirebaseException {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setContent("{\"foo\": \"bar\"}");
    ErrorHandlingHttpClient<FirebaseException> client = createHttpClient(response);

    GenericData body = client.sendAndParse(TEST_REQUEST, GenericData.class);

    assertEquals(1, body.size());
    assertEquals("bar", body.get("foo"));
  }

  @Test
  public void testSuccessfulRequestWithoutContent() throws FirebaseException {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setZeroContent();
    ErrorHandlingHttpClient<FirebaseException> client = createHttpClient(response);

    IncomingHttpResponse responseInfo = client.send(TEST_REQUEST);

    assertEquals(HttpStatusCodes.STATUS_CODE_OK, responseInfo.getStatusCode());
    assertNull(responseInfo.getContent());
  }

  @Test
  public void testSuccessfulRequestWithHeadersAndBody() throws FirebaseException, IOException {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setContent("{\"foo\": \"bar\"}");
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    ErrorHandlingHttpClient<FirebaseException> client = createHttpClient(response)
        .setInterceptor(interceptor);

    HttpRequestInfo request = HttpRequestInfo.buildJsonPostRequest(
        "https://firebase.google.com", ImmutableMap.of("key", "value"));

    request.addHeader("h1", "v1")
        .addAllHeaders(ImmutableMap.of("h2", "v2", "h3", "v3"));
    GenericData body = client.sendAndParse(request, GenericData.class);

    assertEquals(1, body.size());
    assertEquals("bar", body.get("foo"));
    HttpRequest last = interceptor.getLastRequest();
    assertEquals(HttpMethods.POST, last.getRequestMethod());
    assertEquals("v1", last.getHeaders().get("h1"));
    assertEquals("v2", last.getHeaders().get("h2"));
    assertEquals("v3", last.getHeaders().get("h3"));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    last.getContent().writeTo(out);
    assertEquals("{\"key\":\"value\"}", out.toString());
  }

  @Test
  public void testSuccessfulRequestWithNonJsonBody() throws FirebaseException, IOException {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setContent("{\"foo\": \"bar\"}");
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    ErrorHandlingHttpClient<FirebaseException> client = createHttpClient(response)
        .setInterceptor(interceptor);
    HttpContent content = new ByteArrayContent("text/plain", "Test".getBytes());

    HttpRequestInfo request = HttpRequestInfo.buildRequest(
        HttpMethods.POST, "https://firebase.google.com", content);

    GenericData body = client.sendAndParse(request, GenericData.class);

    assertEquals(1, body.size());
    assertEquals("bar", body.get("foo"));
    HttpRequest last = interceptor.getLastRequest();
    assertEquals(HttpMethods.POST, last.getRequestMethod());
    assertEquals("text/plain", last.getContent().getType());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    last.getContent().writeTo(out);
    assertEquals("Test", out.toString());
  }

  @Test
  public void testUnsupportedMethod() throws FirebaseException, IOException {
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(new MockLowLevelHttpResponse().setContent("{}"))
        .setSupportedMethods(ImmutableSet.of(HttpMethods.GET, HttpMethods.POST))
        .build();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    ErrorHandlingHttpClient<FirebaseException> client = new ErrorHandlingHttpClient<>(
        transport.createRequestFactory(), DEFAULT_JSON_FACTORY, new TestHttpErrorHandler());
    client.setInterceptor(interceptor);
    HttpRequestInfo patchRequest = HttpRequestInfo.buildJsonRequest(
        HttpMethods.PATCH, "https://firebase.google.com", ImmutableMap.of("key", "value"));

    client.sendAndParse(patchRequest, GenericData.class);

    HttpRequest last = interceptor.getLastRequest();
    assertEquals(HttpMethods.POST, last.getRequestMethod());
    assertEquals(HttpMethods.PATCH, last.getHeaders().get("X-HTTP-Method-Override"));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    last.getContent().writeTo(out);
    assertEquals("{\"key\":\"value\"}", out.toString());
  }

  @Test
  public void testNetworkError() {
    final IOException exception = new IOException("Test");
    MockHttpTransport transport = new MockHttpTransport(){
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
        throw exception;
      }
    };
    ErrorHandlingHttpClient<FirebaseException> client = new ErrorHandlingHttpClient<>(
        transport.createRequestFactory(),
        DEFAULT_JSON_FACTORY,
        new TestHttpErrorHandler());

    try {
      client.sendAndParse(TEST_REQUEST, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals("IO error: Test", e.getMessage());
      assertNull(e.getHttpResponse());
      assertSame(exception, e.getCause());
    }
  }

  @Test
  public void testErrorResponse() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(HttpStatusCodes.STATUS_CODE_SERVER_ERROR)
        .addHeader("Custom-Header", "value")
        .setContent("{}");
    ErrorHandlingHttpClient<FirebaseException> client = createHttpClient(response);

    try {
      client.sendAndParse(TEST_REQUEST, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.INTERNAL, e.getErrorCode());
      assertEquals("Example error message: {}", e.getMessage());
      assertHttpResponse(e, HttpStatusCodes.STATUS_CODE_SERVER_ERROR, "{}");
      IncomingHttpResponse httpResponse = e.getHttpResponse();
      assertEquals(1, httpResponse.getHeaders().size());
      assertEquals(ImmutableList.of("value"), httpResponse.getHeaders().get("custom-header"));
      assertNotNull(e.getCause());
    }
  }

  @Test
  public void testParseError() {
    String payload = "not json";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setContent(payload);
    ErrorHandlingHttpClient<FirebaseException> client = createHttpClient(response);

    try {
      client.sendAndParse(TEST_REQUEST, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals("Parse error", e.getMessage());
      assertHttpResponse(e, HttpStatusCodes.STATUS_CODE_OK, payload);
      assertNotNull(e.getCause());
    }
  }

  @Test
  public void testRetryOnError() {
    CountingLowLevelHttpRequest request = CountingLowLevelHttpRequest.fromStatus(503);
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpRequest(request)
        .build();

    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("token"))
        .setHttpTransport(transport)
        .build());
    RetryConfig retryConfig = RetryConfig.builder()
        .setMaxRetries(4)
        .setRetryStatusCodes(ImmutableList.of(503))
        .setSleeper(new MockSleeper())
        .build();
    HttpRequestFactory requestFactory = ApiClientUtils.newAuthorizedRequestFactory(
        app, retryConfig);
    ErrorHandlingHttpClient<FirebaseException> client = new ErrorHandlingHttpClient<>(
        requestFactory, Utils.getDefaultJsonFactory(), new TestHttpErrorHandler());

    try {
      client.sendAndParse(TEST_REQUEST, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.INTERNAL, e.getErrorCode());
      assertEquals("Example error message: null", e.getMessage());
      assertHttpResponse(e, HttpStatusCodes.STATUS_CODE_SERVICE_UNAVAILABLE, null);
      assertNotNull(e.getCause());

      assertEquals(5, request.getCount());
    } finally {
      app.delete();
    }
  }

  @Test
  public void testRequestInitializationError() {
    CountingLowLevelHttpRequest request = CountingLowLevelHttpRequest.fromStatus(503);
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpRequest(request)
        .build();

    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials() {
          @Override
          public AccessToken refreshAccessToken() throws IOException {
            throw new IOException("Failed to fetch credentials");
          }
        })
        .setHttpTransport(transport)
        .build());
    HttpRequestFactory requestFactory = ApiClientUtils.newAuthorizedRequestFactory(app);
    ErrorHandlingHttpClient<FirebaseException> client = new ErrorHandlingHttpClient<>(
        requestFactory, Utils.getDefaultJsonFactory(), new TestHttpErrorHandler());

    try {
      client.sendAndParse(TEST_REQUEST, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals("IO error: Failed to fetch credentials", e.getMessage());
      assertNull(e.getHttpResponse());
      assertNotNull(e.getCause());
    } finally {
      app.delete();
    }
  }

  private ErrorHandlingHttpClient<FirebaseException> createHttpClient(
      MockLowLevelHttpResponse response) {
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    return new ErrorHandlingHttpClient<>(
        transport.createRequestFactory(),
        DEFAULT_JSON_FACTORY,
        new TestHttpErrorHandler());
  }

  private void assertHttpResponse(FirebaseException e, int statusCode, String content) {
    IncomingHttpResponse httpResponse = e.getHttpResponse();
    assertNotNull(httpResponse);
    assertEquals(statusCode, httpResponse.getStatusCode());
    assertEquals(content, httpResponse.getContent());
    assertEquals("GET", httpResponse.getRequest().getMethod());
  }

  private static class TestHttpErrorHandler implements HttpErrorHandler<FirebaseException> {
    @Override
    public FirebaseException handleIOException(IOException e) {
      return new FirebaseException(
          ErrorCode.UNKNOWN, "IO error: " + e.getMessage(), e);
    }

    @Override
    public FirebaseException handleHttpResponseException(
        HttpResponseException e, IncomingHttpResponse response) {
      return new FirebaseException(
          ErrorCode.INTERNAL, "Example error message: " + e.getContent(), e, response);
    }

    @Override
    public FirebaseException handleParseException(IOException e, IncomingHttpResponse response) {
      return new FirebaseException(ErrorCode.UNKNOWN, "Parse error", e, response);
    }
  }
}
