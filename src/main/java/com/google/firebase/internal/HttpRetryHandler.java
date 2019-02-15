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
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.auth.http.HttpCredentialsAdapter;
import java.io.IOException;

final class HttpRetryHandler implements HttpUnsuccessfulResponseHandler, HttpIOExceptionHandler {

  private final HttpCredentialsAdapter credentials;
  private final HttpRetryConfig retryConfig;
  private final HttpIOExceptionHandler ioExceptionHandler;
  private final HttpUnsuccessfulResponseHandler responseHandler;

  HttpRetryHandler(HttpCredentialsAdapter credentials, HttpRetryConfig retryConfig) {
    this.credentials = checkNotNull(credentials);
    this.retryConfig = checkNotNull(retryConfig);
    this.ioExceptionHandler = new HttpBackOffIOExceptionHandler(retryConfig.newBackoff());
    this.responseHandler = new RetryAfterAwareHttpResponseHandler(retryConfig);
  }

  @Override
  public boolean handleIOException(HttpRequest request, boolean supportsRetry) throws IOException {
    return ioExceptionHandler.handleIOException(request, supportsRetry);
  }

  @Override
  public boolean handleResponse(
      HttpRequest request, HttpResponse response, boolean supportsRetry) throws IOException {

    boolean retry = credentials.handleResponse(request, response, supportsRetry);
    if (!retry) {
      int status = response.getStatusCode();
      if (retryConfig.getRetryStatusCodes().contains(status)) {
        retry = responseHandler.handleResponse(request, response, supportsRetry);
      }
    }
    request.setUnsuccessfulResponseHandler(this);
    return retry;
  }

  HttpIOExceptionHandler getIoExceptionHandler() {
    return ioExceptionHandler;
  }

  HttpUnsuccessfulResponseHandler getResponseHandler() {
    return responseHandler;
  }
}
