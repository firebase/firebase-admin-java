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

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.auth.http.HttpCredentialsAdapter;
import java.io.IOException;

final class HttpRetryHandler implements HttpUnsuccessfulResponseHandler {

  private final HttpCredentialsAdapter credentials;
  private final HttpRetryConfig retryConfig;
  private final HttpUnsuccessfulResponseHandler responseHandler;

  private HttpRetryHandler(Builder builder) {
    this.credentials = checkNotNull(builder.credentials);
    this.retryConfig = checkNotNull(builder.retryConfig);
    this.responseHandler = checkNotNull(builder.responseHandler);
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

  static Builder builder() {
    return new Builder();
  }

  static class Builder {
    private HttpCredentialsAdapter credentials;
    private HttpRetryConfig retryConfig;
    private HttpUnsuccessfulResponseHandler responseHandler;

    private Builder() { }

    Builder setCredentials(HttpCredentialsAdapter credentials) {
      this.credentials = credentials;
      return this;
    }

    Builder setRetryConfig(HttpRetryConfig retryConfig) {
      this.retryConfig = retryConfig;
      return this;
    }

    Builder setResponseHandler(HttpUnsuccessfulResponseHandler responseHandler) {
      this.responseHandler = responseHandler;
      return this;
    }

    HttpRetryHandler build() {
      return new HttpRetryHandler(this);
    }
  }
}
