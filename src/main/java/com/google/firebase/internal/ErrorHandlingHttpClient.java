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
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.JsonParser;
import com.google.common.io.CharStreams;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseHttpResponse;
import java.io.IOException;
import java.io.InputStreamReader;

public final class ErrorHandlingHttpClient<T extends FirebaseException> {

  private final HttpRequestFactory requestFactory;
  private final JsonFactory jsonFactory;
  private final HttpErrorHandler<T> errorHandler;

  public ErrorHandlingHttpClient(
      FirebaseApp app, HttpErrorHandler<T> errorHandler, RetryConfig retryConfig) {
    this(
        ApiClientUtils.newAuthorizedRequestFactory(app, retryConfig),
        app.getOptions().getJsonFactory(),
        errorHandler);
  }

  public ErrorHandlingHttpClient(HttpRequestFactory requestFactory,
      JsonFactory jsonFactory, HttpErrorHandler<T> errorHandler) {
    this.requestFactory = checkNotNull(requestFactory, "requestFactory must not be null");
    this.jsonFactory = checkNotNull(jsonFactory, "jsonFactory must not be null");
    this.errorHandler = checkNotNull(errorHandler, "errorHandler must not be null");
  }

  public <V> V sendAndParse(HttpRequestInfo requestInfo, Class<V> responseType) throws T {
    HttpResponseInfo responseInfo = this.sendRequest(requestInfo);

    try {
      return this.parseResponse(responseInfo, responseType);
    } finally {
      responseInfo.disconnect();
    }
  }

  private HttpResponseInfo sendRequest(HttpRequestInfo requestInfo) throws T {
    HttpRequest request = newHttpRequest(requestInfo);

    try {
      return new HttpResponseInfo(request.execute());
    } catch (HttpResponseException e) {
      FirebaseHttpResponse response = new FirebaseHttpResponse(e, request);
      throw errorHandler.handleHttpResponseException(e, response);
    } catch (IOException e) {
      throw errorHandler.handleIOException(e);
    }
  }

  private HttpRequest newHttpRequest(HttpRequestInfo requestInfo) throws T {
    try {
      HttpRequest request = requestInfo.newHttpRequest(requestFactory);
      request.setParser(new JsonObjectParser(jsonFactory));
      return request;
    } catch (IOException e) {
      throw errorHandler.handleIOException(e);
    }
  }

  private <V> V parseResponse(HttpResponseInfo responseInfo, Class<V> responseType) throws T {
    try {
      JsonParser parser = jsonFactory.createJsonParser(responseInfo.content);
      return parser.parseAndClose(responseType);
    } catch (IOException e) {
      throw errorHandler.handleParseException(e, responseInfo.toFirebaseHttpResponse());
    }
  }

  private static class HttpResponseInfo {
    private final HttpResponse response;
    private final String content;

    HttpResponseInfo(HttpResponse response) throws IOException {
      this.response = response;
      // Read and buffer the content here. Otherwise if a parse error occurs,
      // we lose the content.
      this.content = CharStreams.toString(
          new InputStreamReader(response.getContent(), response.getContentCharset()));
    }

    void disconnect() {
      ApiClientUtils.disconnectQuietly(response);
    }

    FirebaseHttpResponse toFirebaseHttpResponse() {
      return new FirebaseHttpResponse(response, content);
    }
  }
}
