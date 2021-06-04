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

import static com.google.firebase.internal.ApiClientUtils.DEFAULT_RETRY_CONFIG;
import static org.junit.Assert.assertEquals;
  <<<<<<< redacted-passwords
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.api.client.http.GenericUrl;
  =======
import static org.junit.Assert.assertTrue;

  >>>>>>> master
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.api.client.testing.util.MockSleeper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.internal.RetryInitializer.RetryHandlerDecorator;
  <<<<<<< redacted-passwords
import java.io.IOException;
  =======
  >>>>>>> master

public class TestApiClientUtils {

  private static final RetryConfig TEST_RETRY_CONFIG = RetryConfig.builder()
      .setMaxRetries(DEFAULT_RETRY_CONFIG.getMaxRetries())
      .setRetryStatusCodes(DEFAULT_RETRY_CONFIG.getRetryStatusCodes())
      .setMaxIntervalMillis(DEFAULT_RETRY_CONFIG.getMaxIntervalMillis())
      .setSleeper(new MockSleeper())
      .build();

  <<<<<<< redacted-passwords
  private static final GenericUrl TEST_URL = new GenericUrl("https://firebase.google.com");

  =======
  >>>>>>> master
  /**
   * Creates a new {@code HttpRequestFactory} which provides authorization (OAuth2), timeouts and
   * automatic retries. Bypasses exponential backoff between consecutive retries for faster
   * execution during tests.
   *
   * @param app {@link FirebaseApp} from which to obtain authorization credentials.
   * @return A new {@code HttpRequestFactory} instance.
   */
  public static HttpRequestFactory delayBypassedRequestFactory(FirebaseApp app) {
    return ApiClientUtils.newAuthorizedRequestFactory(app, TEST_RETRY_CONFIG);
  }

  /**
   * Creates a new {@code HttpRequestFactory} which provides authorization (OAuth2), timeouts but
   * no retries.
   *
   * @param app {@link FirebaseApp} from which to obtain authorization credentials.
   * @return A new {@code HttpRequestFactory} instance.
   */
  public static HttpRequestFactory retryDisabledRequestFactory(FirebaseApp app) {
    return ApiClientUtils.newAuthorizedRequestFactory(app, null);
  }

  /**
  <<<<<<< redacted-passwords
   * Checks whther the given HttpRequestFactory has been configured for authorization and
   * automatic retries.
   *
   * @param requestFactory The HttpRequestFactory to check.
   */
  public static void assertAuthAndRetrySupport(HttpRequestFactory requestFactory) {
    assertTrue(requestFactory.getInitializer() instanceof FirebaseRequestInitializer);
    HttpRequest request;
    try {
      request = requestFactory.buildGetRequest(TEST_URL);
    } catch (IOException e) {
      throw new RuntimeException("Failed to initialize request", e);
    }

  =======
   * Checks whether the given HttpRequest has been configured for authorization and
   * automatic retries.
   *
   * @param request The HttpRequest to check.
   */
  public static void assertAuthAndRetrySupport(HttpRequest request) {
  >>>>>>> master
    // Verify authorization
    assertTrue(request.getHeaders().getAuthorization().startsWith("Bearer "));

    // Verify retry support
    HttpUnsuccessfulResponseHandler retryHandler = request.getUnsuccessfulResponseHandler();
    assertTrue(retryHandler instanceof RetryHandlerDecorator);
    RetryConfig retryConfig = ((RetryHandlerDecorator) retryHandler).getRetryHandler()
        .getRetryConfig();
    assertEquals(DEFAULT_RETRY_CONFIG.getMaxRetries(), retryConfig.getMaxRetries());
    assertEquals(DEFAULT_RETRY_CONFIG.getMaxIntervalMillis(), retryConfig.getMaxIntervalMillis());
  <<<<<<< redacted-passwords
    assertFalse(retryConfig.isRetryOnIOExceptions());
  =======
    assertEquals(DEFAULT_RETRY_CONFIG.isRetryOnIOExceptions(), retryConfig.isRetryOnIOExceptions());
  >>>>>>> master
    assertEquals(DEFAULT_RETRY_CONFIG.getRetryStatusCodes(), retryConfig.getRetryStatusCodes());
  }
}
