/*
 * Copyright 2020 Google Inc.
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

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Internal API for configuring outgoing HTTP requests. To be used with the
 * {@link ErrorHandlingHttpClient} class.
 */
public final class HttpRequestInfo {

  private final String method;
  private final GenericUrl url;
  private final HttpContent content;
  private final Object jsonContent;
  private final Map<String, String> headers = new HashMap<>();

  private HttpRequestInfo(String method, GenericUrl url, HttpContent content, Object jsonContent) {
    checkArgument(!Strings.isNullOrEmpty(method), "method must not be null");
    this.method = method;
    this.url = checkNotNull(url, "url must not be null");
    this.content = content;
    this.jsonContent = jsonContent;
  }

  public HttpRequestInfo addHeader(String name, String value) {
    this.headers.put(name, value);
    return this;
  }

  public HttpRequestInfo addAllHeaders(Map<String, String> headers) {
    this.headers.putAll(headers);
    return this;
  }

  public HttpRequestInfo addParameter(String name, Object value) {
    this.url.put(name, value);
    return this;
  }

  public HttpRequestInfo addAllParameters(Map<String, Object> params) {
    this.url.putAll(params);
    return this;
  }

  public static HttpRequestInfo buildGetRequest(String url) {
    return buildRequest(HttpMethods.GET, url, null);
  }

  public static HttpRequestInfo buildDeleteRequest(String url) {
    return buildRequest(HttpMethods.DELETE, url, null);
  }

  public static HttpRequestInfo buildRequest(
      String method, String url, @Nullable HttpContent content) {
    return new HttpRequestInfo(method, new GenericUrl(url), content, null);
  }

  public static HttpRequestInfo buildJsonPostRequest(String url, @Nullable Object content) {
    return buildJsonRequest(HttpMethods.POST, url, content);
  }

  public static HttpRequestInfo buildJsonPatchRequest(String url, @Nullable Object content) {
    return buildJsonRequest(HttpMethods.PATCH, url, content);
  }

  public static HttpRequestInfo buildJsonRequest(
      String method, String url, @Nullable Object content) {
    return new HttpRequestInfo(method, new GenericUrl(url), null, content);
  }

  HttpRequest newHttpRequest(
      HttpRequestFactory factory, JsonFactory jsonFactory) throws IOException {
    HttpRequest request;
    HttpContent httpContent = getContent(jsonFactory);
    if (factory.getTransport().supportsMethod(method)) {
      request = factory.buildRequest(method, url, httpContent);
    } else {
      // Some HttpTransport implementations (notably NetHttpTransport) don't support new methods
      // like PATCH. We try to emulate such requests over POST by setting the method override
      // header, which is recognized by most Google backend APIs.
      request = factory.buildPostRequest(url, httpContent);
      request.getHeaders().set("X-HTTP-Method-Override", method);
    }

    for (Map.Entry<String, String> entry : headers.entrySet()) {
      request.getHeaders().set(entry.getKey(), entry.getValue());
    }

    return request;
  }

  private HttpContent getContent(JsonFactory jsonFactory) {
    if (content != null) {
      return content;
    }

    if (jsonContent != null) {
      return new JsonHttpContent(jsonFactory, jsonContent);
    }

    return null;
  }
}
