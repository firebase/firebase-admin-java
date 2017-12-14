/*
 * Copyright 2017 Google Inc.
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

package com.google.firebase.iid;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.core.ApiFuture;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.TaskToApiFuture;
import com.google.firebase.tasks.Task;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * This class is the entry point for all server-side Firebase Instance ID actions.
 *
 * <p>Enables deleting instance IDs associated with Firebase projects.
 */
public class FirebaseInstanceId {

  private static final String IID_SERVICE_URL = "https://console.firebase.google.com/v1";

  private static final Map<Integer, String> ERROR_CODES = ImmutableMap.<Integer, String>builder()
      .put(400, "Malformed instance ID argument.")
      .put(401, "Request not authorized.")
      .put(403, "Project does not match instance ID or the client does not"
          + " have sufficient privileges.")
      .put(404, "Failed to find the instance ID.")
      .put(409, "Already deleted.")
      .put(429, "Request throttled out by the backend server.")
      .put(500, "Internal server error.")
      .put(503, "Backend servers are over capacity. Try again later.")
      .build();

  private final FirebaseApp app;
  private final HttpRequestFactory requestFactory;
  private final JsonFactory jsonFactory;
  private final String projectId;

  private HttpResponseInterceptor interceptor;

  private FirebaseInstanceId(FirebaseApp app) {
    HttpTransport httpTransport = app.getOptions().getHttpTransport();
    GoogleCredentials credentials = ImplFirebaseTrampolines.getCredentials(app);
    this.app = app;
    this.requestFactory = httpTransport.createRequestFactory(
        new HttpCredentialsAdapter(credentials));
    this.jsonFactory = app.getOptions().getJsonFactory();
    this.projectId = ImplFirebaseTrampolines.getProjectId(app);
    checkArgument(!Strings.isNullOrEmpty(projectId),
        "Project ID is required to access instance ID service. Use a service account credential or "
            + "set the project ID explicitly via FirebaseOptions. Alternatively you can also "
            + "set the project ID via the GCLOUD_PROJECT environment variable.");
  }

  /**
   * Gets the {@link FirebaseInstanceId} instance for the default {@link FirebaseApp}.
   *
   * @return The {@link FirebaseInstanceId} instance for the default {@link FirebaseApp}.
   */
  public static FirebaseInstanceId getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }

  /**
   * Gets the {@link FirebaseInstanceId} instance for the specified {@link FirebaseApp}.
   *
   * @return The {@link FirebaseInstanceId} instance for the specified {@link FirebaseApp}.
   */
  public static synchronized FirebaseInstanceId getInstance(FirebaseApp app) {
    FirebaseInstanceIdService service = ImplFirebaseTrampolines.getService(app, SERVICE_ID,
        FirebaseInstanceIdService.class);
    if (service == null) {
      service = ImplFirebaseTrampolines.addService(app, new FirebaseInstanceIdService(app));
    }
    return service.getInstance();
  }

  @VisibleForTesting
  void setInterceptor(HttpResponseInterceptor interceptor) {
    this.interceptor = interceptor;
  }

  /**
   * Deletes the specified instance ID from Firebase.
   *
   * <p>This can be used to delete an instance ID and associated user data from a Firebase project,
   * pursuant to the General Data Protection Regulation (GDPR).
   *
   * @param instanceId A non-null, non-empty instance ID string.
   * @return An {@code ApiFuture} which will complete successfully when the instance ID is deleted,
   *     or unsuccessfully with the failure Exception..
   */
  public ApiFuture<Void> deleteInstanceIdAsync(@NonNull String instanceId) {
    return new TaskToApiFuture<>(deleteInstanceId(instanceId));
  }

  private Task<Void> deleteInstanceId(final String instanceId) {
    checkArgument(!Strings.isNullOrEmpty(instanceId), "instance ID must not be null or empty");
    return ImplFirebaseTrampolines.submitCallable(app, new Callable<Void>(){
      @Override
      public Void call() throws Exception {
        String url = String.format(
            "%s/project/%s/instanceId/%s", IID_SERVICE_URL, projectId, instanceId);
        HttpRequest request = requestFactory.buildDeleteRequest(new GenericUrl(url));
        request.setParser(new JsonObjectParser(jsonFactory));
        request.setResponseInterceptor(interceptor);
        HttpResponse response = null;
        try {
          response = request.execute();
          ByteStreams.exhaust(response.getContent());
        } catch (Exception e) {
          handleError(instanceId, e);
        } finally {
          if (response != null) {
            response.disconnect();
          }
        }
        return null;
      }
    });
  }

  private void handleError(String instanceId, Exception e) throws FirebaseInstanceIdException {
    String msg = "Error while invoking instance ID service.";
    if (e instanceof HttpResponseException) {
      int statusCode = ((HttpResponseException) e).getStatusCode();
      if (ERROR_CODES.containsKey(statusCode)) {
        msg = String.format("Instance ID \"%s\": %s", instanceId, ERROR_CODES.get(statusCode));
      }
    }
    throw new FirebaseInstanceIdException(msg, e);
  }

  private static final String SERVICE_ID = FirebaseInstanceId.class.getName();

  private static class FirebaseInstanceIdService extends FirebaseService<FirebaseInstanceId> {

    FirebaseInstanceIdService(FirebaseApp app) {
      super(SERVICE_ID, new FirebaseInstanceId(app));
    }

    @Override
    public void destroy() {
      // NOTE: We don't explicitly tear down anything here, but public methods of StorageClient
      // will now fail because calls to getOptions() and getToken() will hit FirebaseApp,
      // which will throw once the app is deleted.
    }
  }
}
