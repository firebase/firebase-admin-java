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
import static org.junit.Assert.assertTrue;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.util.ByteArrayStreamingContent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

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
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.support.BasicHttpServerRequestHandler;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.junit.Assert;
import org.junit.Test;

public class ApacheHttp2TransportTest {
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
