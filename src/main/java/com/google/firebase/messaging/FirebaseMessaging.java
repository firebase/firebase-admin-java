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

package com.google.firebase.messaging;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.JsonParser;
import com.google.api.client.util.Key;
import com.google.api.core.ApiFuture;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.internal.CallableOperation;
import com.google.firebase.internal.FirebaseRequestInitializer;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.NonNull;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * This class is the entry point for all server-side Firebase Cloud Messaging actions.
 *
 * <p>You can get an instance of FirebaseMessaging via {@link #getInstance(FirebaseApp)}, and
 * then use it to send messages or manage FCM topic subscriptions.
 */
public class FirebaseMessaging {

  private static final String FCM_URL = "https://fcm.googleapis.com/v1/projects/%s/messages:send";
  private static final String FCM_ERROR_TYPE =
      "type.googleapis.com/google.firebase.fcm.v1.FcmError";

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

  private static final String IID_HOST = "https://iid.googleapis.com";
  private static final String IID_SUBSCRIBE_PATH = "iid/v1:batchAdd";
  private static final String IID_UNSUBSCRIBE_PATH = "iid/v1:batchRemove";

  private final FirebaseApp app;
  private final HttpRequestFactory requestFactory;
  private final JsonFactory jsonFactory;
  private final String url;

  private HttpResponseInterceptor interceptor;

  private FirebaseMessaging(FirebaseApp app) {
    HttpTransport httpTransport = app.getOptions().getHttpTransport();
    this.app = app;
    this.requestFactory = httpTransport.createRequestFactory(new FirebaseRequestInitializer(app));
    this.jsonFactory = app.getOptions().getJsonFactory();
    String projectId = ImplFirebaseTrampolines.getProjectId(app);
    checkArgument(!Strings.isNullOrEmpty(projectId),
        "Project ID is required to access messaging service. Use a service account credential or "
            + "set the project ID explicitly via FirebaseOptions. Alternatively you can also "
            + "set the project ID via the GOOGLE_CLOUD_PROJECT environment variable.");
    this.url = String.format(FCM_URL, projectId);
  }

  /**
   * Gets the {@link FirebaseMessaging} instance for the default {@link FirebaseApp}.
   *
   * @return The {@link FirebaseMessaging} instance for the default {@link FirebaseApp}.
   */
  public static FirebaseMessaging getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }

  /**
   * Gets the {@link FirebaseMessaging} instance for the specified {@link FirebaseApp}.
   *
   * @return The {@link FirebaseMessaging} instance for the specified {@link FirebaseApp}.
   */
  public static synchronized FirebaseMessaging getInstance(FirebaseApp app) {
    FirebaseMessagingService service = ImplFirebaseTrampolines.getService(app, SERVICE_ID,
        FirebaseMessagingService.class);
    if (service == null) {
      service = ImplFirebaseTrampolines.addService(app, new FirebaseMessagingService(app));
    }
    return service.getInstance();
  }

  /**
   * Sends the given {@link Message} via Firebase Cloud Messaging.
   *
   * @param message A non-null {@link Message} to be sent.
   * @return A message ID string.
   */
  public String send(@NonNull Message message) throws FirebaseMessagingException {
    return send(message, false);
  }

  /**
   * Sends the given {@link Message} via Firebase Cloud Messaging.
   *
   * <p>If the {@code dryRun} option is set to true, the message will not be actually sent. Instead
   * FCM performs all the necessary validations, and emulates the send operation.
   *
   * @param message A non-null {@link Message} to be sent.
   * @param dryRun a boolean indicating whether to perform a dry run (validation only) of the send.
   * @return A message ID string.
   */
  public String send(@NonNull Message message, boolean dryRun) throws FirebaseMessagingException {
    return sendOp(message, dryRun).call();
  }

  /**
   * Similar to {@link #send(Message)} but performs the operation asynchronously.
   *
   * @param message A non-null {@link Message} to be sent.
   * @return An {@code ApiFuture} that will complete with a message ID string when the message
   *     has been sent.
   */
  public ApiFuture<String> sendAsync(@NonNull Message message) {
    return sendAsync(message, false);
  }

  /**
   * Similar to {@link #send(Message, boolean)} but performs the operation asynchronously.
   *
   * @param message A non-null {@link Message} to be sent.
   * @param dryRun a boolean indicating whether to perform a dry run (validation only) of the send.
   * @return An {@code ApiFuture} that will complete with a message ID string when the message
   *     has been sent, or when the emulation has finished.
   */
  public ApiFuture<String> sendAsync(@NonNull Message message, boolean dryRun) {
    return sendOp(message, dryRun).callAsync(app);
  }

  /**
   * Subscribes a list of registration tokens to a topic.
   *
   * @param registrationTokens A non-null, non-empty list of device registration tokens, with at
   *     most 1000 entries.
   * @param topic Name of the topic to subscribe to. May contain the {@code /topics/} prefix.
   * @return A {@link TopicManagementResponse}.
   */
  public TopicManagementResponse subscribeToTopic(@NonNull List<String> registrationTokens,
      @NonNull String topic) throws FirebaseMessagingException {
    return manageTopicOp(registrationTokens, topic, IID_SUBSCRIBE_PATH).call();
  }

  /**
   * Similar to {@link #subscribeToTopic(List, String)} but performs the operation asynchronously.
   *
   * @param registrationTokens A non-null, non-empty list of device registration tokens, with at
   *     most 1000 entries.
   * @param topic Name of the topic to subscribe to. May contain the {@code /topics/} prefix.
   * @return An {@code ApiFuture} that will complete with a {@link TopicManagementResponse}.
   */
  public ApiFuture<TopicManagementResponse> subscribeToTopicAsync(
      @NonNull List<String> registrationTokens, @NonNull String topic) {
    return manageTopicOp(registrationTokens, topic, IID_SUBSCRIBE_PATH).callAsync(app);
  }

  /**
   * Unubscribes a list of registration tokens from a topic.
   *
   * @param registrationTokens A non-null, non-empty list of device registration tokens, with at
   *     most 1000 entries.
   * @param topic Name of the topic to unsubscribe from. May contain the {@code /topics/} prefix.
   * @return A {@link TopicManagementResponse}.
   */
  public TopicManagementResponse unsubscribeFromTopic(@NonNull List<String> registrationTokens,
      @NonNull String topic) throws FirebaseMessagingException {
    return manageTopicOp(registrationTokens, topic, IID_UNSUBSCRIBE_PATH).call();
  }

  /**
   * Similar to {@link #unsubscribeFromTopic(List, String)} but performs the operation
   * asynchronously.
   *
   * @param registrationTokens A non-null, non-empty list of device registration tokens, with at
   *     most 1000 entries.
   * @param topic Name of the topic to unsubscribe from. May contain the {@code /topics/} prefix.
   * @return An {@code ApiFuture} that will complete with a {@link TopicManagementResponse}.
   */
  public ApiFuture<TopicManagementResponse> unsubscribeFromTopicAsync(
      @NonNull List<String> registrationTokens, @NonNull String topic) {
    return manageTopicOp(registrationTokens, topic, IID_UNSUBSCRIBE_PATH)
        .callAsync(app);
  }

  private CallableOperation<String, FirebaseMessagingException> sendOp(
      final Message message, final boolean dryRun) {
    checkNotNull(message, "message must not be null");
    return new CallableOperation<String, FirebaseMessagingException>() {
      @Override
      protected String execute() throws FirebaseMessagingException {
        ImmutableMap.Builder<String, Object> payload = ImmutableMap.<String, Object>builder()
            .put("message", message);
        if (dryRun) {
          payload.put("validate_only", true);
        }
        HttpResponse response = null;
        try {
          HttpRequest request = requestFactory.buildPostRequest(
              new GenericUrl(url), new JsonHttpContent(jsonFactory, payload.build()));
          request.getHeaders().set("X-GOOG-API-FORMAT-VERSION", "2");
          request.setParser(new JsonObjectParser(jsonFactory));
          request.setResponseInterceptor(interceptor);
          response = request.execute();
          MessagingServiceResponse parsed = new MessagingServiceResponse();
          jsonFactory.createJsonParser(response.getContent()).parseAndClose(parsed);
          return parsed.name;
        } catch (HttpResponseException e) {
          handleSendHttpError(e);
          return null;
        } catch (IOException e) {
          throw new FirebaseMessagingException(
              INTERNAL_ERROR, "Error while calling FCM backend service", e);
        } finally {
          disconnectQuietly(response);
        }
      }
    };
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
    String code = FCM_ERROR_CODES.get(response.getErrorCode());
    if (code == null) {
      code = UNKNOWN_ERROR;
    }
    String msg = response.getErrorMessage();
    if (Strings.isNullOrEmpty(msg)) {
      msg = String.format("Unexpected HTTP response with status: %d; body: %s",
          e.getStatusCode(), e.getContent());
    }
    throw new FirebaseMessagingException(code, msg, e);
  }

  private CallableOperation<TopicManagementResponse, FirebaseMessagingException>
      manageTopicOp(
          final List<String> registrationTokens, final String topic, final String path) {
    checkRegistrationTokens(registrationTokens);
    checkTopic(topic);
    return new CallableOperation<TopicManagementResponse, FirebaseMessagingException>() {
      @Override
      protected TopicManagementResponse execute() throws FirebaseMessagingException {
        final String prefixedTopic;
        if (topic.startsWith("/topics/")) {
          prefixedTopic = topic;
        } else {
          prefixedTopic = "/topics/" + topic;
        }
        Map<String, Object> payload = ImmutableMap.of(
            "to", prefixedTopic,
            "registration_tokens", registrationTokens
        );

        final String url = String.format("%s/%s", IID_HOST, path);
        HttpResponse response = null;
        try {
          HttpRequest request = requestFactory.buildPostRequest(
              new GenericUrl(url), new JsonHttpContent(jsonFactory, payload));
          request.getHeaders().set("access_token_auth", "true");
          request.setParser(new JsonObjectParser(jsonFactory));
          request.setResponseInterceptor(interceptor);
          response = request.execute();
          InstanceIdServiceResponse parsed = new InstanceIdServiceResponse();
          jsonFactory.createJsonParser(response.getContent()).parseAndClose(parsed);
          checkState(parsed.results != null && !parsed.results.isEmpty(),
              "unexpected response from topic management service");
          return new TopicManagementResponse(parsed.results);
        } catch (HttpResponseException e) {
          handleTopicManagementHttpError(e);
          return null;
        } catch (IOException e) {
          throw new FirebaseMessagingException(
              INTERNAL_ERROR, "Error while calling IID backend service", e);
        } finally {
          disconnectQuietly(response);
        }
      }
    };
  }

  private void handleTopicManagementHttpError(
      HttpResponseException e) throws FirebaseMessagingException {
    InstanceIdServiceErrorResponse response = new InstanceIdServiceErrorResponse();
    try {
      JsonParser parser = jsonFactory.createJsonParser(e.getContent());
      parser.parseAndClose(response);
    } catch (IOException ignored) {
      // ignored
    }

    // Infer error code from HTTP status
    String code = IID_ERROR_CODES.get(e.getStatusCode());
    if (code == null) {
      code = UNKNOWN_ERROR;
    }
    String msg = response.error;
    if (Strings.isNullOrEmpty(msg)) {
      msg = String.format("Unexpected HTTP response with status: %d; body: %s",
          e.getStatusCode(), e.getContent());
    }
    throw new FirebaseMessagingException(code, msg, e);
  }

  private static void disconnectQuietly(HttpResponse response) {
    if (response != null) {
      try {
        response.disconnect();
      } catch (IOException ignored) {
        // ignored
      }
    }
  }

  private static void checkRegistrationTokens(List<String> registrationTokens) {
    checkArgument(registrationTokens != null && !registrationTokens.isEmpty(),
        "registrationTokens list must not be null or empty");
    checkArgument(registrationTokens.size() <= 1000,
        "registrationTokens list must not contain more than 1000 elements");
    for (String token : registrationTokens) {
      checkArgument(!Strings.isNullOrEmpty(token),
          "registration tokens list must not contain null or empty strings");
    }
  }

  private static void checkTopic(String topic) {
    checkArgument(!Strings.isNullOrEmpty(topic), "topic must not be null or empty");
    checkArgument(topic.matches("^(/topics/)?(private/)?[a-zA-Z0-9-_.~%]+$"), "invalid topic name");
  }

  @VisibleForTesting
  void setInterceptor(HttpResponseInterceptor interceptor) {
    this.interceptor = interceptor;
  }

  private static final String SERVICE_ID = FirebaseMessaging.class.getName();

  private static class FirebaseMessagingService extends FirebaseService<FirebaseMessaging> {

    FirebaseMessagingService(FirebaseApp app) {
      super(SERVICE_ID, new FirebaseMessaging(app));
    }

    @Override
    public void destroy() {
      // NOTE: We don't explicitly tear down anything here, but public methods of FirebaseMessaging
      // will now fail because calls to getOptions() and getToken() will hit FirebaseApp,
      // which will throw once the app is deleted.
    }
  }

  private static class MessagingServiceResponse {
    @Key("name")
    private String name;
  }

  private static class MessagingServiceErrorResponse {
    @Key("error")
    private Map<String, Object> error;


    String getErrorCode() {
      if (error == null) {
        return null;
      }
      Object details = error.get("details");
      if (details != null && details instanceof List) {
        for (Object detail : (List) details) {
          if (detail instanceof Map) {
            Map detailMap = (Map) detail;
            if (FCM_ERROR_TYPE.equals(detailMap.get("@type"))) {
              return (String) detailMap.get("errorCode");
            }
          }
        }
      }
      return (String) error.get("status");
    }

    String getErrorMessage() {
      if (error != null) {
        return (String) error.get("message");
      }
      return null;
    }
  }

  private static class InstanceIdServiceResponse {
    @Key("results")
    private List<Map<String, Object>> results;
  }

  private static class InstanceIdServiceErrorResponse {
    @Key("error")
    private String error;
  }
}
