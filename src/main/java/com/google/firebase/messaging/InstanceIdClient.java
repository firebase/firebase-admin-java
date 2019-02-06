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

package com.google.firebase.messaging;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.JsonParser;
import com.google.api.client.util.Key;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.internal.ApiClientUtils;
import com.google.firebase.internal.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A helper class for interacting with the Firebase Instance ID service. Implements the FCM
 * topic management functionality.
 */
final class InstanceIdClient {

  private static final String IID_HOST = "https://iid.googleapis.com";

  private static final String IID_SUBSCRIBE_PATH = "iid/v1:batchAdd";

  private static final String IID_UNSUBSCRIBE_PATH = "iid/v1:batchRemove";

  static final Map<Integer, String> IID_ERROR_CODES =
      ImmutableMap.<Integer, String>builder()
          .put(400, "invalid-argument")
          .put(401, "authentication-error")
          .put(403, "authentication-error")
          .put(500, FirebaseMessaging.INTERNAL_ERROR)
          .put(503, "server-unavailable")
          .build();

  private final HttpRequestFactory requestFactory;
  private final JsonFactory jsonFactory;
  private final HttpResponseInterceptor responseInterceptor;

  InstanceIdClient(FirebaseApp app, @Nullable HttpResponseInterceptor responseInterceptor) {
    this.requestFactory = ApiClientUtils.newAuthorizedRequestFactory(app);
    this.jsonFactory = app.getOptions().getJsonFactory();
    this.responseInterceptor = responseInterceptor;
  }

  TopicManagementResponse subscribeToTopic(
      String topic, List<String> registrationTokens) throws FirebaseMessagingException {
    try {
      return sendInstanceIdRequest(topic, registrationTokens, IID_SUBSCRIBE_PATH);
    } catch (HttpResponseException e) {
      throw createExceptionFromResponse(e);
    } catch (IOException e) {
      throw new FirebaseMessagingException(
          FirebaseMessaging.INTERNAL_ERROR, "Error while calling IID backend service", e);
    }
  }

  TopicManagementResponse unsubscribeFromTopic(
      String topic, List<String> registrationTokens) throws FirebaseMessagingException {
    try {
      return sendInstanceIdRequest(topic, registrationTokens, IID_UNSUBSCRIBE_PATH);
    } catch (HttpResponseException e) {
      throw createExceptionFromResponse(e);
    } catch (IOException e) {
      throw new FirebaseMessagingException(
          FirebaseMessaging.INTERNAL_ERROR, "Error while calling IID backend service", e);
    }
  }

  private TopicManagementResponse sendInstanceIdRequest(
      String topic, List<String> registrationTokens, String path) throws IOException {
    String url = String.format("%s/%s", IID_HOST, path);
    Map<String, Object> payload = ImmutableMap.of(
        "to", getPrefixedTopic(topic),
        "registration_tokens", registrationTokens
    );
    HttpResponse response = null;
    try {
      HttpRequest request = requestFactory.buildPostRequest(
          new GenericUrl(url), new JsonHttpContent(jsonFactory, payload));
      request.getHeaders().set("access_token_auth", "true");
      request.setParser(new JsonObjectParser(jsonFactory));
      request.setResponseInterceptor(responseInterceptor);
      response = request.execute();

      JsonParser parser = jsonFactory.createJsonParser(response.getContent());
      InstanceIdServiceResponse parsedResponse = new InstanceIdServiceResponse();
      parser.parse(parsedResponse);
      return new TopicManagementResponse(parsedResponse.results);
    } finally {
      ApiClientUtils.disconnectQuietly(response);
    }
  }

  private FirebaseMessagingException createExceptionFromResponse(HttpResponseException e) {
    InstanceIdServiceErrorResponse response = new InstanceIdServiceErrorResponse();
    if (e.getContent() != null) {
      try {
        JsonParser parser = jsonFactory.createJsonParser(e.getContent());
        parser.parseAndClose(response);
      } catch (IOException ignored) {
        // ignored
      }
    }
    return newException(response, e);
  }

  private String getPrefixedTopic(String topic) {
    if (topic.startsWith("/topics/")) {
      return topic;
    } else {
      return "/topics/" + topic;
    }
  }

  private static FirebaseMessagingException newException(
      InstanceIdServiceErrorResponse response, HttpResponseException e) {
    // Infer error code from HTTP status
    String code = IID_ERROR_CODES.get(e.getStatusCode());
    if (code == null) {
      code = FirebaseMessaging.UNKNOWN_ERROR;
    }
    String msg = response.error;
    if (Strings.isNullOrEmpty(msg)) {
      msg = String.format("Unexpected HTTP response with status: %d; body: %s",
          e.getStatusCode(), e.getContent());
    }
    return new FirebaseMessagingException(code, msg, e);
  }

  private static class InstanceIdServiceResponse {
    @Key("results")
    private List<GenericJson> results;
  }

  private static class InstanceIdServiceErrorResponse {
    @Key("error")
    private String error;
  }
}
