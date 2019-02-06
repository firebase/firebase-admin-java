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
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.JsonParser;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.internal.ApiClientUtils;
import com.google.firebase.internal.Nullable;
import com.google.firebase.messaging.internal.MessagingServiceErrorResponse;
import com.google.firebase.messaging.internal.MessagingServiceResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A helper class for interacting with Firebase Cloud Messaging service.
 */
final class FirebaseMessagingClient {

  private static final String FCM_URL = "https://fcm.googleapis.com/v1/projects/%s/messages:send";

  private static final String FCM_BATCH_URL = "https://fcm.googleapis.com/batch";

  private static final Map<String, String> FCM_ERROR_CODES =
      ImmutableMap.<String, String>builder()
          // FCM v1 canonical error codes
          .put("NOT_FOUND", "registration-token-not-registered")
          .put("PERMISSION_DENIED", "mismatched-credential")
          .put("RESOURCE_EXHAUSTED", "message-rate-exceeded")
          .put("UNAUTHENTICATED", "invalid-apns-credentials")

          // FCM v1 new error codes
          .put("APNS_AUTH_ERROR", "invalid-apns-credentials")
          .put("INTERNAL", FirebaseMessaging.INTERNAL_ERROR)
          .put("INVALID_ARGUMENT", "invalid-argument")
          .put("QUOTA_EXCEEDED", "message-rate-exceeded")
          .put("SENDER_ID_MISMATCH", "mismatched-credential")
          .put("UNAVAILABLE", "server-unavailable")
          .put("UNREGISTERED", "registration-token-not-registered")
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
    this.requestFactory = ApiClientUtils.newAuthorizedRequestFactory(app);
    this.childRequestFactory = ApiClientUtils.newUnauthorizedRequestFactory(app);
    this.jsonFactory = app.getOptions().getJsonFactory();
    this.responseInterceptor = responseInterceptor;
  }

  String send(Message message, boolean dryRun) throws FirebaseMessagingException {
    try {
      return sendSingleRequest(message, dryRun);
    } catch (HttpResponseException e) {
      throw createExceptionFromResponse(e);
    } catch (IOException e) {
      throw new FirebaseMessagingException(
          FirebaseMessaging.INTERNAL_ERROR, "Error while calling FCM backend service", e);
    }
  }

  BatchResponse sendAll(
      List<Message> messages, boolean dryRun) throws FirebaseMessagingException {
    try {
      return sendBatchRequest(messages, dryRun);
    } catch (HttpResponseException e) {
      throw createExceptionFromResponse(e);
    } catch (IOException e) {
      throw new FirebaseMessagingException(
          FirebaseMessaging.INTERNAL_ERROR, "Error while calling FCM backend service", e);
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
      return parsed.getMessageId();
    } finally {
      ApiClientUtils.disconnectQuietly(response);
    }
  }

  private BatchResponse sendBatchRequest(
      List<Message> messages, boolean dryRun) throws IOException {

    MessagingBatchCallback callback = new MessagingBatchCallback();
    BatchRequest batch = newBatchRequest(messages, dryRun, callback);
    batch.execute();
    return new BatchResponse(callback.getResponses());
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
      // A simple performance test showed a 400-500ms speed up for batches of 1000 messages.
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

  private FirebaseMessagingException createExceptionFromResponse(HttpResponseException e) {
    MessagingServiceErrorResponse response = new MessagingServiceErrorResponse();
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

  private HttpRequestInitializer getBatchRequestInitializer() {
    return new HttpRequestInitializer(){
      @Override
      public void initialize(HttpRequest request) throws IOException {
        requestFactory.getInitializer().initialize(request);
        request.setResponseInterceptor(responseInterceptor);
      }
    };
  }

  private static FirebaseMessagingException newException(MessagingServiceErrorResponse response) {
    return newException(response, null);
  }

  private static FirebaseMessagingException newException(
      MessagingServiceErrorResponse response, @Nullable HttpResponseException e) {
    String code = FCM_ERROR_CODES.get(response.getErrorCode());
    if (code == null) {
      code = FirebaseMessaging.UNKNOWN_ERROR;
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

  private static class MessagingBatchCallback
      implements BatchCallback<MessagingServiceResponse, MessagingServiceErrorResponse> {

    private final ImmutableList.Builder<SendResponse> responses = ImmutableList.builder();

    @Override
    public void onSuccess(
        MessagingServiceResponse response, HttpHeaders responseHeaders) {
      responses.add(SendResponse.fromMessageId(response.getMessageId()));
    }

    @Override
    public void onFailure(
        MessagingServiceErrorResponse error, HttpHeaders responseHeaders) {
      responses.add(SendResponse.fromException(newException(error)));
    }

    List<SendResponse> getResponses() {
      return this.responses.build();
    }
  }
}
