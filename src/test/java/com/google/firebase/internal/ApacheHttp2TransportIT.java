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
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.support.BasicHttpServerRequestHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;

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
  public void testUnauthorizedGetRequest() throws Exception {
    final HttpRequestHandler handler = new HttpRequestHandler() {
      @Override
      public void handle(
          ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
          throws HttpException, IOException {
        response.setCode(HttpStatus.SC_OK);
        response.setHeader(HttpHeaders.CONTENT_LENGTH, "0");
      }
    };
    try (FakeServer server = new FakeServer(handler)) {
      ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(false);
      HttpRequestInfo request = HttpRequestInfo.buildGetRequest("http://localhost:" + server.getPort());
      IncomingHttpResponse response = httpClient.send(request);
      assertEquals(200, response.getStatusCode());
    }
  }

  @Test(timeout = 10_000L)
  public void testUnauthorizedPostRequest() throws Exception {
    final HttpRequestHandler handler = new HttpRequestHandler() {
      @Override
      public void handle(
          ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
          throws HttpException, IOException {
        String responseJson = "{\"data\":\"{\\\"foo\\\":\\\"bar\\\"}\"}";
        byte[] responseData = responseJson.getBytes(StandardCharsets.UTF_8);
        response.setCode(HttpStatus.SC_OK);
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(responseData.length));
        response.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        response.setEntity(new ByteArrayEntity(responseData, ContentType.APPLICATION_JSON));
      }
    };
    try (FakeServer server = new FakeServer(handler)) {
      ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(false);
      HttpRequestInfo request = HttpRequestInfo.buildJsonPostRequest("http://localhost:" + server.getPort(), payload);
      GenericData body = httpClient.sendAndParse(request, GenericData.class);
      assertEquals("{\"foo\":\"bar\"}", body.get("data"));
    }
  }

  @Test(timeout = 10_000L)
  public void testConnectTimeoutAuthorizedGet() throws FirebaseException {
    app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .setConnectTimeout(100)
        .build(), "test-app");
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(true, app);
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
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(true, app);
    HttpRequestInfo request = HttpRequestInfo.buildJsonPostRequest("https://localhost:" + port, payload);

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
  public void testReadTimeoutAuthorizedGet() throws Exception {
    final HttpRequestHandler handler = new HttpRequestHandler() {
      @Override
      public void handle(
          ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
          throws HttpException, IOException {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          // Ignore
        }
        response.setCode(HttpStatus.SC_OK);
      }
    };
    try (FakeServer server = new FakeServer(handler)) {
      app = FirebaseApp.initializeApp(FirebaseOptions.builder()
          .setCredentials(MOCK_CREDENTIALS)
          .setConnectTimeout(5000)
          .setReadTimeout(100)
          .build(), "test-app");
      ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(true, app);
      HttpRequestInfo request = HttpRequestInfo.buildGetRequest("http://localhost:" + server.getPort());

      try {
        httpClient.send(request);
        fail("No exception thrown for HTTP error response");
      } catch (FirebaseException e) {
        assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
        assertEquals("IO error: Connection Timeout", e.getMessage());
        assertNull(e.getHttpResponse());
      }
    }
  }

  @Test(timeout = 10_000L)
  public void testReadTimeoutAuthorizedPost() throws Exception {
    final HttpRequestHandler handler = new HttpRequestHandler() {
      @Override
      public void handle(
          ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
          throws HttpException, IOException {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          // Ignore
        }
        response.setCode(HttpStatus.SC_OK);
      }
    };
    try (FakeServer server = new FakeServer(handler)) {
      app = FirebaseApp.initializeApp(FirebaseOptions.builder()
          .setCredentials(MOCK_CREDENTIALS)
          .setConnectTimeout(5000)
          .setReadTimeout(100)
          .build(), "test-app-2");
      ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(true, app);
      HttpRequestInfo request = HttpRequestInfo.buildJsonPostRequest("http://localhost:" + server.getPort(), payload);

      try {
        httpClient.send(request);
        fail("No exception thrown for HTTP error response");
      } catch (FirebaseException e) {
        assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
        assertEquals("IO error: Connection Timeout", e.getMessage());
        assertNull(e.getHttpResponse());
      }
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
      assertEquals("Unknown exception in request", exception.getMessage());
    }
    assertTrue("Expected to have called our test interceptor", interceptorCalled.get());
  }

  @Test(timeout = 10_000L)
  public void testVerifyProxyIsRespected() {
    try {
      System.setProperty("https.proxyHost", "localhost");
      System.setProperty("https.proxyPort", "8080");

      HttpTransport transport = new ApacheHttp2Transport();
      transport.createRequestFactory().buildGetRequest(new GenericUrl("https://dummy.nonexistent/get")).execute();
      fail("No exception thrown for HTTP error response");
    } catch (IOException e) {
      assertEquals("Connection exception in request", e.getMessage());
      assertTrue(e.getCause().getMessage().contains("localhost:8080"));
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

  private static class FakeServer implements AutoCloseable {
    private final HttpServer server;

    FakeServer(final HttpRequestHandler httpHandler) throws IOException {
      HttpRequestMapper<HttpRequestHandler> mapper = new HttpRequestMapper<HttpRequestHandler>() {
        @Override
        public HttpRequestHandler resolve(HttpRequest request, HttpContext context)
            throws HttpException {
          return httpHandler;
        }
      };
      server = new HttpServer(
          0,
          HttpService.builder()
              .withHttpProcessor(
                  new HttpProcessor() {
                    @Override
                    public void process(
                        HttpRequest request, EntityDetails entity, HttpContext context)
                        throws HttpException, IOException {
                    }

                    @Override
                    public void process(
                        HttpResponse response, EntityDetails entity, HttpContext context)
                        throws HttpException, IOException {
                    }
                  })
              .withHttpServerRequestHandler(new BasicHttpServerRequestHandler(mapper))
              .build(),
          null,
          null,
          null,
          null,
          null,
          null);
      server.start();
    }

    public int getPort() {
      return server.getLocalPort();
    }

    @Override
    public void close() {
      server.initiateShutdown();
    }
  }
}

