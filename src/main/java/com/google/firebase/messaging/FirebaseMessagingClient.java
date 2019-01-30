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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.api.client.googleapis.batch.BatchCallback;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.JsonParser;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.internal.FirebaseRequestInitializer;
import com.google.firebase.internal.Nullable;
import com.google.firebase.messaging.internal.InstanceIdServiceErrorResponse;
import com.google.firebase.messaging.internal.InstanceIdServiceResponse;
import com.google.firebase.messaging.internal.MessagingServiceErrorResponse;
import com.google.firebase.messaging.internal.MessagingServiceResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A helper class for interacting with Firebase Cloud Messaging service and the Firebase Instance
 * ID service.
 */
final class FirebaseMessagingClient {

  private static final String FCM_URL = "https://fcm.googleapis.com/v1/projects/%s/messages:send";

  private static final String FCM_BATCH_URL = "https://fcm.googleapis.com/batch";

  private static final String IID_HOST = "https://iid.googleapis.com";
  private static final String IID_SUBSCRIBE_PATH = "iid/v1:batchAdd";
  private static final String IID_UNSUBSCRIBE_PATH = "iid/v1:batchRemove";

  private static final String INTERNAL_ERROR = "internal-error";
  private static final String UNKNOWN_ERROR = "unknown-error";

  private static final Map<String, String> FCM_ERROR_CODES =
      ImmutableMap.<String, String>builder()
          // FCM v1 canonical error codes
          .put("NOT_FOUND", "registration-token-not-registered")
          .put("PERMISSION_DENIED", "mismatched-credential")
          .put("RESOURCE_EXHAUSTED", "message-rate-exceeded")
          .put("UNAUTHENTICATED", "invalid-apns-credentials")

          // FCM v1 new error codes
          .put("APNS_AUTH_ERROR", "invalid-apns-credentials")
          .put("INTERNAL", INTERNAL_ERROR)
          .put("INVALID_ARGUMENT", "invalid-argument")
          .put("QUOTA_EXCEEDED", "message-rate-exceeded")
          .put("SENDER_ID_MISMATCH", "mismatched-credential")
          .put("UNAVAILABLE", "server-unavailable")
          .put("UNREGISTERED", "registration-token-not-registered")
          .build();

  static final Map<Integer, String> IID_ERROR_CODES =
      ImmutableMap.<Integer, String>builder()
          .put(400, "invalid-argument")
          .put(401, "authentication-error")
          .put(403, "authentication-error")
          .put(500, INTERNAL_ERROR)
          .put(503, "server-unavailable")
          .build();

  private final String fcmSendUrl;
  private final HttpRequestFactory requestFactory;
  private final HttpRequestFactory childRequestFactory;
  private final JsonFactory jsonFactory;
  private final HttpResponseInterceptor responseInterceptor;

  FirebaseMessagingClient(FirebaseApp app, @Nullable HttpResponseInterceptor responseInterceptor) {
    String projectId = ImplFirebaseTrampolines.getProjectId(app);
    checkArgument(!Strings.isNullOrEmpty(projectId),
        "Project ID is required to access messaging service. Use a service account credential or "
            + "set the project ID explicitly via FirebaseOptions. Alternatively you can also "
            + "set the project ID via the GOOGLE_CLOUD_PROJECT environment variable.");
    this.fcmSendUrl = String.format(FCM_URL, projectId);
    HttpTransport httpTransport = app.getOptions().getHttpTransport();
    this.requestFactory = httpTransport.createRequestFactory(new FirebaseRequestInitializer(app));
    this.childRequestFactory = httpTransport.createRequestFactory();
    this.jsonFactory = app.getOptions().getJsonFactory();
    this.responseInterceptor = responseInterceptor;
  }

  String send(Message message, boolean dryRun) throws FirebaseMessagingException {
    try {
      return sendSingleRequest(message, dryRun);
    } catch (HttpResponseException e) {
      handleSendHttpError(e);
      return null;
    } catch (IOException e) {
      throw new FirebaseMessagingException(
          INTERNAL_ERROR, "Error while calling FCM backend service", e);
    }
  }

  List<BatchResponse> sendBatch(
      List<Message> messages, boolean dryRun) throws FirebaseMessagingException {
    try {
      return sendBatchRequest(messages, dryRun);
    } catch (HttpResponseException e) {
      handleSendHttpError(e);
      return null;
    } catch (IOException e) {
      throw new FirebaseMessagingException(
          INTERNAL_ERROR, "Error while calling FCM backend service", e);
    }
  }

  TopicManagementResponse subscribeToTopic(
      String topic, List<String> registrationTokens) throws FirebaseMessagingException {
    try {
      return sendInstanceIdRequest(topic, registrationTokens, IID_SUBSCRIBE_PATH);
    } catch (HttpResponseException e) {
      handleTopicManagementHttpError(e);
      return null;
    } catch (IOException e) {
      throw new FirebaseMessagingException(
          INTERNAL_ERROR, "Error while calling IID backend service", e);
    }
  }

  TopicManagementResponse unsubscribeFromTopic(
      String topic, List<String> registrationTokens) throws FirebaseMessagingException {
    try {
      return sendInstanceIdRequest(topic, registrationTokens, IID_UNSUBSCRIBE_PATH);
    } catch (HttpResponseException e) {
      handleTopicManagementHttpError(e);
      return null;
    } catch (IOException e) {
      throw new FirebaseMessagingException(
          INTERNAL_ERROR, "Error while calling IID backend service", e);
    }
  }

  private String sendSingleRequest(Message message, boolean dryRun) throws IOException {
    HttpRequest request = requestFactory.buildPostRequest(
        new GenericUrl(fcmSendUrl),
        new JsonHttpContent(jsonFactory, message.wrapForTransport(dryRun)));
    setFcmApiFormatVersion(request.getHeaders());
    request.setParser(new JsonObjectParser(jsonFactory));
    request.setResponseInterceptor(responseInterceptor);
    HttpResponse response = request.execute();
    try {
      MessagingServiceResponse parsed = new MessagingServiceResponse();
      jsonFactory.createJsonParser(response.getContent()).parseAndClose(parsed);
      return parsed.getName();
    } finally {
      disconnectQuietly(response);
    }
  }

  private List<BatchResponse> sendBatchRequest(
      List<Message> messages, boolean dryRun) throws IOException {

    MessagingBatchCallback callback = new MessagingBatchCallback();
    BatchRequest batch = newBatchRequest(messages, dryRun, callback);
    batch.execute();
    return callback.getResponses();
  }

  private BatchRequest newBatchRequest(
      List<Message> messages, boolean dryRun, MessagingBatchCallback callback) throws IOException {

    BatchRequest batch = new BatchRequest(
        requestFactory.getTransport(), getBatchRequestInitializer());
    batch.setBatchUrl(new GenericUrl(FCM_BATCH_URL));

    final JsonObjectParser jsonParser = new JsonObjectParser(this.jsonFactory);
    final GenericUrl sendUrl = new GenericUrl(fcmSendUrl);
    for (Message message : messages) {
      // Using a separate request factory without authorization is faster for large batches.
      // A simple perf test showed a 400-500ms speed up for batches of 1000 messages.
      HttpRequest request = childRequestFactory.buildPostRequest(
          sendUrl,
          new JsonHttpContent(jsonFactory, message.wrapForTransport(dryRun)));
      request.setParser(jsonParser);
      setFcmApiFormatVersion(request.getHeaders());
      batch.queue(
          request, MessagingServiceResponse.class, MessagingServiceErrorResponse.class, callback);
    }

    return batch;
  }

  private void setFcmApiFormatVersion(HttpHeaders headers) {
    headers.set("X-GOOG-API-FORMAT-VERSION", "2");
  }

  private void disconnectQuietly(HttpResponse response) {
    if (response != null) {
      try {
        response.disconnect();
      } catch (IOException ignored) {
        // ignored
      }
    }
  }

  private void handleSendHttpError(HttpResponseException e) throws FirebaseMessagingException {
    MessagingServiceErrorResponse response = new MessagingServiceErrorResponse();
    if (e.getContent() != null) {
      try {
        JsonParser parser = jsonFactory.createJsonParser(e.getContent());
        parser.parseAndClose(response);
      } catch (IOException ignored) {
        // ignored
      }
    }
    throw newException(response, e);
  }

  private HttpRequestInitializer getBatchRequestInitializer() {
    return new HttpRequestInitializer(){
      @Override
      public void initialize(HttpRequest request) throws IOException {
        requestFactory.getInitializer().initialize(request);
        request.setResponseInterceptor(responseInterceptor);
      }
    };
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
      InstanceIdServiceResponse parsed = response.parseAs(InstanceIdServiceResponse.class);
      return new TopicManagementResponse(parsed.getResults());
    } finally {
      disconnectQuietly(response);
    }
  }

  private String getPrefixedTopic(String topic) {
    if (topic.startsWith("/topics/")) {
      return topic;
    } else {
      return "/topics/" + topic;
    }
  }

  private void handleTopicManagementHttpError(
      HttpResponseException e) throws FirebaseMessagingException {
    InstanceIdServiceErrorResponse response = new InstanceIdServiceErrorResponse();
    if (e.getContent() != null) {
      try {
        JsonParser parser = jsonFactory.createJsonParser(e.getContent());
        parser.parseAndClose(response);
      } catch (IOException ignored) {
        // ignored
      }
    }
    throw newException(response, e);
  }

  private static FirebaseMessagingException newException(MessagingServiceErrorResponse response) {
    return newException(response, null);
  }

  private static FirebaseMessagingException newException(
      MessagingServiceErrorResponse response, @Nullable HttpResponseException e) {
    String code = FCM_ERROR_CODES.get(response.getErrorCode());
    if (code == null) {
      code = UNKNOWN_ERROR;
    }
    String msg = response.getErrorMessage();
    if (Strings.isNullOrEmpty(msg)) {
      if (e != null) {
        msg = String.format("Unexpected HTTP response with status: %d; body: %s",
            e.getStatusCode(), e.getContent());
      } else {
        msg = String.format("Unexpected HTTP response: %s", response.toString());
      }
    }
    return new FirebaseMessagingException(code, msg, e);
  }

  private static FirebaseMessagingException newException(
      InstanceIdServiceErrorResponse response, HttpResponseException e) {
    // Infer error code from HTTP status
    String code = IID_ERROR_CODES.get(e.getStatusCode());
    if (code == null) {
      code = UNKNOWN_ERROR;
    }
    String msg = response.getError();
    if (Strings.isNullOrEmpty(msg)) {
      msg = String.format("Unexpected HTTP response with status: %d; body: %s",
          e.getStatusCode(), e.getContent());
    }
    return new FirebaseMessagingException(code, msg, e);
  }

  private static class MessagingBatchCallback
      implements BatchCallback<MessagingServiceResponse, MessagingServiceErrorResponse> {

    private final ImmutableList.Builder<BatchResponse> responses = ImmutableList.builder();

    @Override
    public void onSuccess(
        MessagingServiceResponse response, HttpHeaders responseHeaders) {
      responses.add(BatchResponse.fromResponse(response));
    }

    @Override
    public void onFailure(
        MessagingServiceErrorResponse error, HttpHeaders responseHeaders) {
      responses.add(BatchResponse.fromException(newException(error)));
    }

    List<BatchResponse> getResponses() {
      return this.responses.build();
    }
  }
}
