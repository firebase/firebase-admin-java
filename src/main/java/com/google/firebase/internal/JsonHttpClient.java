/*
 * Copyright 2017 Google Inc.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpExecuteInterceptor;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.common.base.Strings;
import java.io.IOException;

public class JsonHttpClient {

  private final String urlPrefix;
  private final HttpTransport transport;
  private final HttpExecuteInterceptor interceptor;
  private final JsonFactory jsonFactory;

  private JsonHttpClient(Builder builder) {
    checkArgument(!Strings.isNullOrEmpty(builder.host), "Host must not be null or empty");
    checkArgument(builder.port > 0  && builder.port <= 65535,
        "Port number must be in the interval (0,65535]");
    // We allow empty paths, but not null
    checkArgument(builder.path != null, "Path must not be null");
    this.urlPrefix = String.format("%s://%s:%d/%s", builder.secure ? "https" : "http", builder.host,
        builder.port, builder.path);
    this.transport = checkNotNull(builder.transport, "HttpTransport must not be null");
    this.jsonFactory = checkNotNull(builder.jsonFactory, "JsonFactory must not be null");
    this.interceptor = builder.interceptor;
  }

  public <T> T post(String path, final String token, Object payload,
                    Class<T> output) throws IOException {
    // Path can be empty, but not null
    checkNotNull(path, "Path must not be null");
    HttpRequestFactory requestFactory =
        transport.createRequestFactory(new HttpRequestInitializer() {
          @Override
          public void initialize(HttpRequest request) {
            init(request, token);
          }
        });
    HttpContent content = new JsonHttpContent(jsonFactory, payload);
    HttpRequest request = requestFactory.buildPostRequest(getUrl(path), content);
    return execute(request, output);
  }

  private void init(HttpRequest request, String token) {
    request.setParser(new JsonObjectParser(jsonFactory));
    if (!Strings.isNullOrEmpty(token)) {
      request.getHeaders().setAuthorization("Bearer " + token);
    }
    if (interceptor != null) {
      request.setInterceptor(interceptor);
    }
  }

  private <T> T execute(HttpRequest request, Class<T> outputClass) throws IOException {
    HttpResponse response = request.execute();
    response.setLoggingEnabled(true);
    try {
      return response.parseAs(outputClass);
    } finally {
      response.disconnect();
    }
  }

  private GenericUrl getUrl(String path) {
    return new GenericUrl(urlPrefix + path);
  }

  public static class Builder {
    private String host;
    private int port;
    private String path = "";
    private boolean secure;
    private HttpTransport transport = Utils.getDefaultTransport();
    private JsonFactory jsonFactory = Utils.getDefaultJsonFactory();
    private HttpExecuteInterceptor interceptor;

    public Builder setHost(String host) {
      this.host = host;
      return this;
    }

    public Builder setPort(int port) {
      this.port = port;
      return this;
    }

    public Builder setPath(String path) {
      this.path = path;
      return this;
    }

    public Builder setSecure(boolean secure) {
      this.secure = secure;
      return this;
    }

    public Builder setTransport(HttpTransport transport) {
      this.transport = transport;
      return this;
    }

    public Builder setJsonFactory(JsonFactory jsonFactory) {
      this.jsonFactory = jsonFactory;
      return this;
    }

    public Builder setInterceptor(HttpExecuteInterceptor interceptor) {
      this.interceptor = interceptor;
      return this;
    }

    public JsonHttpClient build() {
      return new JsonHttpClient(this);
    }
  }
}
