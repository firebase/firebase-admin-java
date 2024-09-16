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

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http2.H2StreamResetException;
import org.apache.hc.core5.util.Timeout;

final class ApacheHttp2Request extends LowLevelHttpRequest {
  private final CloseableHttpAsyncClient httpAsyncClient;
  private final SimpleRequestBuilder requestBuilder;
  private SimpleHttpRequest request;
  private final RequestConfig.Builder requestConfig;
  private int writeTimeout;
  private ApacheHttp2AsyncEntityProducer entityProducer;

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
    entityProducer = new ApacheHttp2AsyncEntityProducer(this, writeFuture);

    // Execute
    final Future<SimpleHttpResponse> responseFuture = httpAsyncClient.execute(
        new BasicRequestProducer(request, entityProducer),
        SimpleResponseConsumer.create(),
        new FutureCallback<SimpleHttpResponse>() {
          @Override
          public void completed(final SimpleHttpResponse response) {
          }

          @Override
          public void failed(final Exception exception) {
          }

          @Override
          public void cancelled() {
          }
        });

    // Wait for write
    try {
      if (writeTimeout != 0) {
        writeFuture.get(writeTimeout, TimeUnit.MILLISECONDS);
      }
    } catch (TimeoutException e) {
      throw new IOException("Write Timeout", e.getCause());
    } catch (Exception e) {
      throw new IOException("Exception in write", e.getCause());
    }

    // Wait for response
    try {
      final SimpleHttpResponse response = responseFuture.get();
      return new ApacheHttp2Response(response);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ConnectTimeoutException) {
        throw new IOException("Connection Timeout", e.getCause());
      } else if (e.getCause() instanceof HttpHostConnectException) {
        throw new IOException("Connection exception in request", e.getCause());
      } else if (e.getCause() instanceof H2StreamResetException) {
        throw new IOException("Stream exception in request", e.getCause());
      } else {
        throw new IOException("Unknown exception in request", e);
      }
    } catch (InterruptedException e) {
      throw new IOException("Request Interrupted", e);
    } catch (CancellationException e) {
      throw new IOException("Request Cancelled", e);
    }
  }

  @VisibleForTesting
  ApacheHttp2AsyncEntityProducer getEntityProducer() {
    return entityProducer;
  }
}
