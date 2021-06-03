/*
 * Copyright 2019 Google Inc.
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

import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

public final class FirebaseHttpRequest {

  private final String method;
  private final String url;
  private final HttpContent content;
  private final Map<String, Object> headers;

  public FirebaseHttpRequest(String method, String url) {
    this.method = method;
    this.url = url;
    this.content = null;
    this.headers = ImmutableMap.of();
  }

  FirebaseHttpRequest(HttpRequest request) {
    this.method = request.getRequestMethod();
    this.url = request.getUrl().toString();
    this.content = request.getContent();
    this.headers = ImmutableMap.copyOf(request.getHeaders());
  }

  public String getMethod() {
    return method;
  }

  public String getUrl() {
    return url;
  }

  public HttpContent getContent() {
    return content;
  }

  public Map<String, Object> getHeaders() {
    return headers;
  }
}
