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

package com.google.firebase.internal;

import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.common.collect.ImmutableList;
import com.google.firebase.FirebaseApp;

import java.io.IOException;

/**
 * A set of shared utilities for using the Google API client.
 */
public class ApiClientUtils {

  static final RetryConfig DEFAULT_RETRY_CONFIG = RetryConfig.builder()
      .setMaxRetries(4)
      .setRetryStatusCodes(ImmutableList.of(500, 503))
      .setMaxIntervalMillis(60 * 1000)
      .build();

  private ApiClientUtils() { }

  /**
   * Creates a new {@code HttpRequestFactory} which provides authorization (OAuth2), timeouts and
   * automatic retries.
   *
   * @param app {@link FirebaseApp} from which to obtain authorization credentials.
   * @return A new {@code HttpRequestFactory} instance.
   */
  public static HttpRequestFactory newAuthorizedRequestFactory(FirebaseApp app) {
    return newAuthorizedRequestFactory(app, DEFAULT_RETRY_CONFIG);
  }

  /**
   * Creates a new {@code HttpRequestFactory} which provides authorization (OAuth2), timeouts and
   * automatic retries.
   *
   * @param app {@link FirebaseApp} from which to obtain authorization credentials.
   * @param retryConfig {@link RetryConfig} instance or null to disable retries.
   * @return A new {@code HttpRequestFactory} instance.
   */
  public static HttpRequestFactory newAuthorizedRequestFactory(
      FirebaseApp app, @Nullable RetryConfig retryConfig) {
    HttpTransport transport = app.getOptions().getHttpTransport();
    return transport.createRequestFactory(new FirebaseRequestInitializer(app, retryConfig));
  }

  public static HttpRequestFactory newUnauthorizedRequestFactory(FirebaseApp app) {
    HttpTransport transport = app.getOptions().getHttpTransport();
    return transport.createRequestFactory();
  }

  public static void disconnectQuietly(HttpResponse response) {
    if (response != null) {
      try {
        response.disconnect();
      } catch (IOException ignored) {
        // ignored
      }
    }
  }
}
