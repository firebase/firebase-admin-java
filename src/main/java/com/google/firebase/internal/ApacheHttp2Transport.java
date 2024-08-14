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
import org.apache.hc.core5.util.TimeValue;

public final class ApacheHttp2Transport extends HttpTransport {

  public final CloseableHttpAsyncClient httpAsyncClient;

  public ApacheHttp2Transport() {
    this(newDefaultHttpAsyncClient());
  }

  public ApacheHttp2Transport(CloseableHttpAsyncClient httpAsyncClient) {
    this.httpAsyncClient = httpAsyncClient;
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
    httpAsyncClient.close();
  }

  public HttpAsyncClient getHttpClient() {
    return httpAsyncClient;
  }
}
