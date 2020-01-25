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
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;

import com.google.firebase.FirebaseException;
import java.io.IOException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

/**
 * A set of shared utilities for using the Google API client.
 */
public class ApiClientUtils {

  private static final RetryConfig DEFAULT_RETRY_CONFIG = RetryConfig.builder()
      .setMaxRetries(4)
      .setRetryStatusCodes(ImmutableList.of(500, 503))
      .setMaxIntervalMillis(60 * 1000)
      .build();

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
   * @param retryConfig {@link RetryConfig} which specifies how and when to retry errors.
   * @return A new {@code HttpRequestFactory} instance.
   */
  public static HttpRequestFactory newAuthorizedRequestFactory(
      FirebaseApp app, @Nullable RetryConfig retryConfig) {
    HttpTransport transport = app.getOptions().getHttpTransport();
    return transport.createRequestFactory(
        new FirebaseRequestInitializer(app, retryConfig));
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

  public static FirebaseException newFirebaseException(IOException e) {
    ErrorCode code = ErrorCode.UNKNOWN;
    String message = "Unknown error while making a remote service call" ;
    if (isInstance(e, SocketTimeoutException.class)) {
      code = ErrorCode.DEADLINE_EXCEEDED;
      message = "Timed out while making an API call";
    }

    if (isInstance(e, UnknownHostException.class) || isInstance(e, NoRouteToHostException.class)) {
      code = ErrorCode.UNAVAILABLE;
      message = "Failed to establish a connection";
    }

    return new FirebaseException(code, message + ": " + e.getMessage(), null, e);
  }

  /**
   * Checks if the given exception stack t contains an instance of type.
   */
  private static <T> boolean isInstance(IOException t, Class<T> type) {
    Throwable current = t;
    Set<Throwable> chain = new HashSet<>();
    while (current != null) {
      if (!chain.add(current)) {
        break;
      }

      if (type.isInstance(current)) {
        return true;
      }

      current = current.getCause();
    }

    return false;
  }
}
