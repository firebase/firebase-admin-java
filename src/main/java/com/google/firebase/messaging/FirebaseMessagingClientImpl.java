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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.googleapis.batch.BatchCallback;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClient;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.OutgoingHttpRequest;
import com.google.firebase.internal.AbstractPlatformErrorHandler;
import com.google.firebase.internal.ApiClientUtils;
import com.google.firebase.internal.ErrorHandlingHttpClient;
import com.google.firebase.internal.HttpRequestInfo;
import com.google.firebase.internal.SdkUtils;
import com.google.firebase.messaging.internal.MessagingServiceErrorResponse;
import com.google.firebase.messaging.internal.MessagingServiceResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A helper class for interacting with Firebase Cloud Messaging service.
 */
final class FirebaseMessagingClientImpl implements FirebaseMessagingClient {

  private static final String FCM_URL = "https://fcm.googleapis.com/v1/projects/%s/messages:send";

  private static final Map<String, String> COMMON_HEADERS =
      ImmutableMap.of(
          "X-GOOG-API-FORMAT-VERSION", "2",
          "X-Firebase-Client", "fire-admin-java/" + SdkUtils.getVersion());

  private final String fcmSendUrl;
  private final HttpRequestFactory requestFactory;
  private final HttpRequestFactory childRequestFactory;
  private final JsonFactory jsonFactory;
  private final HttpResponseInterceptor responseInterceptor;
  private final MessagingErrorHandler errorHandler;
  private final ErrorHandlingHttpClient<FirebaseMessagingException> httpClient;
  private final MessagingBatchClient batchClient;

  private FirebaseMessagingClientImpl(Builder builder) {
    checkArgument(!Strings.isNullOrEmpty(builder.projectId));
    this.fcmSendUrl = String.format(FCM_URL, builder.projectId);
    this.requestFactory = checkNotNull(builder.requestFactory);
    this.childRequestFactory = checkNotNull(builder.childRequestFactory);
    this.jsonFactory = checkNotNull(builder.jsonFactory);
    this.responseInterceptor = builder.responseInterceptor;
    this.errorHandler = new MessagingErrorHandler(this.jsonFactory);
    this.httpClient = new ErrorHandlingHttpClient<>(requestFactory, jsonFactory, errorHandler)
      .setInterceptor(responseInterceptor);
    this.batchClient = new MessagingBatchClient(requestFactory.getTransport(), jsonFactory);
  }

  @VisibleForTesting
  String getFcmSendUrl() {
    return fcmSendUrl;
  }

  @VisibleForTesting
  HttpRequestFactory getRequestFactory() {
    return requestFactory;
  }

  @VisibleForTesting
  HttpRequestFactory getChildRequestFactory() {
    return childRequestFactory;
  }

  @VisibleForTesting
  JsonFactory getJsonFactory() {
    return jsonFactory;
  }

  public String send(Message message, boolean dryRun) throws FirebaseMessagingException {
    return sendSingleRequest(message, dryRun);
  }

  public BatchResponse sendAll(
      List<Message> messages, boolean dryRun) throws FirebaseMessagingException {
    return sendBatchRequest(messages, dryRun);
  }

  private String sendSingleRequest(
      Message message, boolean dryRun) throws FirebaseMessagingException {
    HttpRequestInfo request =
        HttpRequestInfo.buildJsonPostRequest(
            fcmSendUrl, message.wrapForTransport(dryRun))
            .addAllHeaders(COMMON_HEADERS);
    MessagingServiceResponse parsed = httpClient.sendAndParse(
        request, MessagingServiceResponse.class);
    return parsed.getMessageId();
  }

  private BatchResponse sendBatchRequest(
      List<Message> messages, boolean dryRun) throws FirebaseMessagingException {

    MessagingBatchCallback callback = new MessagingBatchCallback();
    try {
      BatchRequest batch = newBatchRequest(messages, dryRun, callback);
      batch.execute();
      return new BatchResponseImpl(callback.getResponses());
    } catch (HttpResponseException e) {
      OutgoingHttpRequest req = new OutgoingHttpRequest(
          HttpMethods.POST, MessagingBatchClient.FCM_BATCH_URL);
      IncomingHttpResponse resp = new IncomingHttpResponse(e, req);
      throw errorHandler.handleHttpResponseException(e, resp);
    } catch (IOException e) {
      throw errorHandler.handleIOException(e);
    }
  }

  private BatchRequest newBatchRequest(
      List<Message> messages, boolean dryRun, MessagingBatchCallback callback) throws IOException {

    BatchRequest batch = batchClient.batch(getBatchRequestInitializer());
    final JsonObjectParser jsonParser = new JsonObjectParser(this.jsonFactory);
    final GenericUrl sendUrl = new GenericUrl(fcmSendUrl);
    for (Message message : messages) {
      // Using a separate request factory without authorization is faster for large batches.
      // A simple performance test showed a 400-500ms speed up for batches of 1000 messages.
      HttpRequest request = childRequestFactory.buildPostRequest(
          sendUrl,
          new JsonHttpContent(jsonFactory, message.wrapForTransport(dryRun)));
      request.setParser(jsonParser);
      request.getHeaders().putAll(COMMON_HEADERS);
      batch.queue(
          request, MessagingServiceResponse.class, MessagingServiceErrorResponse.class, callback);
    }
    return batch;
  }

  private HttpRequestInitializer getBatchRequestInitializer() {
    return new HttpRequestInitializer() {
      @Override
      public void initialize(HttpRequest request) throws IOException {
        // Batch requests are not executed on the ErrorHandlingHttpClient. Therefore, they
        // require some special handling at initialization.
        HttpRequestInitializer initializer = requestFactory.getInitializer();
        if (initializer != null) {
          initializer.initialize(request);
        }
        request.setResponseInterceptor(responseInterceptor);
      }
    };
  }

  static FirebaseMessagingClientImpl fromApp(FirebaseApp app) {
    String projectId = ImplFirebaseTrampolines.getProjectId(app);
    checkArgument(!Strings.isNullOrEmpty(projectId),
        "Project ID is required to access messaging service. Use a service account credential or "
            + "set the project ID explicitly via FirebaseOptions. Alternatively you can also "
            + "set the project ID via the GOOGLE_CLOUD_PROJECT environment variable.");
    return FirebaseMessagingClientImpl.builder()
        .setProjectId(projectId)
        .setRequestFactory(ApiClientUtils.newAuthorizedRequestFactory(app))
        .setChildRequestFactory(ApiClientUtils.newUnauthorizedRequestFactory(app))
        .setJsonFactory(app.getOptions().getJsonFactory())
        .build();
  }

  static Builder builder() {
    return new Builder();
  }

  static final class Builder {

    private String projectId;
    private HttpRequestFactory requestFactory;
    private HttpRequestFactory childRequestFactory;
    private JsonFactory jsonFactory;
    private HttpResponseInterceptor responseInterceptor;

    private Builder() { }

    Builder setProjectId(String projectId) {
      this.projectId = projectId;
      return this;
    }

    Builder setRequestFactory(HttpRequestFactory requestFactory) {
      this.requestFactory = requestFactory;
      return this;
    }

    Builder setChildRequestFactory(HttpRequestFactory childRequestFactory) {
      this.childRequestFactory = childRequestFactory;
      return this;
    }

    Builder setJsonFactory(JsonFactory jsonFactory) {
      this.jsonFactory = jsonFactory;
      return this;
    }

    Builder setResponseInterceptor(HttpResponseInterceptor responseInterceptor) {
      this.responseInterceptor = responseInterceptor;
      return this;
    }

    FirebaseMessagingClientImpl build() {
      return new FirebaseMessagingClientImpl(this);
    }
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
    public void onFailure(MessagingServiceErrorResponse error, HttpHeaders responseHeaders) {
      // We only specify error codes and message for these partial failures. Recall that these
      // exceptions are never actually thrown, but only made accessible via SendResponse.
      FirebaseException base = createFirebaseException(error);
      FirebaseMessagingException exception = FirebaseMessagingException.withMessagingErrorCode(
          base, error.getMessagingErrorCode());
      responses.add(SendResponse.fromException(exception));
    }

    List<SendResponse> getResponses() {
      return this.responses.build();
    }

    private FirebaseException createFirebaseException(MessagingServiceErrorResponse error) {
      String status = error.getStatus();
      ErrorCode errorCode = Strings.isNullOrEmpty(status)
          ? ErrorCode.UNKNOWN : Enum.valueOf(ErrorCode.class, status);

      String msg = error.getErrorMessage();
      if (Strings.isNullOrEmpty(msg)) {
        msg = String.format("Unexpected HTTP response: %s", error.toString());
      }

      return new FirebaseException(errorCode, msg, null);
    }
  }

  private static class MessagingErrorHandler
      extends AbstractPlatformErrorHandler<FirebaseMessagingException> {

    private MessagingErrorHandler(JsonFactory jsonFactory) {
      super(jsonFactory);
    }

    @Override
    protected FirebaseMessagingException createException(FirebaseException base) {
      String response = getResponse(base);
      MessagingServiceErrorResponse parsed = safeParse(response);
      return FirebaseMessagingException.withMessagingErrorCode(
          base, parsed.getMessagingErrorCode());
    }

    private String getResponse(FirebaseException base) {
      if (base.getHttpResponse() == null) {
        return null;
      }

      return base.getHttpResponse().getContent();
    }

    private MessagingServiceErrorResponse safeParse(String response) {
      if (!Strings.isNullOrEmpty(response)) {
        try {
          return jsonFactory.createJsonParser(response)
              .parseAndClose(MessagingServiceErrorResponse.class);
        } catch (IOException ignore) {
          // Ignore any error that may occur while parsing the error response. The server
          // may have responded with a non-json payload.
        }
      }

      return new MessagingServiceErrorResponse();
    }
  }

  private static class MessagingBatchClient extends AbstractGoogleJsonClient {

    private static final String FCM_ROOT_URL = "https://fcm.googleapis.com";
    private static final String FCM_BATCH_PATH = "batch";
    private static final String FCM_BATCH_URL = String.format(
        "%s/%s", FCM_ROOT_URL, FCM_BATCH_PATH);

    MessagingBatchClient(HttpTransport transport, JsonFactory jsonFactory) {
      super(new Builder(transport, jsonFactory));
    }

    private MessagingBatchClient(Builder builder) {
      super(builder);
    }

    private static class Builder extends AbstractGoogleJsonClient.Builder {
      Builder(HttpTransport transport, JsonFactory jsonFactory) {
        super(transport, jsonFactory, FCM_ROOT_URL, "", null, false);
        setBatchPath(FCM_BATCH_PATH);
        setApplicationName("fire-admin-java");
      }

      @Override
      public AbstractGoogleJsonClient build() {
        return new MessagingBatchClient(this);
      }
    }
  }
}
