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

package com.google.firebase.auth.internal;

import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.json.JsonFactory;
import com.google.common.collect.ImmutableSortedSet;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.internal.ErrorHandlingHttpClient;
import com.google.firebase.internal.HttpRequestInfo;
import com.google.firebase.internal.SdkUtils;
import java.util.Map;
import java.util.Set;

/**
 * Provides a convenient API for making REST calls to the Firebase Auth backend servers.
 */
public final class AuthHttpClient {

  private static final String CLIENT_VERSION_HEADER = "X-Client-Version";

  private static final String CLIENT_VERSION = "Java/Admin/" + SdkUtils.getVersion();

  private final ErrorHandlingHttpClient<FirebaseAuthException> httpClient;
  private final JsonFactory jsonFactory;

  public AuthHttpClient(JsonFactory jsonFactory, HttpRequestFactory requestFactory) {
    AuthErrorHandler authErrorHandler = new AuthErrorHandler(jsonFactory);
    this.httpClient = new ErrorHandlingHttpClient<>(requestFactory, jsonFactory, authErrorHandler);
    this.jsonFactory = jsonFactory;
  }

  public static Set<String> generateMask(Map<String, Object> properties) {
    ImmutableSortedSet.Builder<String> maskBuilder = ImmutableSortedSet.naturalOrder();
    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      if (entry.getValue() instanceof Map) {
        Set<String> childMask = generateMask((Map<String, Object>) entry.getValue());
        for (String childProperty : childMask) {
          maskBuilder.add(entry.getKey() + "." + childProperty);
        }
      } else {
        maskBuilder.add(entry.getKey());
      }
    }
    return maskBuilder.build();
  }

  public void setInterceptor(HttpResponseInterceptor interceptor) {
    this.httpClient.setInterceptor(interceptor);
  }

  public <T> T sendRequest(HttpRequestInfo request, Class<T> clazz) throws FirebaseAuthException {
    IncomingHttpResponse response = this.sendRequest(request);
    return this.parse(response, clazz);
  }

  public IncomingHttpResponse sendRequest(HttpRequestInfo request) throws FirebaseAuthException {
    request.addHeader(CLIENT_VERSION_HEADER, CLIENT_VERSION);
    return httpClient.send(request);
  }

  public <T> T parse(IncomingHttpResponse response, Class<T> clazz) throws FirebaseAuthException {
    return httpClient.parse(response, clazz);
  }
}
