package com.google.firebase.messaging;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.core.ApiFuture;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.TaskToApiFuture;
import com.google.firebase.tasks.Task;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class FirebaseMessaging {

  private static final String FCM_URL = "https://fcm.googleapis.com/v1/projects/%s/messages:send";

  private static final String IID_HOST = "https://iid.googleapis.com";
  private static final String IID_SUBSCRIBE_PATH = "iid/v1:batchAdd";

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
   * Gets the {@link FirebaseInstanceId} instance for the specified {@link FirebaseApp}.
   *
   * @return The {@link FirebaseInstanceId} instance for the specified {@link FirebaseApp}.
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
    return new TaskToApiFuture<>(send(message));
  }

  private Task<String> send(final Message message) {
    checkNotNull(message, "message must not be null");
    return ImplFirebaseTrampolines.submitCallable(app, new Callable<String>() {
      @Override
      public String call() throws FirebaseMessagingException {
        try {
          return makeSendRequest(message);
        } catch (Exception e) {
          throw new FirebaseMessagingException("Error while calling FCM service", e);
        }
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
        try {
          return makeTopicManagementRequest(registrationTokens, topic, IID_SUBSCRIBE_PATH);
        } catch (IOException e) {
          throw new FirebaseMessagingException("Error while calling IID service", e);
        }
      }
    });
  }

  private String makeSendRequest(Message message) throws IOException {
    HttpRequest request = requestFactory.buildPostRequest(
        new GenericUrl(url), new JsonHttpContent(jsonFactory, ImmutableMap.of("message", message)));
    request.setParser(new JsonObjectParser(jsonFactory));
    request.setResponseInterceptor(interceptor);
    HttpResponse response = null;
    try {
      response = request.execute();
      Map map = response.parseAs(Map.class);
      return (String) map.get("name");
    } finally {
      if (response != null) {
        response.disconnect();
      }
    }
  }

  private TopicManagementResponse makeTopicManagementRequest(
      List<String> registrationTokens, String topic, String path) throws IOException {
    Map<String, Object> payload = ImmutableMap.of(
        "to", topic,
        "registration_tokens", registrationTokens
    );

    final String url = String.format("%s/%s", IID_HOST, path);
    HttpRequest request = requestFactory.buildPostRequest(
        new GenericUrl(url), new JsonHttpContent(jsonFactory, payload));
    request.getHeaders().set("access_token_auth", "true");
    request.setParser(new JsonObjectParser(jsonFactory));
    request.setResponseInterceptor(interceptor);
    HttpResponse response = null;
    try {
      response = request.execute();
      Map parsed = response.parseAs(Map.class);
      List<Map> results = (List<Map>) parsed.get("results");
      if (results == null || results.isEmpty()) {
        throw new IOException("Unexpected topic management response");
      }
      return new TopicManagementResponse(results);
    } finally {
      if (response != null) {
        response.disconnect();
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
}
