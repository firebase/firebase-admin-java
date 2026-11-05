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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.ByteArrayStreamingContent;
import com.google.api.client.util.GenericData;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.client5.http.async.HttpAsyncClient;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class ApacheHttp2TransportTest {
  private static final ImmutableMap<String, Object> TEST_PAYLOAD =
      ImmutableMap.<String, Object>of("foo", "bar");

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testContentLengthSet() throws Exception {
    SimpleRequestBuilder requestBuilder = SimpleRequestBuilder.create(HttpMethods.POST)
        .setUri("http://www.google.com");

    ApacheHttp2Request request = new ApacheHttp2Request(
        new MockApacheHttp2AsyncClient() {
          @SuppressWarnings("unchecked")
          @Override
          public <T> Future<T> doExecute(
              final HttpHost target,
              final AsyncRequestProducer requestProducer,
              final AsyncResponseConsumer<T> responseConsumer,
              final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
              final HttpContext context,
              final FutureCallback<T> callback) {
            return (Future<T>) CompletableFuture
                .completedFuture(
                    new Message<HttpResponse, ApacheHttp2Entity>(new BasicHttpResponse(200), null));
          }
        }, requestBuilder);

    HttpContent content = new ByteArrayContent("text/plain",
        "sample".getBytes(StandardCharsets.UTF_8));
    request.setStreamingContent(content);
    request.setContentLength(content.getLength());
    request.execute();

    assertFalse(request.getEntityProducer().isChunked());
    assertEquals(6, request.getEntityProducer().getContentLength());
  }

  @Test
  public void testChunked() throws Exception {
    byte[] buf = new byte[300];
    Arrays.fill(buf, (byte) ' ');
    SimpleRequestBuilder requestBuilder = SimpleRequestBuilder.create(HttpMethods.POST)
        .setUri("http://www.google.com");
    ApacheHttp2Request request = new ApacheHttp2Request(
        new MockApacheHttp2AsyncClient() {
          @SuppressWarnings("unchecked")
          @Override
          public <T> Future<T> doExecute(
              final HttpHost target,
              final AsyncRequestProducer requestProducer,
              final AsyncResponseConsumer<T> responseConsumer,
              final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
              final HttpContext context,
              final FutureCallback<T> callback) {
            return (Future<T>) CompletableFuture
                .completedFuture(
                    new Message<HttpResponse, ApacheHttp2Entity>(new BasicHttpResponse(200), null));
          }
        }, requestBuilder);

    HttpContent content = new InputStreamContent("text/plain", new ByteArrayInputStream(buf));
    request.setStreamingContent(content);
    request.execute();

    assertTrue(request.getEntityProducer().isChunked());
    assertEquals(-1, request.getEntityProducer().getContentLength());
  }

  @Test
  public void testExecute() throws Exception {
    SimpleHttpResponse simpleHttpResponse = SimpleHttpResponse.create(200, new byte[] { 1, 2, 3 });

    SimpleRequestBuilder requestBuilder = SimpleRequestBuilder.create(HttpMethods.POST)
        .setUri("http://www.google.com");

    ApacheHttp2Request request = new ApacheHttp2Request(
        new MockApacheHttp2AsyncClient() {
          @SuppressWarnings("unchecked")
          @Override
          public <T> Future<T> doExecute(
              final HttpHost target,
              final AsyncRequestProducer requestProducer,
              final AsyncResponseConsumer<T> responseConsumer,
              final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
              final HttpContext context,
              final FutureCallback<T> callback) {
            return (Future<T>) CompletableFuture
                .completedFuture(new Message<HttpResponse, ApacheHttp2Entity>(simpleHttpResponse,
                    new ApacheHttp2Entity(simpleHttpResponse.getBodyBytes(), null)));
          }
        }, requestBuilder);
    LowLevelHttpResponse response = request.execute();
    assertTrue(response instanceof ApacheHttp2Response);

    // we confirm that the simple response we prepared in this test is the same as
    // the content's response
    assertTrue(response.getContent() instanceof ByteArrayInputStream);
    assertEquals(simpleHttpResponse, ((ApacheHttp2Response) response).getMessage().getHead());
    // No need to cloase ByteArrayInputStream since close() has no effect.
  }

  @Test
  public void testApacheHttpTransport() {
    ApacheHttp2Transport transport = new ApacheHttp2Transport();
    checkHttpTransport(transport);
    assertFalse(transport.isMtls());
  }

  @Test
  public void testApacheHttpTransportWithParam() {
    HttpAsyncClientBuilder clientBuilder = HttpAsyncClients.custom();
    ApacheHttp2Transport transport = new ApacheHttp2Transport(clientBuilder.build(), true);
    checkHttpTransport(transport);
    assertTrue(transport.isMtls());
  }

  @Test
  public void testNewDefaultHttpClient() {
    HttpAsyncClient client = ApacheHttp2Transport.newDefaultHttpAsyncClient();
    checkHttpClient(client);
  }

  @Test
  public void testDefaultHttpClientBuilder() {
    HttpAsyncClientBuilder clientBuilder = ApacheHttp2Transport.defaultHttpAsyncClientBuilder();
    HttpAsyncClient client = clientBuilder.build();
    checkHttpClient(client);
  }

  private void checkHttpTransport(ApacheHttp2Transport transport) {
    assertNotNull(transport);
    HttpAsyncClient client = transport.getHttpClient();
    checkHttpClient(client);
  }

  private void checkHttpClient(HttpAsyncClient client) {
    assertNotNull(client);
  }

  @Test
  public void testRequestsWithContent() throws IOException {
    // This test confirms that we can set the content on any type of request
    CloseableHttpAsyncClient mockClient = new MockApacheHttp2AsyncClient() {
      @SuppressWarnings("unchecked")
      @Override
      public <T> Future<T> doExecute(
          final HttpHost target,
          final AsyncRequestProducer requestProducer,
          final AsyncResponseConsumer<T> responseConsumer,
          final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
          final HttpContext context,
          final FutureCallback<T> callback) {
        return (Future<T>) CompletableFuture
            .completedFuture(new Message<HttpResponse, ApacheHttp2Entity>(
                new BasicHttpResponse(200), null));
      }
    };
    ApacheHttp2Transport transport = new ApacheHttp2Transport(mockClient);

    // Test GET.
    execute(transport.buildRequest("GET", "http://www.test.url"));
    // Test DELETE.
    execute(transport.buildRequest("DELETE", "http://www.test.url"));
    // Test HEAD.
    execute(transport.buildRequest("HEAD", "http://www.test.url"));
    // Test PATCH.
    execute(transport.buildRequest("PATCH", "http://www.test.url"));
    // Test PUT.
    execute(transport.buildRequest("PUT", "http://www.test.url"));
    // Test POST.
    execute(transport.buildRequest("POST", "http://www.test.url"));
    // Test PATCH.
    execute(transport.buildRequest("PATCH", "http://www.test.url"));
  }

  @Test
  public void testNormalizedUrl() throws IOException {
    final HttpRequestHandler handler = new HttpRequestHandler() {
      @Override
      public void handle(
          ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
          throws HttpException, IOException {
        // Extract the request URI and convert to bytes
        byte[] responseData = request.getRequestUri().getBytes(StandardCharsets.UTF_8);

        // Set the response headers (status code and content length)
        response.setCode(HttpStatus.SC_OK);
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(responseData.length));

        // Set the response entity (body)
        ByteArrayEntity entity = new ByteArrayEntity(responseData, ContentType.TEXT_PLAIN);
        response.setEntity(entity);
      }
    };
    try (FakeServer server = new FakeServer(handler)) {
      HttpTransport transport = new ApacheHttp2Transport();
      GenericUrl testUrl = new GenericUrl("http://localhost/foo//bar");
      testUrl.setPort(server.getPort());
      com.google.api.client.http.HttpResponse response = transport.createRequestFactory()
          .buildGetRequest(testUrl)
          .execute();
      assertEquals(200, response.getStatusCode());
      assertEquals("/foo//bar", response.parseAsString());
    }
  }

  @Test
  public void testReadErrorStream() throws IOException {
    final HttpRequestHandler handler = new HttpRequestHandler() {
      @Override
      public void handle(
          ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
          throws HttpException, IOException {
        byte[] responseData = "Forbidden".getBytes(StandardCharsets.UTF_8);
        response.setCode(HttpStatus.SC_FORBIDDEN); // 403 Forbidden
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(responseData.length));
        ByteArrayEntity entity = new ByteArrayEntity(responseData, ContentType.TEXT_PLAIN);
        response.setEntity(entity);
      }
    };
    try (FakeServer server = new FakeServer(handler)) {
      HttpTransport transport = new ApacheHttp2Transport();
      GenericUrl testUrl = new GenericUrl("http://localhost/foo//bar");
      testUrl.setPort(server.getPort());
      com.google.api.client.http.HttpRequest getRequest = transport.createRequestFactory()
          .buildGetRequest(testUrl);
      getRequest.setThrowExceptionOnExecuteError(false);
      com.google.api.client.http.HttpResponse response = getRequest.execute();
      assertEquals(403, response.getStatusCode());
      assertEquals("Forbidden", response.parseAsString());
    }
  }

  @Test
  public void testReadErrorStream_withException() throws IOException {
    final HttpRequestHandler handler = new HttpRequestHandler() {
      @Override
      public void handle(
          ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
          throws HttpException, IOException {
        byte[] responseData = "Forbidden".getBytes(StandardCharsets.UTF_8);
        response.setCode(HttpStatus.SC_FORBIDDEN); // 403 Forbidden
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(responseData.length));
        ByteArrayEntity entity = new ByteArrayEntity(responseData, ContentType.TEXT_PLAIN);
        response.setEntity(entity);
      }
    };
    try (FakeServer server = new FakeServer(handler)) {
      HttpTransport transport = new ApacheHttp2Transport();
      GenericUrl testUrl = new GenericUrl("http://localhost/foo//bar");
      testUrl.setPort(server.getPort());
      com.google.api.client.http.HttpRequest getRequest = transport.createRequestFactory()
          .buildGetRequest(testUrl);
      try {
        getRequest.execute();
        Assert.fail();
      } catch (HttpResponseException ex) {
        assertEquals("Forbidden", ex.getContent());
      }
    }
  }

  private void execute(ApacheHttp2Request request) throws IOException {
    byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);
    request.setStreamingContent(new ByteArrayStreamingContent(bytes));
    request.setContentType("text/html");
    request.setContentLength(bytes.length);
    request.execute();
  }

  @Test
  public void testGzipResponse() throws IOException {
    final String originalContent = "hello world";
    final java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(baos)) {
      gzip.write(originalContent.getBytes(StandardCharsets.UTF_8));
    }
    final byte[] gzippedContent = baos.toByteArray();

    final HttpRequestHandler handler = new HttpRequestHandler() {
      @Override
      public void handle(
          ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
          throws HttpException, IOException {
        response.setCode(HttpStatus.SC_OK);
        response.setHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(gzippedContent.length));
        response.setHeader(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
        ByteArrayEntity entity = new ByteArrayEntity(gzippedContent, ContentType.TEXT_PLAIN);
        response.setEntity(entity);
      }
    };

    try (FakeServer server = new FakeServer(handler)) {
      ApacheHttp2Transport transport = new ApacheHttp2Transport();
      GenericUrl testUrl = new GenericUrl("http://localhost/foo");
      testUrl.setPort(server.getPort());

      // Execute the low-level request directly to accurately assert metadata
      // without Google's parsed HttpHeaders map mangling the payload lengths.
      ApacheHttp2Request request = transport.buildRequest("GET", testUrl.build());
      LowLevelHttpResponse response = request.execute();

      assertEquals(200, response.getStatusCode());
      assertEquals("text/plain; charset=UTF-8", response.getContentType());

      boolean wasAutoDecompressed = response.getContentEncoding() == null;
      if (wasAutoDecompressed) {
        assertEquals(originalContent.length(), response.getContentLength());
      } else {
        assertEquals("gzip", response.getContentEncoding());
        assertEquals(gzippedContent.length, response.getContentLength());
      }

      // Verify the low-level stream returns the exact expected payload based on
      // decompression state
      java.io.InputStream stream = response.getContent();
      byte[] resultBytes = com.google.common.io.ByteStreams.toByteArray(stream);

      if (wasAutoDecompressed) {
        assertEquals(originalContent, new String(resultBytes, StandardCharsets.UTF_8));
      } else {
        org.junit.Assert.assertArrayEquals(gzippedContent, resultBytes);
      }
    }
  }

  @Test
  public void testEmptyResponseWithHeaders() throws IOException {
    // Tests that a response with no actual body but headers does not throw NPE
    // in ApacheHttp2Response due to entity being null.
    final HttpRequestHandler handler = new HttpRequestHandler() {
      @Override
      public void handle(
          ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
          throws HttpException, IOException {
        response.setCode(HttpStatus.SC_NO_CONTENT);
        response.setHeader(HttpHeaders.CONTENT_LENGTH, "0");
        // Explicitly omitting the entity to simulate NO_CONTENT bodyless response
      }
    };

    try (FakeServer server = new FakeServer(handler)) {
      HttpTransport transport = new ApacheHttp2Transport();
      GenericUrl testUrl = new GenericUrl("http://localhost/empty");
      testUrl.setPort(server.getPort());
      com.google.api.client.http.HttpResponse response = transport.createRequestFactory()
          .buildGetRequest(testUrl)
          .execute();

      assertEquals(204, response.getStatusCode());
      assertEquals(0L, response.getHeaders().getContentLength().longValue());
    }
  }

  @Test
  public void testReadTimeout() throws Exception {
    final HttpRequestHandler handler = new HttpRequestHandler() {
      @Override
      public void handle(
          ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
          throws HttpException, IOException {
        try {
          Thread.sleep(1000L);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        response.setCode(HttpStatus.SC_OK);
      }
    };

    try (FakeServer server = new FakeServer(handler)) {
      HttpTransport transport = new ApacheHttp2Transport();
      GenericUrl testUrl = new GenericUrl("http://localhost/timeout");
      testUrl.setPort(server.getPort());
      com.google.api.client.http.HttpRequest request = transport.createRequestFactory()
          .buildGetRequest(testUrl);
      request.setReadTimeout(100);

      try {
        request.execute();
        Assert.fail("Expected IOException on read timeout");
      } catch (IOException e) {
        assertEquals("Stream exception in request", e.getMessage());
      } finally {
        transport.shutdown();
      }
    }
  }

  @Test(timeout = 10_000L)
  public void testUnauthorizedGetRequest() throws Exception {
    final AtomicReference<String> receivedMethod = new AtomicReference<>();
    final AtomicReference<String> receivedAuth = new AtomicReference<>();
    final HttpRequestHandler handler = new HttpRequestHandler() {
      @Override
      public void handle(
          ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) {
        receivedMethod.set(request.getMethod());
        Header authHeader = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null) {
          receivedAuth.set(authHeader.getValue());
        }
        byte[] responseData = "{\"result\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        response.setCode(HttpStatus.SC_OK);
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(responseData.length));
        response.setEntity(new ByteArrayEntity(responseData, ContentType.APPLICATION_JSON));
      }
    };

    try (FakeServer server = new FakeServer(handler)) {
      HttpTransport transport = new ApacheHttp2Transport();
      try {
        FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
            .setCredentials(new MockGoogleCredentials("test_token"))
            .setHttpTransport(transport)
            .build());
        String testUrl = "http://localhost:" + server.getPort() + "/get";
        HttpRequestInfo request = HttpRequestInfo.buildGetRequest(testUrl);
        IncomingHttpResponse response = createHttpClient(false, app).send(request);
        assertEquals(200, response.getStatusCode());
        assertEquals("{\"result\":\"ok\"}", response.getContent());
        assertEquals("GET", receivedMethod.get());
        assertNull(receivedAuth.get());
      } finally {
        transport.shutdown();
      }
    }
  }

  @Test(timeout = 10_000L)
  public void testUnauthorizedPostRequest() throws Exception {
    final AtomicReference<String> receivedMethod = new AtomicReference<>();
    final AtomicReference<String> receivedAuth = new AtomicReference<>();
    final AtomicReference<String> receivedContentType = new AtomicReference<>();
    final AtomicReference<String> receivedBody = new AtomicReference<>();
    final HttpRequestHandler handler = new HttpRequestHandler() {
      @Override
      public void handle(
          ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
          throws IOException {
        receivedMethod.set(request.getMethod());
        Header authHeader = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null) {
          receivedAuth.set(authHeader.getValue());
        }
        if (request.getEntity() != null) {
          receivedContentType.set(request.getEntity().getContentType());
          byte[] requestBytes = ByteStreams.toByteArray(request.getEntity().getContent());
          receivedBody.set(new String(requestBytes, StandardCharsets.UTF_8));
        }
        byte[] responseData = "{\"data\":\"{\\\"foo\\\":\\\"bar\\\"}\"}"
            .getBytes(StandardCharsets.UTF_8);
        response.setCode(HttpStatus.SC_OK);
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(responseData.length));
        response.setEntity(new ByteArrayEntity(responseData, ContentType.APPLICATION_JSON));
      }
    };

    try (FakeServer server = new FakeServer(handler)) {
      HttpTransport transport = new ApacheHttp2Transport();
      try {
        FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
            .setCredentials(new MockGoogleCredentials("test_token"))
            .setHttpTransport(transport)
            .build());
        String testUrl = "http://localhost:" + server.getPort() + "/post";
        HttpRequestInfo request = HttpRequestInfo.buildJsonPostRequest(testUrl, TEST_PAYLOAD);
        GenericData body = createHttpClient(false, app).sendAndParse(request, GenericData.class);
        assertEquals("{\"foo\":\"bar\"}", body.get("data"));
        assertEquals("POST", receivedMethod.get());
        assertNull(receivedAuth.get());
        assertNotNull(receivedContentType.get());
        assertTrue(receivedContentType.get().startsWith("application/json"));
        assertEquals("{\"foo\":\"bar\"}", receivedBody.get());
      } finally {
        transport.shutdown();
      }
    }
  }

  @Test(timeout = 10_000L)
  public void testAuthorizedPostRequest() throws Exception {
    final AtomicReference<String> receivedMethod = new AtomicReference<>();
    final AtomicReference<String> receivedAuth = new AtomicReference<>();
    final AtomicReference<String> receivedContentType = new AtomicReference<>();
    final AtomicReference<String> receivedBody = new AtomicReference<>();
    final HttpRequestHandler handler = new HttpRequestHandler() {
      @Override
      public void handle(
          ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
          throws IOException {
        receivedMethod.set(request.getMethod());
        Header authHeader = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null) {
          receivedAuth.set(authHeader.getValue());
        }
        if (request.getEntity() != null) {
          receivedContentType.set(request.getEntity().getContentType());
          byte[] requestBytes = ByteStreams.toByteArray(request.getEntity().getContent());
          receivedBody.set(new String(requestBytes, StandardCharsets.UTF_8));
        }
        byte[] responseData = "{\"result\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        response.setCode(HttpStatus.SC_OK);
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(responseData.length));
        response.setEntity(new ByteArrayEntity(responseData, ContentType.APPLICATION_JSON));
      }
    };

    try (FakeServer server = new FakeServer(handler)) {
      HttpTransport transport = new ApacheHttp2Transport();
      try {
        FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
            .setCredentials(new MockGoogleCredentials("test_token"))
            .setHttpTransport(transport)
            .build());
        String testUrl = "http://localhost:" + server.getPort() + "/post";
        HttpRequestInfo request = HttpRequestInfo.buildJsonPostRequest(testUrl, TEST_PAYLOAD);
        GenericData body = createHttpClient(true, app).sendAndParse(request, GenericData.class);
        assertEquals("ok", body.get("result"));
        assertEquals("POST", receivedMethod.get());
        assertEquals("Bearer test_token", receivedAuth.get());
        assertNotNull(receivedContentType.get());
        assertTrue(receivedContentType.get().startsWith("application/json"));
        assertEquals("{\"foo\":\"bar\"}", receivedBody.get());
      } finally {
        transport.shutdown();
      }
    }
  }

  @Test(timeout = 10_000L)
  public void testReadTimeoutAuthorizedGet() throws Exception {
    final CountDownLatch requestReceived = new CountDownLatch(1);
    final CountDownLatch releaseResponse = new CountDownLatch(1);
    final AtomicReference<String> receivedAuth = new AtomicReference<>();
    final HttpRequestHandler handler = new HttpRequestHandler() {
      @Override
      public void handle(
          ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) {
        Header authHeader = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null) {
          receivedAuth.set(authHeader.getValue());
        }
        requestReceived.countDown();
        try {
          releaseResponse.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        response.setCode(HttpStatus.SC_OK);
      }
    };

    try (FakeServer server = new FakeServer(handler)) {
      HttpTransport transport = new ApacheHttp2Transport();
      try {
        FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
            .setCredentials(new MockGoogleCredentials("test_token"))
            .setReadTimeout(100)
            .setHttpTransport(transport)
            .build());
        String testUrl = "http://localhost:" + server.getPort() + "/delay";
        HttpRequestInfo request = HttpRequestInfo.buildGetRequest(testUrl);

        try {
          createHttpClient(true, app).send(request);
          fail("No exception thrown for HTTP error response");
        } catch (FirebaseException e) {
          assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
          assertEquals("IO error: Stream exception in request", e.getMessage());
          assertNull(e.getHttpResponse());
        }
        assertTrue(requestReceived.await(1, TimeUnit.SECONDS));
        assertEquals("Bearer test_token", receivedAuth.get());
      } finally {
        releaseResponse.countDown();
        transport.shutdown();
      }
    }
  }

  @Test(timeout = 10_000L)
  public void testReadTimeoutAuthorizedPost() throws Exception {
    final CountDownLatch requestReceived = new CountDownLatch(1);
    final CountDownLatch releaseResponse = new CountDownLatch(1);
    final AtomicReference<String> receivedAuth = new AtomicReference<>();
    final AtomicReference<String> receivedBody = new AtomicReference<>();
    final HttpRequestHandler handler = new HttpRequestHandler() {
      @Override
      public void handle(
          ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
          throws IOException {
        Header authHeader = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null) {
          receivedAuth.set(authHeader.getValue());
        }
        if (request.getEntity() != null) {
          byte[] requestBytes = ByteStreams.toByteArray(request.getEntity().getContent());
          receivedBody.set(new String(requestBytes, StandardCharsets.UTF_8));
        }
        requestReceived.countDown();
        try {
          releaseResponse.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        response.setCode(HttpStatus.SC_OK);
      }
    };

    try (FakeServer server = new FakeServer(handler)) {
      HttpTransport transport = new ApacheHttp2Transport();
      try {
        FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
            .setCredentials(new MockGoogleCredentials("test_token"))
            .setReadTimeout(100)
            .setHttpTransport(transport)
            .build());
        String testUrl = "http://localhost:" + server.getPort() + "/delay";
        HttpRequestInfo request = HttpRequestInfo.buildJsonPostRequest(testUrl, TEST_PAYLOAD);

        try {
          createHttpClient(true, app).send(request);
          fail("No exception thrown for HTTP error response");
        } catch (FirebaseException e) {
          assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
          assertEquals("IO error: Stream exception in request", e.getMessage());
          assertNull(e.getHttpResponse());
        }
        assertTrue(requestReceived.await(1, TimeUnit.SECONDS));
        assertEquals("Bearer test_token", receivedAuth.get());
        assertEquals("{\"foo\":\"bar\"}", receivedBody.get());
      } finally {
        releaseResponse.countDown();
        transport.shutdown();
      }
    }
  }

  @Test(timeout = 10_000L)
  public void testWriteTimeoutAuthorizedGet() throws IOException {
    final AtomicReference<Future<?>> responseFutureRef = new AtomicReference<>();
    CloseableHttpAsyncClient stalledClient = new MockApacheHttp2AsyncClient() {
      @Override
      protected <T> Future<T> doExecute(
          HttpHost target,
          AsyncRequestProducer requestProducer,
          AsyncResponseConsumer<T> responseConsumer,
          HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
          HttpContext context,
          FutureCallback<T> callback) {
        CompletableFuture<T> future = new CompletableFuture<>();
        responseFutureRef.set(future);
        return future;
      }
    };

    HttpTransport transport = new ApacheHttp2Transport(stalledClient);
    try {
      FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
          .setCredentials(new MockGoogleCredentials("test_token"))
          .setWriteTimeout(100)
          .setHttpTransport(transport)
          .build());
      HttpRequestInfo request = HttpRequestInfo.buildGetRequest("https://example.com/get");

      try {
        createHttpClient(true, app).send(request);
        fail("No exception thrown for HTTP error response");
      } catch (FirebaseException e) {
        assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
        assertEquals("IO error: Write Timeout", e.getMessage());
        assertNull(e.getHttpResponse());
        assertTrue(e.getCause() instanceof IOException);
        assertTrue(e.getCause().getCause() instanceof TimeoutException);
      }
      assertNotNull(responseFutureRef.get());
      assertTrue(responseFutureRef.get().isCancelled());
    } finally {
      transport.shutdown();
    }
  }

  @Test(timeout = 10_000L)
  public void testWriteTimeoutAuthorizedPost() throws IOException {
    final AtomicInteger bytesOffered = new AtomicInteger();
    final AtomicReference<Future<?>> responseFutureRef = new AtomicReference<>();
    CloseableHttpAsyncClient backpressuredClient = new MockApacheHttp2AsyncClient() {
      @Override
      protected <T> Future<T> doExecute(
          HttpHost target,
          AsyncRequestProducer requestProducer,
          AsyncResponseConsumer<T> responseConsumer,
          HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
          HttpContext context,
          FutureCallback<T> callback) {
        try {
          requestProducer.produce(new DataStreamChannel() {
            @Override
            public void requestOutput() {}

            @Override
            public int write(ByteBuffer src) {
              bytesOffered.set(src.remaining());
              return 0;
            }

            @Override
            public void endStream() {}

            @Override
            public void endStream(List<? extends Header> trailers) {}
          });
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        responseFutureRef.set(future);
        return future;
      }
    };

    HttpTransport transport = new ApacheHttp2Transport(backpressuredClient);
    try {
      FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
          .setCredentials(new MockGoogleCredentials("test_token"))
          .setWriteTimeout(100)
          .setHttpTransport(transport)
          .build());
      HttpRequestInfo request = HttpRequestInfo.buildJsonPostRequest(
          "https://example.com/post", TEST_PAYLOAD);

      try {
        createHttpClient(true, app).send(request);
        fail("No exception thrown for HTTP error response");
      } catch (FirebaseException e) {
        assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
        assertEquals("IO error: Write Timeout", e.getMessage());
        assertNull(e.getHttpResponse());
        assertTrue(e.getCause() instanceof IOException);
        assertTrue(e.getCause().getCause() instanceof TimeoutException);
      }
      assertEquals("{\"foo\":\"bar\"}".length(), bytesOffered.get());
      assertNotNull(responseFutureRef.get());
      assertTrue(responseFutureRef.get().isCancelled());
    } finally {
      transport.shutdown();
    }
  }

  private static ErrorHandlingHttpClient<FirebaseException> createHttpClient(
      boolean authorized, FirebaseApp app) {
    HttpRequestFactory requestFactory = authorized
        ? ApiClientUtils.newAuthorizedRequestFactory(app)
        : ApiClientUtils.newUnauthorizedRequestFactory(app);
    JsonFactory jsonFactory = ApiClientUtils.getDefaultJsonFactory();
    return new ErrorHandlingHttpClient<>(requestFactory, jsonFactory, new TestHttpErrorHandler());
  }

  private static class TestHttpErrorHandler implements HttpErrorHandler<FirebaseException> {
    @Override
    public FirebaseException handleIOException(IOException e) {
      return new FirebaseException(ErrorCode.UNKNOWN, "IO error: " + e.getMessage(), e);
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
      server = ServerBootstrap.bootstrap()
          .setListenerPort(0)
          .setHttpProcessor(HttpProcessorBuilder.create().build())
          .setRequestRouter(mapper)
          .create();
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
