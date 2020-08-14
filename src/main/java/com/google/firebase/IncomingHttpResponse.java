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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.database.annotations.Nullable;
import java.util.Map;

/**
 * Contains information that describes an HTTP response received by the SDK.
 */
public final class IncomingHttpResponse {

  private final int statusCode;
  private final String content;
  private final Map<String, Object> headers;
  private final OutgoingHttpRequest request;

  /**
   * Creates an {@code IncomingHttpResponse} from a successful response and the content read
   * from it. The caller is expected to read the content from the response, and handle any errors
   * that may occur while reading.
   *
   * @param response A successful response.
   * @param content Content read from the response.
   */
  public IncomingHttpResponse(HttpResponse response, @Nullable String content) {
    checkNotNull(response, "response must not be null");
    this.statusCode = response.getStatusCode();
    this.content = content;
    this.headers = ImmutableMap.copyOf(response.getHeaders());
    this.request = new OutgoingHttpRequest(response.getRequest());
  }

  /**
   * Creates an {@code IncomingHttpResponse} from an HTTP error response.
   *
   * @param e The exception representing the HTTP error response.
   * @param request The request that resulted in the error.
   */
  public IncomingHttpResponse(HttpResponseException e, HttpRequest request) {
    this(e, new OutgoingHttpRequest(request));
  }

  /**
   * Creates an {@code IncomingHttpResponse} from an HTTP error response.
   *
   * @param e The exception representing the HTTP error response.
   * @param request The request that resulted in the error.
   */
  public IncomingHttpResponse(HttpResponseException e, OutgoingHttpRequest request) {
    checkNotNull(e, "exception must not be null");
    this.statusCode = e.getStatusCode();
    this.content = e.getContent();
    this.headers = ImmutableMap.copyOf(e.getHeaders());
    this.request = checkNotNull(request, "request must not be null");
  }

  /**
   * Returns the status code of the response.
   *
   * @return An HTTP status code (e.g. 500).
   */
  public int getStatusCode() {
    return this.statusCode;
  }

  /**
   * Returns the content of the response as a string.
   *
   * @return HTTP content or null.
   */
  @Nullable
  public String getContent() {
    return this.content;
  }

  /**
   * Returns the headers set on the response.
   *
   * @return An immutable map of headers (possibly empty).
   */
  public Map<String, Object> getHeaders() {
    return this.headers;
  }

  /**
   * Returns the request that resulted in this response.
   *
   * @return An HTTP request.
   */
  public OutgoingHttpRequest getRequest() {
    return request;
  }
}
