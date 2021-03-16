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

import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.json.JsonFactory;
import com.google.firebase.FirebaseException;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.internal.AbstractPlatformErrorHandler;
import com.google.firebase.internal.ErrorHandlingHttpClient;
import com.google.firebase.internal.HttpRequestInfo;
import com.google.firebase.internal.SdkUtils;

final class HttpHelper {

  private static final String CLIENT_VERSION_HEADER = "X-Client-Version";

  private static final String CLIENT_VERSION = "Java/Admin/" + SdkUtils.getVersion();

  private final ErrorHandlingHttpClient<FirebaseProjectManagementException> httpClient;

  HttpHelper(JsonFactory jsonFactory, HttpRequestFactory requestFactory) {
    ProjectManagementErrorHandler errorHandler = new ProjectManagementErrorHandler(jsonFactory);
    this.httpClient = new ErrorHandlingHttpClient<>(requestFactory, jsonFactory, errorHandler);
  }

  void setInterceptor(HttpResponseInterceptor interceptor) {
    httpClient.setInterceptor(interceptor);
  }

  <T> IncomingHttpResponse makeGetRequest(
      String url,
      T parsedResponseInstance,
      String requestIdentifier,
      String requestIdentifierDescription) throws FirebaseProjectManagementException {
    return makeRequest(
        HttpRequestInfo.buildGetRequest(url),
        parsedResponseInstance,
        requestIdentifier,
        requestIdentifierDescription);
  }

  <T> IncomingHttpResponse makePostRequest(
      String url,
      Object payload,
      T parsedResponseInstance,
      String requestIdentifier,
      String requestIdentifierDescription) throws FirebaseProjectManagementException {
    return makeRequest(
        HttpRequestInfo.buildJsonPostRequest(url, payload),
        parsedResponseInstance,
        requestIdentifier,
        requestIdentifierDescription);
  }

  <T> void makePatchRequest(
      String url,
      Object payload,
      T parsedResponseInstance,
      String requestIdentifier,
      String requestIdentifierDescription) throws FirebaseProjectManagementException {
    makeRequest(
        HttpRequestInfo.buildJsonRequest(HttpMethods.PATCH, url, payload),
        parsedResponseInstance,
        requestIdentifier,
        requestIdentifierDescription);
  }

  <T> void makeDeleteRequest(
      String url,
      T parsedResponseInstance,
      String requestIdentifier,
      String requestIdentifierDescription) throws FirebaseProjectManagementException {
    makeRequest(
        HttpRequestInfo.buildDeleteRequest(url),
        parsedResponseInstance,
        requestIdentifier,
        requestIdentifierDescription);
  }

  private <T> IncomingHttpResponse makeRequest(
      HttpRequestInfo baseRequest,
      T parsedResponseInstance,
      String requestIdentifier,
      String requestIdentifierDescription) throws FirebaseProjectManagementException {
    try {
      baseRequest.addHeader(CLIENT_VERSION_HEADER, CLIENT_VERSION);
      IncomingHttpResponse response = httpClient.send(baseRequest);
      httpClient.parse(response, parsedResponseInstance);
      return response;
    } catch (FirebaseProjectManagementException e) {
      String message = String.format(
          "%s \"%s\": %s", requestIdentifierDescription, requestIdentifier, e.getMessage());
      throw new FirebaseProjectManagementException(e, message);
    }
  }

  private static class ProjectManagementErrorHandler
      extends AbstractPlatformErrorHandler<FirebaseProjectManagementException> {

    ProjectManagementErrorHandler(JsonFactory jsonFactory) {
      super(jsonFactory);
    }

    @Override
    protected FirebaseProjectManagementException createException(FirebaseException base) {
      return new FirebaseProjectManagementException(base);
    }
  }
}
