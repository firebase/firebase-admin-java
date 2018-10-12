/* Copyright 2018 Google Inc.
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

package com.google.firebase.projectmanagement;

import static com.google.api.core.ApiFutures.transform;
import static com.google.api.core.ApiFutures.transformAsync;
import static com.google.common.base.Preconditions.checkArgument;

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
import com.google.api.client.util.Base64;
import com.google.api.client.util.Key;
import com.google.api.core.ApiAsyncFunction;
import com.google.api.core.ApiFunction;
import com.google.api.core.ApiFuture;
import com.google.api.core.SettableApiFuture;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.internal.CallableOperation;
import com.google.firebase.internal.FirebaseRequestInitializer;
import com.google.firebase.internal.FirebaseScheduledExecutor;
import com.google.firebase.internal.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class FirebaseProjectManagementServiceImpl implements AndroidAppService, IosAppService {
  @VisibleForTesting static final String FIREBASE_PROJECT_MANAGEMENT_URL =
      "https://firebase.googleapis.com";
  @VisibleForTesting static final int MAXIMUM_LIST_APPS_PAGE_SIZE = 100;
  @VisibleForTesting static final String PATCH_OVERRIDE_KEY = "X-HTTP-Method-Override";
  @VisibleForTesting static final String PATCH_OVERRIDE_VALUE = "PATCH";

  private static final int MAXIMUM_POLLING_ATTEMPTS = 8;
  private static final long POLL_BASE_WAIT_TIME_MILLIS = 500L;
  private static final double POLL_EXPONENTIAL_BACKOFF_FACTOR = 1.5;
  private static final String ANDROID_APPS_RESOURCE_NAME = "androidApps";
  private static final String IOS_APPS_RESOURCE_NAME = "iosApps";
  private static final String ANDROID_NAMESPACE_PROPERTY = "package_name";
  private static final String IOS_NAMESPACE_PROPERTY = "bundle_id";
  private static final ImmutableMap<Integer, String> ERROR_CODES =
      ImmutableMap.<Integer, String>builder()
          .put(401, "Request not authorized.")
          .put(403, "Client does not have sufficient privileges.")
          .put(404, "Failed to find the App.")
          .put(429, "Request throttled by the backend server.")
          .put(500, "Internal server error.")
          .put(503, "Backend servers are over capacity. Try again later.")
          .build();

  private final FirebaseApp app;
  private final JsonFactory jsonFactory;
  private final HttpRequestFactory requestFactory;
  private final ScheduledExecutorService scheduledExecutor;

  private final CreateAndroidAppFromAppIdFunction createAndroidAppFromAppIdFunction =
      new CreateAndroidAppFromAppIdFunction();
  private final CreateIosAppFromAppIdFunction createIosAppFromAppIdFunction =
      new CreateIosAppFromAppIdFunction();

  private HttpResponseInterceptor interceptor;

  FirebaseProjectManagementServiceImpl(FirebaseApp app) {
    this.app = app;

    JsonFactory jsonFactory = app.getOptions().getJsonFactory();
    this.jsonFactory = jsonFactory;

    HttpTransport httpTransport = app.getOptions().getHttpTransport();
    this.requestFactory =
        httpTransport.createRequestFactory(new FirebaseRequestInitializer(app));

    this.scheduledExecutor = new FirebaseScheduledExecutor(
        ImplFirebaseTrampolines.getThreadFactory(app), "firebase-project-management");
  }

  @VisibleForTesting
  void setInterceptor(HttpResponseInterceptor interceptor) {
    this.interceptor = interceptor;
  }

  void destroy() {
    // NOTE: Any instance of IosApp, AndroidApp, or FirebaseProjectManagement that depends on this
    // instance will no longer be able to make RPC calls. All polling or waiting iOS or Android App
    // creations will be interrupted, even though the initial creation RPC (if made successfully) is
    // still processed normally (asynchronously) by the server.
    scheduledExecutor.shutdownNow();
  }

  /* getAndroidApp */

  @Override
  public AndroidAppMetadata getAndroidApp(String appId) throws FirebaseProjectManagementException {
    return getAndroidAppOp(appId).call();
  }

  @Override
  public ApiFuture<AndroidAppMetadata> getAndroidAppAsync(String appId) {
    return getAndroidAppOp(appId).callAsync(app);
  }

  private CallableOperation<AndroidAppMetadata, FirebaseProjectManagementException> getAndroidAppOp(
      final String appId) {
    return new CallableOperation<AndroidAppMetadata, FirebaseProjectManagementException>() {
      @Override
      protected AndroidAppMetadata execute() throws FirebaseProjectManagementException {
        String url = String.format(
            "%s/v1beta1/projects/-/androidApps/%s", FIREBASE_PROJECT_MANAGEMENT_URL, appId);
        AndroidAppResponse parsedResponse = new AndroidAppResponse();
        makeGetRequest(url, parsedResponse, appId, "App ID");
        return new AndroidAppMetadata(
            parsedResponse.name,
            parsedResponse.appId,
            Strings.nullToEmpty(parsedResponse.displayName),
            parsedResponse.projectId,
            parsedResponse.packageName);
      }
    };
  }

  /* getIosApp */

  @Override
  public IosAppMetadata getIosApp(String appId) throws FirebaseProjectManagementException {
    return getIosAppOp(appId).call();
  }

  @Override
  public ApiFuture<IosAppMetadata> getIosAppAsync(String appId) {
    return getIosAppOp(appId).callAsync(app);
  }

  private CallableOperation<IosAppMetadata, FirebaseProjectManagementException> getIosAppOp(
      final String appId) {
    return new CallableOperation<IosAppMetadata, FirebaseProjectManagementException>() {
      @Override
      protected IosAppMetadata execute() throws FirebaseProjectManagementException {
        String url = String.format(
            "%s/v1beta1/projects/-/iosApps/%s", FIREBASE_PROJECT_MANAGEMENT_URL, appId);
        IosAppResponse parsedResponse = new IosAppResponse();
        makeGetRequest(url, parsedResponse, appId, "App ID");
        return new IosAppMetadata(
            parsedResponse.name,
            parsedResponse.appId,
            Strings.nullToEmpty(parsedResponse.displayName),
            parsedResponse.projectId,
            parsedResponse.bundleId);
      }
    };
  }

  /* listAndroidApps, listIosApps */

  @Override
  public List<AndroidApp> listAndroidApps(String projectId)
      throws FirebaseProjectManagementException {
    return listAndroidAppsOp(projectId).call();
  }

  @Override
  public ApiFuture<List<AndroidApp>> listAndroidAppsAsync(String projectId) {
    return listAndroidAppsOp(projectId).callAsync(app);
  }

  @Override
  public List<IosApp> listIosApps(String projectId) throws FirebaseProjectManagementException {
    return listIosAppsOp(projectId).call();
  }

  @Override
  public ApiFuture<List<IosApp>> listIosAppsAsync(String projectId) {
    return listIosAppsOp(projectId).callAsync(app);
  }

  private CallableOperation<List<AndroidApp>, FirebaseProjectManagementException> listAndroidAppsOp(
      String projectId) {
    return listAppsOp(projectId, ANDROID_APPS_RESOURCE_NAME, createAndroidAppFromAppIdFunction);
  }

  private CallableOperation<List<IosApp>, FirebaseProjectManagementException> listIosAppsOp(
      String projectId) {
    return listAppsOp(projectId, IOS_APPS_RESOURCE_NAME, createIosAppFromAppIdFunction);
  }

  private <T> CallableOperation<List<T>, FirebaseProjectManagementException> listAppsOp(
      final String projectId,
      final String platformResourceName,
      final CreateAppFromAppIdFunction<T> createAppFromAppIdFunction) {
    return new CallableOperation<List<T>, FirebaseProjectManagementException>() {
      @Override
      protected List<T> execute() throws FirebaseProjectManagementException {
        String url = String.format(
            "%s/v1beta1/projects/%s/%s?page_size=%d",
            FIREBASE_PROJECT_MANAGEMENT_URL,
            projectId,
            platformResourceName,
            MAXIMUM_LIST_APPS_PAGE_SIZE);
        ImmutableList.Builder<T> builder = ImmutableList.<T>builder();
        ListAppsResponse parsedResponse;
        do {
          parsedResponse = new ListAppsResponse();
          makeGetRequest(url, parsedResponse, projectId, "Project ID");
          if (parsedResponse.apps == null) {
            break;
          }
          for (AppResponse app : parsedResponse.apps) {
            builder.add(createAppFromAppIdFunction.apply(app.appId));
          }
          url = String.format(
              "%s/v1beta1/projects/%s/%s?page_token=%s&page_size=%d",
              FIREBASE_PROJECT_MANAGEMENT_URL,
              projectId,
              platformResourceName,
              parsedResponse.nextPageToken,
              MAXIMUM_LIST_APPS_PAGE_SIZE);
        } while (!Strings.isNullOrEmpty(parsedResponse.nextPageToken));
        return builder.build();
      }
    };
  }

  private static class ListAppsResponse {
    @Key("apps")
    private List<AppResponse> apps;

    @Key("nextPageToken")
    private String nextPageToken;
  }

  /* createAndroidApp, createIosApp */

  @Override
  public AndroidApp createAndroidApp(String projectId, String packageName, String displayName)
      throws FirebaseProjectManagementException {
    return blockOnFutureAndHandleExceptions(
        createAndroidAppAsync(projectId, packageName, displayName),
        packageName,
        "Package name",
        "Unable to create Android App");
  }

  @Override
  public ApiFuture<AndroidApp> createAndroidAppAsync(
      String projectId, String packageName, String displayName) {
    checkArgument(!Strings.isNullOrEmpty(packageName), "package name must not be null or empty");
    return
        transform(
            transformAsync(
                createAndroidAppOp(projectId, packageName, displayName).callAsync(app),
                new WaitOperationFunction(projectId),
                scheduledExecutor),
            createAndroidAppFromAppIdFunction,
            scheduledExecutor);
  }

  @Override
  public IosApp createIosApp(String projectId, String bundleId, String displayName)
      throws FirebaseProjectManagementException {
    return blockOnFutureAndHandleExceptions(
        createIosAppAsync(projectId, bundleId, displayName),
        bundleId,
        "Bundle ID",
        "Unable to create iOS App");
  }

  @Override
  public ApiFuture<IosApp> createIosAppAsync(
      String projectId, String bundleId, String displayName) {
    checkArgument(!Strings.isNullOrEmpty(bundleId), "bundle ID must not be null or empty");
    return
        transform(
            transformAsync(
                createIosAppOp(projectId, bundleId, displayName).callAsync(app),
                new WaitOperationFunction(projectId),
                scheduledExecutor),
            createIosAppFromAppIdFunction,
            scheduledExecutor);
  }

  private CallableOperation<String, FirebaseProjectManagementException> createAndroidAppOp(
      String projectId, String namespace, String displayName) {
    return createAppOp(
        projectId, namespace, displayName, ANDROID_NAMESPACE_PROPERTY, ANDROID_APPS_RESOURCE_NAME);
  }

  private CallableOperation<String, FirebaseProjectManagementException> createIosAppOp(
      String projectId, String namespace, String displayName) {
    return createAppOp(
        projectId, namespace, displayName, IOS_NAMESPACE_PROPERTY, IOS_APPS_RESOURCE_NAME);
  }

  private CallableOperation<String, FirebaseProjectManagementException> createAppOp(
      final String projectId,
      final String namespace,
      final String displayName,
      final String platformNamespaceProperty,
      final String platformResourceName) {
    return new CallableOperation<String, FirebaseProjectManagementException>() {
      @Override
      protected String execute() throws FirebaseProjectManagementException {
        String url = String.format(
            "%s/v1beta1/projects/%s/%s",
            FIREBASE_PROJECT_MANAGEMENT_URL,
            projectId,
            platformResourceName);
        ImmutableMap.Builder<String, String> payloadBuilder =
            ImmutableMap.<String, String>builder().put(platformNamespaceProperty, namespace);
        if (!Strings.isNullOrEmpty(displayName)) {
          payloadBuilder.put("display_name", displayName);
        }
        OperationResponse operationResponseInstance = new OperationResponse();
        makePostRequest(
            url, payloadBuilder.build(), operationResponseInstance, projectId, "Project ID");
        if (Strings.isNullOrEmpty(operationResponseInstance.name)) {
          throw createFirebaseProjectManagementException(
              namespace,
              "Bundle ID",
              "Unable to create App: server returned null operation name.",
              /* cause= */ null);
        }
        return operationResponseInstance.name;
      }
    };
  }

  /**
   * An {@link ApiAsyncFunction} that transforms a Long Running Operation name to an {@link IosApp}
   * or an {@link AndroidApp} instance by repeatedly polling the server (with exponential backoff)
   * until the App is created successfully, or until the number of poll attempts exceeds the maximum
   * allowed.
   */
  private class WaitOperationFunction implements ApiAsyncFunction<String, String> {
    private final String projectId;

    private WaitOperationFunction(String projectId) {
      this.projectId = projectId;
    }

    /**
     * Returns an {@link ApiFuture} that will eventually contain the App ID of the new created App,
     * or an exception if an error occurred during polling.
     */
    @Override
    public ApiFuture<String> apply(String operationName) throws FirebaseProjectManagementException {
      SettableApiFuture<String> settableFuture = SettableApiFuture.<String>create();
      scheduledExecutor.schedule(
          new WaitOperationRunnable(
              /* numberOfPreviousPolls= */ 0,
              operationName,
              projectId,
              settableFuture),
          /* delay= */ 0L,
          TimeUnit.MILLISECONDS);
      return settableFuture;
    }
  }

  /**
   * A poller that repeatedly polls a Long Running Operation (with exponential backoff) until its
   * status is "done", or until the number of polling attempts exceeds the maximum allowed.
   */
  private class WaitOperationRunnable implements Runnable {
    private final int numberOfPreviousPolls;
    private final String operationName;
    private final String projectId;
    private final SettableApiFuture<String> settableFuture;

    private WaitOperationRunnable(
        int numberOfPreviousPolls,
        String operationName,
        String projectId,
        SettableApiFuture<String> settableFuture) {
      this.numberOfPreviousPolls = numberOfPreviousPolls;
      this.operationName = operationName;
      this.projectId = projectId;
      this.settableFuture = settableFuture;
    }

    @Override
    public void run() {
      String url = String.format("%s/v1/%s", FIREBASE_PROJECT_MANAGEMENT_URL, operationName);
      OperationResponse operationResponseInstance;
      operationResponseInstance = new OperationResponse();
      try {
        makeGetRequest(url, operationResponseInstance, projectId, "Project ID");
      } catch (FirebaseProjectManagementException e) {
        settableFuture.setException(e);
        return;
      }
      if (!operationResponseInstance.done) {
        if (numberOfPreviousPolls + 1 >= MAXIMUM_POLLING_ATTEMPTS) {
          settableFuture.setException(createFirebaseProjectManagementException(
              projectId,
              "Project ID",
              "Unable to create App: deadline exceeded.",
              /* cause= */ null));
        } else {
          long delay = (long) (
              POLL_BASE_WAIT_TIME_MILLIS
                  * Math.pow(POLL_EXPONENTIAL_BACKOFF_FACTOR, numberOfPreviousPolls));
          scheduledExecutor.schedule(
              new WaitOperationRunnable(
                  numberOfPreviousPolls + 1,
                  operationName,
                  projectId,
                  settableFuture),
              delay,
              TimeUnit.MILLISECONDS);
        }
        return;
      }
      // The Long Running Operation API guarantees that when done == true, exactly one of 'response'
      // or 'error' is set.
      if (operationResponseInstance.response == null
          || Strings.isNullOrEmpty(operationResponseInstance.response.appId)) {
        settableFuture.setException(createFirebaseProjectManagementException(
            projectId,
            "Project ID",
            "Unable to create App: internal server error.",
            /* cause= */ null));
      } else {
        settableFuture.set(operationResponseInstance.response.appId);
      }
    }
  }

  // This class is public due to the way parsing nested JSON objects work, and is needed by
  // create{Android|Ios}App and list{Android|Ios}Apps. In any case, the containing class,
  // FirebaseProjectManagementServiceImpl, is package-private.
  public static class AppResponse {
    @Key("name")
    protected String name;

    @Key("appId")
    protected String appId;

    @Key("displayName")
    protected String displayName;

    @Key("projectId")
    protected String projectId;
  }

  private static class AndroidAppResponse extends AppResponse {
    @Key("packageName")
    private String packageName;
  }

  private static class IosAppResponse extends AppResponse {
    @Key("bundleId")
    private String bundleId;
  }

  // This class is public due to the way parsing nested JSON objects work, and is needed by
  // createIosApp and createAndroidApp. In any case, the containing class,
  // FirebaseProjectManagementServiceImpl, is package-private.
  public static class StatusResponse {
    @Key("code")
    private int code;

    @Key("message")
    private String message;
  }

  private static class OperationResponse {
    @Key("name")
    private String name;

    @Key("metadata")
    private String metadata;

    @Key("done")
    private boolean done;

    @Key("error")
    private StatusResponse error;

    @Key("response")
    private AppResponse response;
  }

  /* setAndroidDisplayName, setIosDisplayName */

  @Override
  public void setAndroidDisplayName(String appId, String newDisplayName)
      throws FirebaseProjectManagementException {
    setAndroidDisplayNameOp(appId, newDisplayName).call();
  }

  @Override
  public ApiFuture<Void> setAndroidDisplayNameAsync(String appId, String newDisplayName) {
    return setAndroidDisplayNameOp(appId, newDisplayName).callAsync(app);
  }

  @Override
  public void setIosDisplayName(String appId, String newDisplayName)
      throws FirebaseProjectManagementException {
    setIosDisplayNameOp(appId, newDisplayName).call();
  }

  @Override
  public ApiFuture<Void> setIosDisplayNameAsync(String appId, String newDisplayName) {
    return setIosDisplayNameOp(appId, newDisplayName).callAsync(app);
  }

  private CallableOperation<Void, FirebaseProjectManagementException> setAndroidDisplayNameOp(
      String appId, String newDisplayName) {
    return setDisplayNameOp(appId, newDisplayName, ANDROID_APPS_RESOURCE_NAME);
  }

  private CallableOperation<Void, FirebaseProjectManagementException> setIosDisplayNameOp(
      String appId, String newDisplayName) {
    return setDisplayNameOp(appId, newDisplayName, IOS_APPS_RESOURCE_NAME);
  }

  private CallableOperation<Void, FirebaseProjectManagementException> setDisplayNameOp(
      final String appId, final String newDisplayName, final String platformResourceName) {
    checkArgument(
        !Strings.isNullOrEmpty(newDisplayName), "new Display Name must not be null or empty");
    return new CallableOperation<Void, FirebaseProjectManagementException>() {
      @Override
      protected Void execute() throws FirebaseProjectManagementException {
        String url = String.format(
            "%s/v1beta1/projects/-/%s/%s?update_mask=display_name",
            FIREBASE_PROJECT_MANAGEMENT_URL,
            platformResourceName,
            appId);
        ImmutableMap<String, String> payload =
            ImmutableMap.<String, String>builder().put("display_name", newDisplayName).build();
        EmptyResponse emptyResponseInstance = new EmptyResponse();
        makePatchRequest(url, payload, emptyResponseInstance, appId, "App ID");
        return null;
      }
    };
  }

  private static class EmptyResponse {}

  /* getAndroidConfig, getIosConfig */

  @Override
  public String getAndroidConfig(String appId) throws FirebaseProjectManagementException {
    return getAndroidConfigOp(appId).call();
  }

  @Override
  public ApiFuture<String> getAndroidConfigAsync(String appId) {
    return getAndroidConfigOp(appId).callAsync(app);
  }

  @Override
  public String getIosConfig(String appId) throws FirebaseProjectManagementException {
    return getIosConfigOp(appId).call();
  }

  @Override
  public ApiFuture<String> getIosConfigAsync(String appId) {
    return getIosConfigOp(appId).callAsync(app);
  }

  private CallableOperation<String, FirebaseProjectManagementException> getAndroidConfigOp(
      String appId) {
    return getConfigOp(appId, ANDROID_APPS_RESOURCE_NAME);
  }

  private CallableOperation<String, FirebaseProjectManagementException> getIosConfigOp(
      String appId) {
    return getConfigOp(appId, IOS_APPS_RESOURCE_NAME);
  }

  private CallableOperation<String, FirebaseProjectManagementException> getConfigOp(
      final String appId, final String platformResourceName) {
    return new CallableOperation<String, FirebaseProjectManagementException>() {
      @Override
      protected String execute() throws FirebaseProjectManagementException {
        String url = String.format(
            "%s/v1beta1/projects/-/%s/%s/config",
            FIREBASE_PROJECT_MANAGEMENT_URL,
            platformResourceName,
            appId);
        AppConfigResponse parsedResponse = new AppConfigResponse();
        makeGetRequest(url, parsedResponse, appId, "App ID");
        return new String(Base64.decodeBase64(parsedResponse.configFileContents), Charsets.UTF_8);
      }
    };
  }

  private static class AppConfigResponse {
    @Key("configFilename")
    String configFilename;

    @Key("configFileContents")
    String configFileContents;
  }

  /* getShaCertificates */

  @Override
  public List<ShaCertificate> getShaCertificates(String appId)
      throws FirebaseProjectManagementException {
    return getShaCertificatesOp(appId).call();
  }

  @Override
  public ApiFuture<List<ShaCertificate>> getShaCertificatesAsync(String appId) {
    return getShaCertificatesOp(appId).callAsync(app);
  }

  private CallableOperation<List<ShaCertificate>, FirebaseProjectManagementException>
      getShaCertificatesOp(final String appId) {
    return new CallableOperation<List<ShaCertificate>, FirebaseProjectManagementException>() {
      @Override
      protected List<ShaCertificate> execute() throws FirebaseProjectManagementException {
        String url = String.format(
            "%s/v1beta1/projects/-/androidApps/%s/sha", FIREBASE_PROJECT_MANAGEMENT_URL, appId);
        ListShaCertificateResponse parsedResponse = new ListShaCertificateResponse();
        makeGetRequest(url, parsedResponse, appId, "App ID");
        List<ShaCertificate> certificates = new ArrayList<>();
        if (parsedResponse.certificates == null) {
          return certificates;
        }
        for (ShaCertificateResponse certificate : parsedResponse.certificates) {
          certificates.add(
              ShaCertificate.create(certificate.name, certificate.shaHash, certificate.certType));
        }
        return certificates;
      }
    };
  }

  /* createShaCertificate */

  @Override
  public ShaCertificate createShaCertificate(String appId, String shaHash)
      throws FirebaseProjectManagementException {
    return createShaCertificateOp(appId, shaHash).call();
  }

  @Override
  public ApiFuture<ShaCertificate> createShaCertificateAsync(String appId, String shaHash) {
    return createShaCertificateOp(appId, shaHash).callAsync(app);
  }

  private CallableOperation<ShaCertificate, FirebaseProjectManagementException>
      createShaCertificateOp(final String appId, final String shaHash) {
    return new CallableOperation<ShaCertificate, FirebaseProjectManagementException>() {
      @Override
      protected ShaCertificate execute() throws FirebaseProjectManagementException {
        String url = String.format(
            "%s/v1beta1/projects/-/androidApps/%s/sha", FIREBASE_PROJECT_MANAGEMENT_URL, appId);
        ShaCertificateResponse parsedResponse = new ShaCertificateResponse();
        ImmutableMap<String, String> payload = ImmutableMap.<String, String>builder()
            .put("sha_hash", shaHash)
            .put("cert_type", ShaCertificate.getTypeFromHash(shaHash).name())
            .build();
        makePostRequest(url, payload, parsedResponse, appId, "App ID");
        return ShaCertificate.create(
            parsedResponse.name, parsedResponse.shaHash, parsedResponse.certType);
      }
    };
  }

  /* deleteShaCertificate */

  @Override
  public void deleteShaCertificate(String resourceName)
      throws FirebaseProjectManagementException {
    deleteShaCertificateOp(resourceName).call();
  }

  @Override
  public ApiFuture<Void> deleteShaCertificateAsync(String resourceName) {
    return deleteShaCertificateOp(resourceName).callAsync(app);
  }

  private CallableOperation<Void, FirebaseProjectManagementException> deleteShaCertificateOp(
      final String resourceName) {
    return new CallableOperation<Void, FirebaseProjectManagementException>() {
      @Override
      protected Void execute() throws FirebaseProjectManagementException {
        String url = String.format(
            "%s/v1beta1/%s", FIREBASE_PROJECT_MANAGEMENT_URL, resourceName);
        EmptyResponse parsedResponse = new EmptyResponse();
        makeDeleteRequest(url, parsedResponse, resourceName, "SHA name");
        return null;
      }
    };
  }

  private static class ListShaCertificateResponse {
    @Key("certificates")
    private List<ShaCertificateResponse> certificates;
  }

  // This class is public due to the way parsing nested JSON objects work, and is needed by
  // getShaCertificates. In any case, the containing class, FirebaseProjectManagementServiceImpl, is
  // package-private.
  public static class ShaCertificateResponse {
    @Key("name")
    private String name;

    @Key("shaHash")
    private String shaHash;

    @Key("certType")
    private String certType;
  }

  /* Helper methods. */

  private <T> void makeGetRequest(
      String url,
      T parsedResponseInstance,
      String requestIdentifier,
      String requestIdentifierDescription) throws FirebaseProjectManagementException {
    try {
      makeRequest(
          requestFactory.buildGetRequest(new GenericUrl(url)),
          parsedResponseInstance,
          requestIdentifier,
          requestIdentifierDescription);
    } catch (IOException e) {
      handleError(requestIdentifier, requestIdentifierDescription, e);
    }
  }

  private <T> void makePostRequest(
      String url,
      Object payload,
      T parsedResponseInstance,
      String requestIdentifier,
      String requestIdentifierDescription) throws FirebaseProjectManagementException {
    try {
      makeRequest(
          requestFactory.buildPostRequest(
              new GenericUrl(url), new JsonHttpContent(jsonFactory, payload)),
          parsedResponseInstance,
          requestIdentifier,
          requestIdentifierDescription);
    } catch (IOException e) {
      handleError(requestIdentifier, requestIdentifierDescription, e);
    }
  }

  private <T> void makePatchRequest(
      String url,
      Object payload,
      T parsedResponseInstance,
      String requestIdentifier,
      String requestIdentifierDescription) throws FirebaseProjectManagementException {
    try {
      HttpRequest baseRequest = requestFactory.buildPostRequest(
          new GenericUrl(url), new JsonHttpContent(jsonFactory, payload));
      baseRequest.getHeaders().set(PATCH_OVERRIDE_KEY, PATCH_OVERRIDE_VALUE);
      makeRequest(
          baseRequest, parsedResponseInstance, requestIdentifier, requestIdentifierDescription);
    } catch (IOException e) {
      handleError(requestIdentifier, requestIdentifierDescription, e);
    }
  }

  private <T> void makeDeleteRequest(
      String url,
      T parsedResponseInstance,
      String requestIdentifier,
      String requestIdentifierDescription) throws FirebaseProjectManagementException {
    try {
      makeRequest(
          requestFactory.buildDeleteRequest(new GenericUrl(url)),
          parsedResponseInstance,
          requestIdentifier,
          requestIdentifierDescription);
    } catch (IOException e) {
      handleError(requestIdentifier, requestIdentifierDescription, e);
    }
  }

  private <T> void makeRequest(
      HttpRequest baseRequest,
      T parsedResponseInstance,
      String requestIdentifier,
      String requestIdentifierDescription) throws FirebaseProjectManagementException {
    HttpResponse response = null;
    try {
      baseRequest.setParser(new JsonObjectParser(jsonFactory));
      baseRequest.setResponseInterceptor(interceptor);
      response = baseRequest.execute();
      jsonFactory.createJsonParser(response.getContent(), Charsets.UTF_8)
          .parseAndClose(parsedResponseInstance);
    } catch (Exception e) {
      handleError(requestIdentifier, requestIdentifierDescription, e);
    } finally {
      disconnectQuietly(response);
    }
  }

  private static void disconnectQuietly(HttpResponse response) {
    if (response != null) {
      try {
        response.disconnect();
      } catch (IOException ignored) {
        // Ignored.
      }
    }
  }

  private static void handleError(
      String requestIdentifier, String requestIdentifierDescription, Exception e)
          throws FirebaseProjectManagementException {
    String messageBody = "Error while invoking Firebase Project Management service.";
    if (e instanceof HttpResponseException) {
      int statusCode = ((HttpResponseException) e).getStatusCode();
      if (ERROR_CODES.containsKey(statusCode)) {
        messageBody = ERROR_CODES.get(statusCode);
      }
    }
    throw createFirebaseProjectManagementException(
        requestIdentifier, requestIdentifierDescription, messageBody, e);
  }

  private <T> T blockOnFutureAndHandleExceptions(
      ApiFuture<T> future,
      String requestIdentifier,
      String requestIdentifierDescription,
      String errorMessage) throws FirebaseProjectManagementException {
    try {
      return future.get();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof FirebaseProjectManagementException) {
        throw (FirebaseProjectManagementException) e.getCause();
      }
      throw createFirebaseProjectManagementException(
          requestIdentifier, requestIdentifierDescription, String.format("%s.", errorMessage), e);
    } catch (CancellationException e) {
      throw createFirebaseProjectManagementException(
          requestIdentifier,
          requestIdentifierDescription,
          String.format("%s; the operation was cancelled.", errorMessage),
          e);
    } catch (InterruptedException e) {
      throw createFirebaseProjectManagementException(
          requestIdentifier,
          requestIdentifierDescription,
          String.format("%s; the operation was interrupted.", errorMessage),
          e);
    }
  }

  private static FirebaseProjectManagementException createFirebaseProjectManagementException(
      String requestIdentifier,
      String requestIdentifierDescription,
      String messageBody,
      @Nullable Exception cause) {
    return new FirebaseProjectManagementException(
        String.format(
            "%s \"%s\": %s", requestIdentifierDescription, requestIdentifier, messageBody),
        cause);
  }

  /* Helper types. */

  private interface CreateAppFromAppIdFunction<T> extends ApiFunction<String, T> {}

  private class CreateAndroidAppFromAppIdFunction
      implements CreateAppFromAppIdFunction<AndroidApp> {
    @Override
    public AndroidApp apply(String appId) {
      return AndroidApp.create(appId, FirebaseProjectManagementServiceImpl.this);
    }
  }

  private class CreateIosAppFromAppIdFunction implements CreateAppFromAppIdFunction<IosApp> {
    @Override
    public IosApp apply(String appId) {
      return IosApp.create(appId, FirebaseProjectManagementServiceImpl.this);
    }
  }
}
