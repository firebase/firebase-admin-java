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
import java.util.Map;
import java.util.concurrent.Callable;

public class FirebaseMessaging {

  private static final String FCM_URL = "https://fcm.googleapis.com/v1/projects/%s/messages:send";

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
      public String call() throws Exception {
        HttpRequest request = requestFactory.buildPostRequest(
            new GenericUrl(url), new JsonHttpContent(jsonFactory,
                ImmutableMap.of("message", message)));
        request.setParser(new JsonObjectParser(jsonFactory));
        request.setResponseInterceptor(interceptor);
        HttpResponse response = null;
        try {
          response = request.execute();
          Map map = response.parseAs(Map.class);
          return (String) map.get("name");
        } catch (Exception e) {
          throw new RuntimeException(e);
        } finally {
          if (response != null) {
            response.disconnect();
          }
        }
      }
    });
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
