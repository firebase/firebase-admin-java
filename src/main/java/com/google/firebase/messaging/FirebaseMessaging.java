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
import com.google.api.client.util.Key;
import com.google.api.core.ApiFuture;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.TaskToApiFuture;
import com.google.firebase.tasks.Task;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class FirebaseMessaging {

  private static final String FCM_URL = "https://fcm.googleapis.com/v1/projects/%s/messages:send";

  private static final String INTERNAL_ERROR = "internal-error";
  private static final Map<String, String> ERROR_CODES = ImmutableMap.<String, String>builder()
      .put("INVALID_ARGUMENT", "invalid-argument")
      .put("NOT_FOUND", "registration-token-not-registered")
      .put("PERMISSION_DENIED", "authentication-error")
      .put("RESOURCE_EXHAUSTED", "message-rate-exceeded")
      .put("UNAUTHENTICATED", "authentication-error")
      .put("UNAVAILABLE", "server-unavailable")
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
    GoogleCredentials credentials = ImplFirebaseTrampolines.getCredentials(app);
    this.app = app;
    this.requestFactory = httpTransport.createRequestFactory(
        new HttpCredentialsAdapter(credentials));
    this.jsonFactory = app.getOptions().getJsonFactory();
    String projectId = ImplFirebaseTrampolines.getProjectId(app);
    checkArgument(!Strings.isNullOrEmpty(projectId),
        "Project ID is required to access messaging service. Use a service account credential or "
            + "set the project ID explicitly via FirebaseOptions. Alternatively you can also "
            + "set the project ID via the GCLOUD_PROJECT environment variable.");
    this.url = String.format(FCM_URL, projectId);
  }

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

  public ApiFuture<String> sendAsync(Message message) {
    return sendAsync(message, false);
  }

  public ApiFuture<String> sendAsync(Message message, boolean dryRun) {
    return new TaskToApiFuture<>(send(message, dryRun));
  }

  private Task<String> send(final Message message, final boolean dryRun) {
    checkNotNull(message, "message must not be null");
    return ImplFirebaseTrampolines.submitCallable(app, new Callable<String>() {
      @Override
      public String call() throws FirebaseMessagingException {
        return makeSendRequest(message, dryRun);
      }
    });
  }

  public ApiFuture<TopicManagementResponse> subscribeToTopicAsync(
      List<String> registrationTokens, String topic) {
    return new TaskToApiFuture<>(subscribeToTopic(registrationTokens, topic));
  }

  private Task<TopicManagementResponse> subscribeToTopic(
      final List<String> registrationTokens, final String topic) {
    checkRegistrationTokens(registrationTokens);
    checkTopic(topic);

    return ImplFirebaseTrampolines.submitCallable(app, new Callable<TopicManagementResponse>() {
      @Override
      public TopicManagementResponse call() throws FirebaseMessagingException {
        return makeTopicManagementRequest(registrationTokens, topic, IID_SUBSCRIBE_PATH);
      }
    });
  }

  public ApiFuture<TopicManagementResponse> unsubscribeFromTopicAsync(
      List<String> registrationTokens, String topic) {
    return new TaskToApiFuture<>(unsubscribeFromTopic(registrationTokens, topic));
  }

  private Task<TopicManagementResponse> unsubscribeFromTopic(
      final List<String> registrationTokens, final String topic) {
    checkRegistrationTokens(registrationTokens);
    checkTopic(topic);

    return ImplFirebaseTrampolines.submitCallable(app, new Callable<TopicManagementResponse>() {
      @Override
      public TopicManagementResponse call() throws FirebaseMessagingException {
        return makeTopicManagementRequest(registrationTokens, topic, IID_UNSUBSCRIBE_PATH);
      }
    });
  }

  private String makeSendRequest(Message message,
      boolean dryRun) throws FirebaseMessagingException {
    ImmutableMap.Builder<String, Object> payload = ImmutableMap.<String, Object>builder()
        .put("message", message);
    if (dryRun) {
      payload.put("validate_only", true);
    }
    HttpResponse response = null;
    try {
      HttpRequest request = requestFactory.buildPostRequest(
          new GenericUrl(url), new JsonHttpContent(jsonFactory, payload.build()));
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
          INTERNAL_ERROR, "Error while calling IID backend service", e);
    } finally {
      disconnectQuietly(response);
    }
  }

  private void handleSendHttpError(HttpResponseException e) throws FirebaseMessagingException {
    try {
      Map response = jsonFactory.fromString(e.getContent(), Map.class);
      Map error = (Map) response.get("error");
      if (error != null) {
        String status = (String) error.get("status");
        String code = ERROR_CODES.get(status);
        if (code != null) {
          String message = (String) error.get("message");
          throw new FirebaseMessagingException(code, message, e);
        }
      }
    } catch (IOException ignored) {
      // ignored
    }
    String msg = String.format(
        "Unexpected HTTP response with status: %d; body: %s", e.getStatusCode(), e.getContent());
    throw new FirebaseMessagingException(INTERNAL_ERROR, msg, e);
  }

  private TopicManagementResponse makeTopicManagementRequest(List<String> registrationTokens,
      String topic, String path) throws FirebaseMessagingException {
    Map<String, Object> payload = ImmutableMap.of(
        "to", topic,
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

  private void handleTopicManagementHttpError(
      HttpResponseException e) throws FirebaseMessagingException {
    try {
      Map response = jsonFactory.fromString(e.getContent(), Map.class);
      String error = (String) response.get("error");
      if (!Strings.isNullOrEmpty(error)) {
        throw new FirebaseMessagingException(INTERNAL_ERROR, error, e);
      }
    } catch (IOException ignored) {
      // ignored
    }
    String msg = String.format(
        "Unexpected HTTP response with status: %d; body: %s", e.getStatusCode(), e.getContent());
    throw new FirebaseMessagingException(INTERNAL_ERROR, msg, e);
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
      // NOTE: We don't explicitly tear down anything here, but public methods of StorageClient
      // will now fail because calls to getOptions() and getToken() will hit FirebaseApp,
      // which will throw once the app is deleted.
    }
  }

  private static class MessagingServiceResponse {
    @Key("name")
    private String name;
  }

  private static class InstanceIdServiceResponse {
    @Key("results")
    private List<Map<String, Object>> results;
  }
}
