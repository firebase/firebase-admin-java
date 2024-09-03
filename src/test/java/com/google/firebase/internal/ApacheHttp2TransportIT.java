/*
 * Copyright 2024 Google Inc.
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.GenericData;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.auth.MockGoogleCredentials;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class ApacheHttp2TransportIT {
  private static FirebaseApp app;
  private static final GoogleCredentials MOCK_CREDENTIALS = new MockGoogleCredentials("test_token");
  private static final ImmutableMap<String, Object> payload = 
      ImmutableMap.<String, Object>of("foo", "bar");

  // Sets a 5 second delay before response
  private static final String DELAY_URL = "https://nghttp2.org/httpbin/delay/5";
  private static final String NO_CONNECT_URL = "https://google.com:81";
  private static final String GET_URL = "https://nghttp2.org/httpbin/get";
  private static final String POST_URL = "https://nghttp2.org/httpbin/post";

  @BeforeClass
  public static void setUpClass() {
  }

  @After
  public void cleanup() {
    if (app != null) {
      app.delete();
    }
  }
  
  @Test(timeout = 10_000L)
  public void testUnauthorizedGetRequest() throws FirebaseException {
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(true);
    HttpRequestInfo request = HttpRequestInfo.buildGetRequest(GET_URL);
    IncomingHttpResponse response = httpClient.send(request);
    assertEquals(200, response.getStatusCode());
  }

  @Test(timeout = 10_000L)
  public void testUnauthorizedPostRequest() throws FirebaseException {
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(false);
    HttpRequestInfo request = HttpRequestInfo.buildJsonPostRequest(POST_URL, payload);
    GenericData body = httpClient.sendAndParse(request, GenericData.class);
    assertEquals("{\"foo\":\"bar\"}", body.get("data"));
  }

  @Test(timeout = 10_000L)
  public void testConnectTimeoutAuthorizedGet() throws FirebaseException {
    app  = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        // .setConnectTimeout(100)
        .build(), "test-app");
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(true, app);
    HttpRequestInfo request = HttpRequestInfo.buildGetRequest(GET_URL);

    try {
      httpClient.send(request);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      System.out.println(e.getCause());
      System.out.println(e.getCause().getMessage());
      System.out.println(e.getCause().getCause());
      System.out.println(e.getCause().getCause().getMessage());
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals("IO error: Connection Timeout", e.getMessage());
      assertNull(e.getHttpResponse());
    }
  }

  @Test(timeout = 10_000L)
  public void testConnectTimeoutAuthorizedPost() throws FirebaseException {
    app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .setConnectTimeout(100)
        .build(), "test-app");
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(true, app);
    HttpRequestInfo request = HttpRequestInfo.buildJsonPostRequest(NO_CONNECT_URL, payload);

    System.out.println(System.getProperty("java.version"));

    try {
      httpClient.send(request);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      System.out.println(e.getCause());
      System.out.println(e.getCause().getMessage());
      System.out.println(e.getCause().getCause());
      System.out.println(e.getCause().getCause().getMessage());
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals("IO error: Exception in request", e.getMessage());
      assertNull(e.getHttpResponse());
    }
  }

  @Test(timeout = 10_000L)
  public void testReadTimeoutAuthorizedGet() throws FirebaseException {
    app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .setReadTimeout(100)
        .build(), "test-app");
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(true, app);
    HttpRequestInfo request = HttpRequestInfo.buildGetRequest(DELAY_URL);

    try {
      httpClient.send(request);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals("IO error: Stream exception in request", e.getMessage());
      assertNull(e.getHttpResponse());
    }
  }

  @Test(timeout = 10_000L)
  public void testReadTimeoutAuthorizedPost() throws FirebaseException {
    app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .setReadTimeout(100)
        .build(), "test-app");
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(true, app);
    HttpRequestInfo request = HttpRequestInfo.buildJsonPostRequest(DELAY_URL, payload);

    try {
      httpClient.send(request);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals("IO error: Stream exception in request", e.getMessage());
      assertNull(e.getHttpResponse());
    }
  }

  @Test(timeout = 10_000L)
  public void testWriteTimeoutAuthorizedGet() throws FirebaseException {
    app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .setWriteTimeout(100)
        .build(), "test-app");
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(true, app);
    HttpRequestInfo request = HttpRequestInfo.buildGetRequest(GET_URL);

    try {
      httpClient.send(request);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals("IO error: Write Timeout", e.getMessage());
      assertNull(e.getHttpResponse());
    }
  }

  @Test(timeout = 10_000L)
  public void testWriteTimeoutAuthorizedPost() throws FirebaseException {
    app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .setWriteTimeout(100)
        .build(), "test-app");
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(true, app);
    HttpRequestInfo request = HttpRequestInfo.buildJsonPostRequest(POST_URL, payload);

    try {
      httpClient.send(request);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals("IO error: Write Timeout", e.getMessage());
      assertNull(e.getHttpResponse());
    }
  }

  @Test(timeout = 10_000L)
  public void testRequestShouldNotFollowRedirects() throws IOException {
    ApacheHttp2Transport transport = new ApacheHttp2Transport();
    ApacheHttp2Request request = transport.buildRequest("GET",
        "https://google.com");
    LowLevelHttpResponse response = request.execute();

    assertEquals(301, response.getStatusCode());
    assert (response instanceof ApacheHttp2Response);
    assertEquals("https://www.google.com/", ((ApacheHttp2Response) response).getHeaderValue("location"));
  }

  @Test(timeout = 10_000L)
  public void testRequestCanSetHeaders() {
    final AtomicBoolean interceptorCalled = new AtomicBoolean(false);
    CloseableHttpAsyncClient client = HttpAsyncClients.custom()
        .addRequestInterceptorFirst(
            new HttpRequestInterceptor() {
              @Override
              public void process(
                  HttpRequest request, EntityDetails details, HttpContext context)
                  throws HttpException, IOException {
                Header header = request.getFirstHeader("foo");
                assertNotNull("Should have found header", header);
                assertEquals("bar", header.getValue());
                interceptorCalled.set(true);
                throw new IOException("cancelling request");
              }
            })
        .build();

    ApacheHttp2Transport transport = new ApacheHttp2Transport(client);
    ApacheHttp2Request request = transport.buildRequest("GET", "http://www.google.com");
    request.addHeader("foo", "bar");
    try {
      request.execute();
      fail("should not actually make the request");
    } catch (IOException exception) {
      assertEquals("Exception in request", exception.getMessage());
    }
    assertTrue("Expected to have called our test interceptor", interceptorCalled.get());
  }

  private static ErrorHandlingHttpClient<FirebaseException> getHttpClient(boolean authorized,
      FirebaseApp app) {
    HttpRequestFactory requestFactory;
    if (authorized) {
      requestFactory = ApiClientUtils.newAuthorizedRequestFactory(app);
    } else {
      requestFactory = ApiClientUtils.newUnauthorizedRequestFactory(app);
    }
    JsonFactory jsonFactory = ApiClientUtils.getDefaultJsonFactory();
    TestHttpErrorHandler errorHandler = new TestHttpErrorHandler();
    return new ErrorHandlingHttpClient<>(requestFactory, jsonFactory, errorHandler);
  }

  private static ErrorHandlingHttpClient<FirebaseException> getHttpClient(boolean authorized) {
    app = FirebaseApp.initializeApp(FirebaseOptions.builder()
    .setCredentials(MOCK_CREDENTIALS)
    .build(), "test-app");
    return getHttpClient(authorized, app);
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
