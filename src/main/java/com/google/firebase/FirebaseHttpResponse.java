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

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

public final class FirebaseHttpResponse {

  private final int statusCode;
  private final String content;
  private final Map<String, Object> headers;
  private final FirebaseHttpRequest request;

  public FirebaseHttpResponse(HttpResponse response, String content) {
    this.statusCode = response.getStatusCode();
    this.content = content;
    this.headers = ImmutableMap.copyOf(response.getHeaders());
    this.request = new FirebaseHttpRequest(response.getRequest());
  }

  public FirebaseHttpResponse(HttpResponseException e, HttpRequest request) {
    this(e, new FirebaseHttpRequest(request));
  }

  public FirebaseHttpResponse(HttpResponseException e, FirebaseHttpRequest request) {
    this.statusCode = e.getStatusCode();
    this.content = e.getContent();
    this.headers = ImmutableMap.copyOf(e.getHeaders());
    this.request = request;
  }

  public int getStatusCode() {
    return this.statusCode;
  }

  public String getContent() {
    return this.content;
  }

  public Map<String, Object> getHeaders() {
    return this.headers;
  }

  public FirebaseHttpRequest getRequest() {
    return request;
  }
}
