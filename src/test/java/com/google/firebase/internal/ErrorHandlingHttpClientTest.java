/*
  <<<<<<< hkj-error-handling
 * Copyright 2019 Google Inc.
  =======
 * Copyright 2020 Google Inc.
  >>>>>>> master
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

  <<<<<<< hkj-error-handling
import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.LowLevelHttpRequest;
  =======
  <<<<<<< v7
import com.google.api.client.googleapis.util.Utils;
  =======
  >>>>>>> master
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.JsonFactory;
  >>>>>>> master
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.testing.util.MockSleeper;
import com.google.api.client.util.GenericData;
  <<<<<<< hkj-error-handling
import com.google.common.collect.ImmutableList;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseHttpResponse;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.MockGoogleCredentials;
  =======
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
  >>>>>>> master
import java.io.IOException;
import org.junit.Test;

public class ErrorHandlingHttpClientTest {

  <<<<<<< hkj-error-handling
  =======
  <<<<<<< v7
  private static final JsonFactory DEFAULT_JSON_FACTORY = Utils.getDefaultJsonFactory();
  =======
  private static final JsonFactory DEFAULT_JSON_FACTORY = ApiClientUtils.getDefaultJsonFactory();
  >>>>>>> master

  private static final HttpRequestInfo TEST_REQUEST = HttpRequestInfo.buildGetRequest(
      "https://firebase.google.com");

  >>>>>>> master
  @Test(expected = NullPointerException.class)
  public void testNullRequestFactory() {
    new ErrorHandlingHttpClient<>(
        null,
  <<<<<<< hkj-error-handling
        Utils.getDefaultJsonFactory(),
  =======
        DEFAULT_JSON_FACTORY,
  >>>>>>> master
        new TestHttpErrorHandler());
  }

  @Test(expected = NullPointerException.class)
  public void testNullJsonFactory() {
    new ErrorHandlingHttpClient<>(
  <<<<<<< hkj-error-handling
        Utils.getDefaultTransport().createRequestFactory(),
  =======
  <<<<<<< v7
        Utils.getDefaultTransport().createRequestFactory(),
  =======
        ApiClientUtils.getDefaultTransport().createRequestFactory(),
  >>>>>>> master
  >>>>>>> master
        null,
        new TestHttpErrorHandler());
  }

  @Test(expected = NullPointerException.class)
  public void testNullErrorHandler() {
    new ErrorHandlingHttpClient<>(
  <<<<<<< hkj-error-handling
        Utils.getDefaultTransport().createRequestFactory(),
        Utils.getDefaultJsonFactory(),
  =======
  <<<<<<< v7
        Utils.getDefaultTransport().createRequestFactory(),
  =======
        ApiClientUtils.getDefaultTransport().createRequestFactory(),
  >>>>>>> master
        DEFAULT_JSON_FACTORY,
  >>>>>>> master
        null);
  }

  @Test
  public void testSuccessfulRequest() throws FirebaseException {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setContent("{\"foo\": \"bar\"}");
  <<<<<<< hkj-error-handling
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    ErrorHandlingHttpClient<FirebaseException> client = new ErrorHandlingHttpClient<>(
        transport.createRequestFactory(),
        Utils.getDefaultJsonFactory(),
        new TestHttpErrorHandler());

    HttpRequestInfo requestInfo = HttpRequestInfo.buildGetRequest("https://firebase.google.com");
    GenericData body = client.sendAndParse(requestInfo, GenericData.class);
    assertEquals(1, body.size());
    assertEquals("bar", body.get("foo"));
  =======
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
  >>>>>>> master
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
  <<<<<<< hkj-error-handling
        Utils.getDefaultJsonFactory(),
        new TestHttpErrorHandler());

    HttpRequestInfo requestInfo = HttpRequestInfo.buildGetRequest("https://firebase.google.com");
    try {
      client.sendAndParse(requestInfo, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getCode());
      assertEquals("Network error: Test", e.getMessage());
  =======
        DEFAULT_JSON_FACTORY,
        new TestHttpErrorHandler());

    try {
      client.sendAndParse(TEST_REQUEST, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals("IO error: Test", e.getMessage());
  >>>>>>> master
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
  <<<<<<< hkj-error-handling
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    ErrorHandlingHttpClient<FirebaseException> client = new ErrorHandlingHttpClient<>(
        transport.createRequestFactory(),
        Utils.getDefaultJsonFactory(),
        new TestHttpErrorHandler());

    HttpRequestInfo requestInfo = HttpRequestInfo.buildGetRequest("https://firebase.google.com");
    try {
      client.sendAndParse(requestInfo, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.INTERNAL, e.getCode());
      assertEquals("Example error message: {}", e.getMessage());
      FirebaseHttpResponse httpResponse = e.getHttpResponse();
      assertNotNull(httpResponse);
      assertEquals(HttpStatusCodes.STATUS_CODE_SERVER_ERROR, httpResponse.getStatusCode());
      assertEquals("{}", httpResponse.getContent());
      assertEquals(1, httpResponse.getHeaders().size());
      assertEquals(ImmutableList.of("value"), httpResponse.getHeaders().get("custom-header"));
      assertEquals("GET", httpResponse.getRequest().getMethod());
  =======
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
  >>>>>>> master
      assertNotNull(e.getCause());
    }
  }

  @Test
  public void testParseError() {
  <<<<<<< hkj-error-handling
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setContent("not json");
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    ErrorHandlingHttpClient<FirebaseException> client = new ErrorHandlingHttpClient<>(
        transport.createRequestFactory(),
        Utils.getDefaultJsonFactory(),
        new TestHttpErrorHandler());

    HttpRequestInfo requestInfo = HttpRequestInfo.buildGetRequest("https://firebase.google.com");
    try {
      client.sendAndParse(requestInfo, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getCode());
      assertEquals("Parse error", e.getMessage());
      FirebaseHttpResponse httpResponse = e.getHttpResponse();
      assertNotNull(httpResponse);
      assertEquals(HttpStatusCodes.STATUS_CODE_OK, httpResponse.getStatusCode());
      assertEquals("not json", httpResponse.getContent());
      assertEquals("GET", httpResponse.getRequest().getMethod());
  =======
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
  >>>>>>> master
      assertNotNull(e.getCause());
    }
  }

  @Test
  public void testRetryOnError() {
    CountingLowLevelHttpRequest request = CountingLowLevelHttpRequest.fromStatus(503);
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpRequest(request)
        .build();

  <<<<<<< hkj-error-handling
  =======
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("token"))
        .setHttpTransport(transport)
        .build());
  >>>>>>> master
    RetryConfig retryConfig = RetryConfig.builder()
        .setMaxRetries(4)
        .setRetryStatusCodes(ImmutableList.of(503))
        .setSleeper(new MockSleeper())
        .build();
  <<<<<<< hkj-error-handling
    HttpRequestInfo requestInfo = HttpRequestInfo.buildGetRequest("https://firebase.google.com");

    FirebaseApp app = FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("token"))
        .setHttpTransport(transport)
        .build());
    ErrorHandlingHttpClient<FirebaseException> client = new ErrorHandlingHttpClient<>(
        app, new TestHttpErrorHandler(), retryConfig);
    try {
      client.sendAndParse(requestInfo, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.INTERNAL, e.getCode());
      assertEquals("Example error message: null", e.getMessage());
      assertNotNull(e.getHttpResponse());
  =======
    HttpRequestFactory requestFactory = ApiClientUtils.newAuthorizedRequestFactory(
        app, retryConfig);
    ErrorHandlingHttpClient<FirebaseException> client = new ErrorHandlingHttpClient<>(
  <<<<<<< v7
        requestFactory, Utils.getDefaultJsonFactory(), new TestHttpErrorHandler());
  =======
        requestFactory, ApiClientUtils.getDefaultJsonFactory(), new TestHttpErrorHandler());
  >>>>>>> master

    try {
      client.sendAndParse(TEST_REQUEST, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.INTERNAL, e.getErrorCode());
      assertEquals("Example error message: null", e.getMessage());
      assertHttpResponse(e, HttpStatusCodes.STATUS_CODE_SERVICE_UNAVAILABLE, null);
  >>>>>>> master
      assertNotNull(e.getCause());

      assertEquals(5, request.getCount());
    } finally {
      app.delete();
    }
  }

  <<<<<<< hkj-error-handling
  private static class TestHttpErrorHandler implements HttpErrorHandler<FirebaseException> {

    @Override
    public FirebaseException handleIOException(IOException e) {
      return new FirebaseException(
          ErrorCode.UNKNOWN, "Network error: " + e.getMessage(), null, e);
  =======
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
  <<<<<<< v7
        requestFactory, Utils.getDefaultJsonFactory(), new TestHttpErrorHandler());
  =======
        requestFactory, ApiClientUtils.getDefaultJsonFactory(), new TestHttpErrorHandler());
  >>>>>>> master

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
  >>>>>>> master
    }

    @Override
    public FirebaseException handleHttpResponseException(
  <<<<<<< hkj-error-handling
        HttpResponseException e, FirebaseHttpResponse response) {
      return new FirebaseException(
          ErrorCode.INTERNAL, "Example error message: " + e.getContent(), response, e);
    }

    @Override
    public FirebaseException handleParseException(IOException e, FirebaseHttpResponse response) {
      return new FirebaseException(ErrorCode.UNKNOWN, "Parse error", response, e);
  =======
        HttpResponseException e, IncomingHttpResponse response) {
      return new FirebaseException(
          ErrorCode.INTERNAL, "Example error message: " + e.getContent(), e, response);
    }

    @Override
    public FirebaseException handleParseException(IOException e, IncomingHttpResponse response) {
      return new FirebaseException(ErrorCode.UNKNOWN, "Parse error", e, response);
  >>>>>>> master
    }
  }
}
