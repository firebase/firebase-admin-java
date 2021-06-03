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

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.common.collect.ImmutableList;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;

import com.google.firebase.FirebaseException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

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
  <<<<<<< hkj-error-handling
   * @param retryConfig {@link RetryConfig} which specifies how and when to retry errors.
  =======
   * @param retryConfig {@link RetryConfig} instance or null to disable retries.
  >>>>>>> master
   * @return A new {@code HttpRequestFactory} instance.
   */
  public static HttpRequestFactory newAuthorizedRequestFactory(
      FirebaseApp app, @Nullable RetryConfig retryConfig) {
    HttpTransport transport = app.getOptions().getHttpTransport();
  <<<<<<< hkj-error-handling
    return transport.createRequestFactory(
        new FirebaseRequestInitializer(app, retryConfig));
  =======
    return transport.createRequestFactory(new FirebaseRequestInitializer(app, retryConfig));
  >>>>>>> master
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

  <<<<<<< hkj-error-handling
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
  =======
  public static JsonFactory getDefaultJsonFactory() {
    // Force using the Jackson2 parser for this project for now. Eventually we should switch
    // to Gson, but there are some issues that's preventing this migration at the moment.
    // See https://github.com/googleapis/google-api-java-client/issues/1779 for details.
    return JacksonFactory.getDefaultInstance();
  }

  public static HttpTransport getDefaultTransport() {
    return Utils.getDefaultTransport();
  >>>>>>> master
  }
}
