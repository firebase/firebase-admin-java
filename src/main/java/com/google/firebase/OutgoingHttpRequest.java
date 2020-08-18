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

package com.google.firebase;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.internal.Nullable;
import java.util.Map;

/**
 * Contains the information that describe an HTTP request made by the SDK.
 */
public final class OutgoingHttpRequest {

  private final String method;
  private final String url;
  private final HttpContent content;
  private final Map<String, Object> headers;

  /**
   * Creates an {@code OutgoingHttpRequest} from the HTTP method and URL.
   *
   * @param method HTTP method name.
   * @param url Target HTTP URL of the request.
   */
  public OutgoingHttpRequest(String method, String url) {
    checkArgument(!Strings.isNullOrEmpty(method), "method must not be null or empty");
    checkArgument(!Strings.isNullOrEmpty(url), "url must not be empty");
    this.method = method;
    this.url = url;
    this.content = null;
    this.headers = ImmutableMap.of();
  }

  OutgoingHttpRequest(HttpRequest request) {
    checkNotNull(request, "request must not be null");
    this.method = request.getRequestMethod();
    this.url = request.getUrl().toString();
    this.content = request.getContent();
    this.headers = ImmutableMap.copyOf(request.getHeaders());
  }

  /**
   * Returns the HTTP method of the request.
   *
   * @return An HTTP method string (e.g. GET).
   */
  public String getMethod() {
    return method;
  }

  /**
   * Returns the URL of the request.
   *
   * @return An absolute HTTP URL.
   */
  public String getUrl() {
    return url;
  }

  /**
   * Returns any content that was sent with the request.
   *
   * @return HTTP content or null.
   */
  @Nullable
  public HttpContent getContent() {
    return content;
  }

  /**
   * Returns the headers set on the request.
   *
   * @return An immutable map of headers (possibly empty).
   */
  public Map<String, Object> getHeaders() {
    return headers;
  }
}
