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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.core.ApiFuture;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.database.annotations.Nullable;
import com.google.firebase.internal.AbstractHttpErrorHandler;
import com.google.firebase.internal.ApiClientUtils;
import com.google.firebase.internal.CallableOperation;
import com.google.firebase.internal.ErrorHandlingHttpClient;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.HttpRequestInfo;
import com.google.firebase.internal.NonNull;

import java.util.Map;

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
  private final String projectId;
  private final ErrorHandlingHttpClient<FirebaseInstanceIdException> httpClient;

  private FirebaseInstanceId(FirebaseApp app) {
    this(app, null);
  }

  @VisibleForTesting
  FirebaseInstanceId(FirebaseApp app, @Nullable HttpRequestFactory requestFactory) {
    this.app = checkNotNull(app, "app must not be null");
    String projectId = ImplFirebaseTrampolines.getProjectId(app);
    checkArgument(!Strings.isNullOrEmpty(projectId),
        "Project ID is required to access instance ID service. Use a service account credential or "
            + "set the project ID explicitly via FirebaseOptions. Alternatively you can also "
            + "set the project ID via the GOOGLE_CLOUD_PROJECT environment variable.");
    this.projectId = projectId;
    if (requestFactory == null) {
      requestFactory = ApiClientUtils.newAuthorizedRequestFactory(app);
    }

    this.httpClient = new ErrorHandlingHttpClient<>(
        requestFactory,
        app.getOptions().getJsonFactory(),
        new InstanceIdErrorHandler());
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
    httpClient.setInterceptor(interceptor);
  }

  /**
   * Deletes the specified instance ID and the associated data from Firebase.
   *
   * <p>Note that Google Analytics for Firebase uses its own form of Instance ID to keep track of
   * analytics data. Therefore deleting a regular Instance ID does not delete Analytics data.
   * See <a href="https://firebase.google.com/support/privacy/manage-iids#delete_an_instance_id">
   * Delete an Instance ID</a> for more information.
   *
   * @param instanceId A non-null, non-empty instance ID string.
   * @throws IllegalArgumentException If the instance ID is null or empty.
   * @throws FirebaseInstanceIdException If an error occurs while deleting the instance ID.
   */
  public void deleteInstanceId(@NonNull String instanceId) throws FirebaseInstanceIdException {
    deleteInstanceIdOp(instanceId).call();
  }

  /**
   * Similar to {@link #deleteInstanceId(String)} but performs the operation asynchronously.
   *
   * @param instanceId A non-null, non-empty instance ID string.
   * @return An {@code ApiFuture} which will complete successfully when the instance ID is deleted,
   *     or unsuccessfully with the failure Exception.
   * @throws IllegalArgumentException If the instance ID is null or empty.
   */
  public ApiFuture<Void> deleteInstanceIdAsync(@NonNull String instanceId) {
    return deleteInstanceIdOp(instanceId).callAsync(app);
  }

  private CallableOperation<Void, FirebaseInstanceIdException> deleteInstanceIdOp(
      final String instanceId) {
    checkArgument(!Strings.isNullOrEmpty(instanceId), "instance ID must not be null or empty");
    return new CallableOperation<Void, FirebaseInstanceIdException>() {
      @Override
      protected Void execute() throws FirebaseInstanceIdException {
        String url = String.format(
            "%s/project/%s/instanceId/%s", IID_SERVICE_URL, projectId, instanceId);
        HttpRequestInfo request = HttpRequestInfo.buildDeleteRequest(url);
        httpClient.send(request);
        return null;
      }
    };
  }

  private static class InstanceIdErrorHandler
      extends AbstractHttpErrorHandler<FirebaseInstanceIdException> {

    @Override
    protected FirebaseInstanceIdException createException(FirebaseException base) {
      String message = base.getMessage();
      String customMessage = getCustomMessage(base);
      if (!Strings.isNullOrEmpty(customMessage)) {
        message = customMessage;
      }

      return new FirebaseInstanceIdException(base, message);
    }

    private String getCustomMessage(FirebaseException base) {
      IncomingHttpResponse response = base.getHttpResponse();
      if (response != null) {
        String instanceId = extractInstanceId(response);
        String description = ERROR_CODES.get(response.getStatusCode());
        if (description != null) {
          return String.format("Instance ID \"%s\": %s", instanceId, description);
        }
      }

      return null;
    }

    private String extractInstanceId(IncomingHttpResponse response) {
      String url = response.getRequest().getUrl();
      int index = url.lastIndexOf('/');
      return url.substring(index + 1);
    }
  }

  private static final String SERVICE_ID = FirebaseInstanceId.class.getName();

  private static class FirebaseInstanceIdService extends FirebaseService<FirebaseInstanceId> {

    FirebaseInstanceIdService(FirebaseApp app) {
      super(SERVICE_ID, new FirebaseInstanceId(app));
    }
  }
}
