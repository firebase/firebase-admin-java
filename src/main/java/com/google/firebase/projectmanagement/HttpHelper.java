/*
 * Copyright 2018 Google Inc.
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

package com.google.firebase.projectmanagement;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.internal.Nullable;
import com.google.firebase.internal.SdkUtils;
import java.io.IOException;

class HttpHelper {

  @VisibleForTesting static final String PATCH_OVERRIDE_KEY = "X-HTTP-Method-Override";
  @VisibleForTesting static final String PATCH_OVERRIDE_VALUE = "PATCH";
  private static final ImmutableMap<Integer, String> ERROR_CODES =
      ImmutableMap.<Integer, String>builder()
          .put(401, "Request not authorized.")
          .put(403, "Client does not have sufficient privileges.")
          .put(404, "Failed to find the resource.")
          .put(409, "The resource already exists.")
          .put(429, "Request throttled by the backend server.")
          .put(500, "Internal server error.")
          .put(503, "Backend servers are over capacity. Try again later.")
          .build();
  private static final String CLIENT_VERSION_HEADER = "X-Client-Version";

  private final String clientVersion = "Java/Admin/" + SdkUtils.getVersion();
  private final JsonFactory jsonFactory;
  private final HttpRequestFactory requestFactory;
  private HttpResponseInterceptor interceptor;

  HttpHelper(JsonFactory jsonFactory, HttpRequestFactory requestFactory) {
    this.jsonFactory = jsonFactory;
    this.requestFactory = requestFactory;
  }

  void setInterceptor(HttpResponseInterceptor interceptor) {
    this.interceptor = interceptor;
  }

  <T> void makeGetRequest(
      String url,
      T parsedResponseInstance,
      String requestIdentifier,
      String requestIdentifierDescription) throws FirebaseProjectManagementException {
    try {
      makeRequest(
          requestFactory.buildGetRequest(new GenericUrl(url)),
          parsedResponseInstance,
          requestIdentifier,
          requestIdentifierDescription);
    } catch (IOException e) {
      handleError(requestIdentifier, requestIdentifierDescription, e);
    }
  }

  <T> void makePostRequest(
      String url,
      Object payload,
      T parsedResponseInstance,
      String requestIdentifier,
      String requestIdentifierDescription) throws FirebaseProjectManagementException {
    try {
      makeRequest(
          requestFactory.buildPostRequest(
              new GenericUrl(url), new JsonHttpContent(jsonFactory, payload)),
          parsedResponseInstance,
          requestIdentifier,
          requestIdentifierDescription);
    } catch (IOException e) {
      handleError(requestIdentifier, requestIdentifierDescription, e);
    }
  }

  <T> void makePatchRequest(
      String url,
      Object payload,
      T parsedResponseInstance,
      String requestIdentifier,
      String requestIdentifierDescription) throws FirebaseProjectManagementException {
    try {
      HttpRequest baseRequest = requestFactory.buildPostRequest(
          new GenericUrl(url), new JsonHttpContent(jsonFactory, payload));
      baseRequest.getHeaders().set(PATCH_OVERRIDE_KEY, PATCH_OVERRIDE_VALUE);
      makeRequest(
          baseRequest, parsedResponseInstance, requestIdentifier, requestIdentifierDescription);
    } catch (IOException e) {
      handleError(requestIdentifier, requestIdentifierDescription, e);
    }
  }

  <T> void makeDeleteRequest(
      String url,
      T parsedResponseInstance,
      String requestIdentifier,
      String requestIdentifierDescription) throws FirebaseProjectManagementException {
    try {
      makeRequest(
          requestFactory.buildDeleteRequest(new GenericUrl(url)),
          parsedResponseInstance,
          requestIdentifier,
          requestIdentifierDescription);
    } catch (IOException e) {
      handleError(requestIdentifier, requestIdentifierDescription, e);
    }
  }

  <T> void makeRequest(
      HttpRequest baseRequest,
      T parsedResponseInstance,
      String requestIdentifier,
      String requestIdentifierDescription) throws FirebaseProjectManagementException {
    HttpResponse response = null;
    try {
      baseRequest.getHeaders().set(CLIENT_VERSION_HEADER, clientVersion);
      baseRequest.setParser(new JsonObjectParser(jsonFactory));
      baseRequest.setResponseInterceptor(interceptor);
      response = baseRequest.execute();
      jsonFactory.createJsonParser(response.getContent(), Charsets.UTF_8)
          .parseAndClose(parsedResponseInstance);
    } catch (Exception e) {
      handleError(requestIdentifier, requestIdentifierDescription, e);
    } finally {
      disconnectQuietly(response);
    }
  }

  private static void disconnectQuietly(HttpResponse response) {
    if (response != null) {
      try {
        response.disconnect();
      } catch (IOException ignored) {
        // Ignored.
      }
    }
  }

  private static void handleError(
      String requestIdentifier, String requestIdentifierDescription, Exception e)
          throws FirebaseProjectManagementException {
    String messageBody = "Error while invoking Firebase Project Management service.";
    if (e instanceof HttpResponseException) {
      int statusCode = ((HttpResponseException) e).getStatusCode();
      if (ERROR_CODES.containsKey(statusCode)) {
        messageBody = ERROR_CODES.get(statusCode);
      }
    }
    throw createFirebaseProjectManagementException(
        requestIdentifier, requestIdentifierDescription, messageBody, e);
  }

  static FirebaseProjectManagementException createFirebaseProjectManagementException(
      String requestIdentifier,
      String requestIdentifierDescription,
      String messageBody,
      @Nullable Exception cause) {
    return new FirebaseProjectManagementException(
        String.format(
            "%s \"%s\": %s", requestIdentifierDescription, requestIdentifier, messageBody),
        cause);
  }
}
