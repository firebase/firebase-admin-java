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

import com.google.api.client.http.HttpTransport;

import java.io.IOException;
import java.net.ProxySelector;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.async.HttpAsyncClient;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;

public final class ApacheHttp2Transport extends HttpTransport {

  private final CloseableHttpAsyncClient httpAsyncClient;
  private final boolean isMtls;

  public ApacheHttp2Transport() {
    this(newDefaultHttpAsyncClient(), false);
  }

  public ApacheHttp2Transport(CloseableHttpAsyncClient httpAsyncClient) {
    this(httpAsyncClient, false);
  }

  public ApacheHttp2Transport(CloseableHttpAsyncClient httpAsyncClient, boolean isMtls) {
    this.httpAsyncClient = httpAsyncClient;
    this.isMtls = isMtls;
    
    httpAsyncClient.start();
  }

  public static CloseableHttpAsyncClient newDefaultHttpAsyncClient() {
    return defaultHttpAsyncClientBuilder().build();
  }

  public static HttpAsyncClientBuilder defaultHttpAsyncClientBuilder() {
    PoolingAsyncClientConnectionManager connectionManager = 
        new PoolingAsyncClientConnectionManager();
    connectionManager.setMaxTotal(100);
    connectionManager.setDefaultMaxPerRoute(100);
    connectionManager.closeIdle(TimeValue.of(30, TimeUnit.SECONDS));
    connectionManager.setDefaultTlsConfig(
        TlsConfig.custom().setVersionPolicy(HttpVersionPolicy.NEGOTIATE).build());

    return HttpAsyncClientBuilder.create()
      .setH2Config(H2Config.DEFAULT)
      .setHttp1Config(Http1Config.DEFAULT)
      .setConnectionManager(connectionManager)
      .setRoutePlanner(new SystemDefaultRoutePlanner(ProxySelector.getDefault()))
      .disableRedirectHandling()
      .disableAutomaticRetries();
  }

  @Override
  public boolean supportsMethod(String method) {
    return true;
  }

  @Override
  protected ApacheHttp2Request buildRequest(String method, String url) {
    SimpleRequestBuilder requestBuilder = SimpleRequestBuilder.create(method).setUri(url);
    return new ApacheHttp2Request(httpAsyncClient, requestBuilder);
  }

  @Override
  public void shutdown() throws IOException {
    httpAsyncClient.close(CloseMode.GRACEFUL);
  }

  public HttpAsyncClient getHttpClient() {
    return httpAsyncClient;
  }

  @Override
  public boolean isMtls() {
    return isMtls;
  }
}
