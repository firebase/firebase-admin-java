package com.google.firebase.internal;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.util.Timeout;

final class ApacheHttp2Request extends LowLevelHttpRequest {
  private final CloseableHttpAsyncClient httpAsyncClient;
  private final SimpleRequestBuilder requestBuilder;
  private SimpleHttpRequest request;
  private final RequestConfig.Builder requestConfig;
  private int writeTimeout;

  ApacheHttp2Request(
      CloseableHttpAsyncClient httpAsyncClient, SimpleRequestBuilder requestBuilder) {
    this.httpAsyncClient = httpAsyncClient;
    this.requestBuilder = requestBuilder;
    this.writeTimeout = 0;

    this.requestConfig = RequestConfig.custom()
      .setRedirectsEnabled(false);
  }

  @Override
  public void addHeader(String name, String value) {
    requestBuilder.addHeader(name, value);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void setTimeout(int connectionTimeout, int readTimeout) throws IOException {
    requestConfig
      .setConnectTimeout(Timeout.ofMilliseconds(connectionTimeout))
      .setResponseTimeout(Timeout.ofMilliseconds(readTimeout));
  }

  @Override
  public void setWriteTimeout(int writeTimeout) throws IOException {
    this.writeTimeout = writeTimeout;
  }

  @Override
  public LowLevelHttpResponse execute() throws IOException {
    // Set request configs
    requestBuilder.setRequestConfig(requestConfig.build());

    // Build request
    request = requestBuilder.build();

    // Make Producer
    CompletableFuture<Void> writeFuture = new CompletableFuture<>();
    ApacheHttp2AsyncEntityProducer entityProducer = 
        new ApacheHttp2AsyncEntityProducer(this, writeFuture);

    // Execute
    final CompletableFuture<SimpleHttpResponse> responseFuture = new CompletableFuture<>();
    try {
      httpAsyncClient.execute(
          new BasicRequestProducer(request, entityProducer),
          SimpleResponseConsumer.create(),
          new FutureCallback<SimpleHttpResponse>() {
            @Override
            public void completed(final SimpleHttpResponse response) {
              responseFuture.complete(response);
            }

            @Override
            public void failed(final Exception exception) {
              responseFuture.completeExceptionally(exception);
            }

            @Override
            public void cancelled() {
              responseFuture.cancel(false);
            }
          });

      if (writeTimeout != 0) {
        writeFuture.get(writeTimeout, TimeUnit.MILLISECONDS);
      }

      final SimpleHttpResponse response = responseFuture.get();
      return new ApacheHttp2Response(request, response);
    } catch (InterruptedException e) {
      throw new IOException("Request Interrupted", e);
    } catch (ExecutionException e) {
      throw new IOException("Exception in request", e);
    } catch (TimeoutException e) {
      throw new IOException("Timed out", e);
    }
  }
}
