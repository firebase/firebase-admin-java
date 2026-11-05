/*
 * Copyright 2024 Google LLC
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

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.JsonFactory;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.auth.MockGoogleCredentials;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class ApacheHttp2TransportIT {
  private static FirebaseApp app;
  private static final GoogleCredentials MOCK_CREDENTIALS = new MockGoogleCredentials("test_token");
  private static final ImmutableMap<String, Object> payload =
      ImmutableMap.<String, Object>of("foo", "bar");

  private static ServerSocket serverSocket;
  private static Socket fillerSocket;
  private static int port;

  @BeforeClass
  public static void setUpClass() throws IOException {
    // Start server socket with a backlog queue of 1 and a automatically assigned
    // port
    serverSocket = new ServerSocket(0, 1);
    port = serverSocket.getLocalPort();
    // Fill the backlog queue to force socket to ignore future connections
    fillerSocket = new Socket();
    fillerSocket.connect(serverSocket.getLocalSocketAddress());
  }

  @AfterClass
  public static void cleanUpClass() throws IOException {
    if (serverSocket != null && !serverSocket.isClosed()) {
      serverSocket.close();
    }
    if (fillerSocket != null && !fillerSocket.isClosed()) {
      fillerSocket.close();
    }
  }

  @After
  public void cleanup() {
    if (app != null) {
      app.delete();
    }

    System.clearProperty("http.proxyHost");
    System.clearProperty("http.proxyPort");
    System.clearProperty("https.proxyHost");
    System.clearProperty("https.proxyPort");
  }

  @Test(timeout = 10_000L)
  public void testConnectTimeoutAuthorizedGet() throws FirebaseException {
    app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .setConnectTimeout(100)
        .build(), "test-app");
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(app);
    HttpRequestInfo request = HttpRequestInfo.buildGetRequest("https://localhost:" + port);

    try {
      httpClient.send(request);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
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
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(app);
    HttpRequestInfo request = HttpRequestInfo.buildJsonPostRequest(
        "https://localhost:" + port, payload);

    try {
      httpClient.send(request);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals("IO error: Connection Timeout", e.getMessage());
      assertNull(e.getHttpResponse());
    }
  }

  @Test(timeout = 10_000L)
  public void testRequestShouldNotFollowRedirects() throws IOException {
    ApacheHttp2Transport transport = new ApacheHttp2Transport();
    try {
      ApacheHttp2Request request = transport.buildRequest("GET", "https://google.com");
      LowLevelHttpResponse response = request.execute();

      assertEquals(301, response.getStatusCode());
      assertTrue(response instanceof ApacheHttp2Response);
      assertEquals(
          "https://www.google.com/",
          ((ApacheHttp2Response) response).getHeaderValue("location"));
    } finally {
      transport.shutdown();
    }
  }

  @Test(timeout = 10_000L)
  public void testRequestCanSetHeaders() throws IOException {
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
    try {
      ApacheHttp2Request request = transport.buildRequest("GET", "http://www.google.com");
      request.addHeader("foo", "bar");
      try {
        request.execute();
        fail("should not actually make the request");
      } catch (IOException exception) {
        assertEquals("Unknown exception in request", exception.getMessage());
      }
      assertTrue("Expected to have called our test interceptor", interceptorCalled.get());
    } finally {
      transport.shutdown();
    }
  }

  @Test(timeout = 10_000L)
  public void testVerifyProxyIsRespected() throws IOException {
    System.setProperty("https.proxyHost", "localhost");
    System.setProperty("https.proxyPort", "8080");

    HttpTransport transport = new ApacheHttp2Transport();
    try {
      transport.createRequestFactory()
          .buildGetRequest(new GenericUrl("https://www.google.com"))
          .execute();
      fail("No exception thrown for HTTP error response");
    } catch (IOException e) {
      assertEquals("Connection exception in request", e.getMessage());
      assertTrue(e.getCause().getMessage().contains("localhost:8080"));
    } finally {
      transport.shutdown();
    }
  }

  @Test
  public void testVerifyDefaultTransportReused() {
    FirebaseOptions o1 = FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .build();

    FirebaseOptions o2 = FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .build();

    HttpTransport t1 = o1.getHttpTransport();
    HttpTransport t2 = o2.getHttpTransport();
    assertEquals(t1, t2);
  }

  private static ErrorHandlingHttpClient<FirebaseException> getHttpClient(FirebaseApp app) {
    HttpRequestFactory requestFactory = ApiClientUtils.newAuthorizedRequestFactory(app);
    JsonFactory jsonFactory = ApiClientUtils.getDefaultJsonFactory();
    TestHttpErrorHandler errorHandler = new TestHttpErrorHandler();
    return new ErrorHandlingHttpClient<>(requestFactory, jsonFactory, errorHandler);
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
