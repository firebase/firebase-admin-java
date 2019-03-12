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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.HttpBackOffIOExceptionHandler;
import com.google.api.client.http.HttpIOExceptionHandler;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import java.io.IOException;

/**
 * Configures HTTP requests to be retried. Requests that encounter I/O errors are retried if
 * {@link RetryConfig#isRetryOnIOExceptions()} is set. Requests failing with unsuccessful HTTP
 * responses are first referred to the {@code HttpUnsuccessfulResponseHandler} that was originally
 * set on the request. If the request does not get retried at that level,
 * {@link RetryUnsuccessfulResponseHandler} is used to schedule additional retries.
 */
final class RetryInitializer implements HttpRequestInitializer {

  private final RetryConfig retryConfig;

  RetryInitializer(RetryConfig retryConfig) {
    this.retryConfig = checkNotNull(retryConfig);
  }

  @Override
  public void initialize(HttpRequest request) {
    request.setNumberOfRetries(retryConfig.getMaxRetries());
    request.setUnsuccessfulResponseHandler(newUnsuccessfulResponseHandler(request));
    if (retryConfig.isRetryOnIOExceptions()) {
      request.setIOExceptionHandler(newIOExceptionHandler());
    }
  }

  private HttpUnsuccessfulResponseHandler newUnsuccessfulResponseHandler(HttpRequest request) {
    RetryUnsuccessfulResponseHandler retryHandler = new RetryUnsuccessfulResponseHandler(
        retryConfig);
    return new RetryHandlerDecorator(retryHandler, request);
  }

  private HttpIOExceptionHandler newIOExceptionHandler() {
    return new HttpBackOffIOExceptionHandler(retryConfig.newBackOff())
        .setSleeper(retryConfig.getSleeper());
  }

  /**
   * Makes sure that any error handlers already set on the request are executed before the retry
   * handler is called. This is needed since some initializers (e.g. HttpCredentialsAdapter)
   * register their own error handlers.
   */
  static class RetryHandlerDecorator implements HttpUnsuccessfulResponseHandler {

    private final RetryUnsuccessfulResponseHandler retryHandler;
    private final HttpUnsuccessfulResponseHandler preRetryHandler;

    private RetryHandlerDecorator(
        RetryUnsuccessfulResponseHandler retryHandler, HttpRequest request) {
      this.retryHandler = checkNotNull(retryHandler);
      HttpUnsuccessfulResponseHandler preRetryHandler = request.getUnsuccessfulResponseHandler();
      if (preRetryHandler == null) {
        preRetryHandler = new HttpUnsuccessfulResponseHandler() {
          @Override
          public boolean handleResponse(
              HttpRequest request, HttpResponse response, boolean supportsRetry) {
            return false;
          }
        };
      }
      this.preRetryHandler = preRetryHandler;
    }

    @Override
    public boolean handleResponse(
        HttpRequest request,
        HttpResponse response,
        boolean supportsRetry) throws IOException {
      try {
        boolean retry = preRetryHandler.handleResponse(request, response, supportsRetry);
        if (!retry) {
          retry = retryHandler.handleResponse(request, response, supportsRetry);
        }
        return retry;
      } finally {
        // Pre-retry handler may have reset the unsuccessful response handler on the
        // request. This changes it back.
        request.setUnsuccessfulResponseHandler(this);
      }
    }

    RetryUnsuccessfulResponseHandler getRetryHandler() {
      return retryHandler;
    }
  }
}
