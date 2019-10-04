/*
 * Copyright 2019 Google Inc.
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
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.testing.util.MockSleeper;
import com.google.api.client.util.GenericData;
import com.google.common.collect.ImmutableList;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseHttpResponse;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.MockGoogleCredentials;
import java.io.IOException;
import org.junit.Test;

public class ErrorHandlingHttpClientTest {

  @Test(expected = NullPointerException.class)
  public void testNullRequestFactory() {
    new ErrorHandlingHttpClient<>(
        null,
        Utils.getDefaultJsonFactory(),
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
        Utils.getDefaultJsonFactory(),
        null);
  }

  @Test
  public void testSuccessfulRequest() throws FirebaseException {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setContent("{\"foo\": \"bar\"}");
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
        Utils.getDefaultJsonFactory(),
        new TestHttpErrorHandler());

    HttpRequestInfo requestInfo = HttpRequestInfo.buildGetRequest("https://firebase.google.com");
    try {
      client.sendAndParse(requestInfo, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getPlatformErrorCode());
      assertEquals("Network error: Test", e.getMessage());
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
      assertEquals(ErrorCode.INTERNAL, e.getPlatformErrorCode());
      assertEquals("Example error message: {}", e.getMessage());
      FirebaseHttpResponse httpResponse = e.getHttpResponse();
      assertNotNull(httpResponse);
      assertEquals(HttpStatusCodes.STATUS_CODE_SERVER_ERROR, httpResponse.getStatusCode());
      assertEquals("{}", httpResponse.getContent());
      assertEquals(1, httpResponse.getHeaders().size());
      assertEquals(ImmutableList.of("value"), httpResponse.getHeaders().get("custom-header"));
      assertEquals("GET", httpResponse.getRequest().getRequestMethod());
      assertNotNull(e.getCause());
    }
  }

  @Test
  public void testParseError() {
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
      assertEquals(ErrorCode.UNKNOWN, e.getPlatformErrorCode());
      assertEquals("Parse error", e.getMessage());
      FirebaseHttpResponse httpResponse = e.getHttpResponse();
      assertNotNull(httpResponse);
      assertEquals(HttpStatusCodes.STATUS_CODE_OK, httpResponse.getStatusCode());
      assertEquals("not json", httpResponse.getContent());
      assertEquals("GET", httpResponse.getRequest().getRequestMethod());
      assertNotNull(e.getCause());
    }
  }

  @Test
  public void testRetryOnError() {
    CountingLowLevelHttpRequest request = CountingLowLevelHttpRequest.fromStatus(503);
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpRequest(request)
        .build();

    FirebaseApp app = FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("token"))
        .setHttpTransport(transport)
        .build());
    RetryConfig retryConfig = RetryConfig.builder()
        .setMaxRetries(4)
        .setRetryStatusCodes(ImmutableList.of(503))
        .setSleeper(new MockSleeper())
        .build();

    ErrorHandlingHttpClient<FirebaseException> client = new ErrorHandlingHttpClient<>(
        app, new TestHttpErrorHandler(), retryConfig);
    HttpRequestInfo requestInfo = HttpRequestInfo.buildGetRequest("https://firebase.google.com");
    try {
      client.sendAndParse(requestInfo, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.INTERNAL, e.getPlatformErrorCode());
      assertEquals("Example error message: null", e.getMessage());
      assertNotNull(e.getHttpResponse());
      assertNotNull(e.getCause());

      assertEquals(5, request.getCount());
    }
  }

  private static class TestHttpErrorHandler implements HttpErrorHandler<FirebaseException> {

    @Override
    public FirebaseException handleIOException(IOException e) {
      return new FirebaseException(
          ErrorCode.UNKNOWN, "Network error: " + e.getMessage(), null, e);
    }

    @Override
    public FirebaseException handleHttpResponseException(
        HttpResponseException e, FirebaseHttpResponse response) {
      return new FirebaseException(
          ErrorCode.INTERNAL, "Example error message: " + e.getContent(), response, e);
    }

    @Override
    public FirebaseException handleParseException(IOException e, FirebaseHttpResponse response) {
      return new FirebaseException(ErrorCode.UNKNOWN, "Parse error", response, e);
    }
  }
}
