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

package com.google.firebase.projectmanagement;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.api.core.ApiFuture;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import java.util.List;

/**
 * This class is the entry point for all Firebase Project Management actions.
 *
 * <p>You can get an instance of FirebaseProjectManagement via {@link #getInstance(FirebaseApp)},
 * and then use it to modify or retrieve information about your Firebase project, as well as create,
 * modify, or retrieve information about the Android or iOS Apps in your Firebase project.
 */
public class FirebaseProjectManagement {
  private static final String SERVICE_ID = FirebaseProjectManagement.class.getName();

  private static final Object GET_INSTANCE_LOCK = new Object();

  private final String projectId;
  private AndroidAppService androidAppService;
  private IosAppService iosAppService;

  private FirebaseProjectManagement(String projectId) {
    checkArgument(!Strings.isNullOrEmpty(projectId),
        "Project ID is required to access the Firebase Project Management service. Use a service "
            + "account credential or set the project ID explicitly via FirebaseOptions. "
            + "Alternatively you can also set the project ID via the GOOGLE_CLOUD_PROJECT "
            + "environment variable.");

    this.projectId = projectId;
  }

  @VisibleForTesting
  void setAndroidAppService(AndroidAppService androidAppService) {
    this.androidAppService = androidAppService;
  }

  @VisibleForTesting
  void setIosAppService(IosAppService iosAppService) {
    this.iosAppService = iosAppService;
  }

  /**
   * Gets the {@link FirebaseProjectManagement} instance for the default {@link FirebaseApp}.
   *
   * @return the {@link FirebaseProjectManagement} instance for the default {@link FirebaseApp}
   */
  @NonNull
  public static FirebaseProjectManagement getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }

  /**
   * Gets the {@link FirebaseProjectManagement} instance for the specified {@link FirebaseApp}.
   *
   * @return the {@link FirebaseProjectManagement} instance for the specified {@link FirebaseApp}
   */
  @NonNull
  public static FirebaseProjectManagement getInstance(FirebaseApp app) {
    synchronized (GET_INSTANCE_LOCK) {
      FirebaseProjectManagementService service = ImplFirebaseTrampolines.getService(
          app, SERVICE_ID, FirebaseProjectManagementService.class);
      if (service == null) {
        service =
            ImplFirebaseTrampolines.addService(app, new FirebaseProjectManagementService(app));
      }
      return service.getInstance();
    }
  }

  /* Android App */

  /**
   * Obtains an {@link AndroidApp} reference to an Android App in the associated Firebase project.
   *
   * @param appId the App ID that identifies this Android App.
   * @see AndroidApp
   */
  @NonNull
  public AndroidApp getAndroidApp(@NonNull String appId) {
    return new AndroidApp(appId, androidAppService);
  }

  /**
   * Lists all Android Apps in the associated Firebase project, returning a list of {@link
   * AndroidApp} references to each. This returned list is read-only and cannot be modified.
   *
   * @throws FirebaseProjectManagementException if there was an error during the RPC
   * @see AndroidApp
   */
  @NonNull
  public List<AndroidApp> listAndroidApps() throws FirebaseProjectManagementException {
    return androidAppService.listAndroidApps(projectId);
  }

  /**
   * Asynchronously lists all Android Apps in the associated Firebase project, returning an {@code
   * ApiFuture} of a list of {@link AndroidApp} references to each. This returned list is read-only
   * and cannot be modified.
   *
   * @see AndroidApp
   */
  @NonNull
  public ApiFuture<List<AndroidApp>> listAndroidAppsAsync() {
    return androidAppService.listAndroidAppsAsync(projectId);
  }

  /**
   * Creates a new Android App in the associated Firebase project and returns an {@link AndroidApp}
   * reference to it.
   *
   * @param packageName the package name of the Android App to be created
   * @throws FirebaseProjectManagementException if there was an error during the RPC
   * @see AndroidApp
   */
  @NonNull
  public AndroidApp createAndroidApp(@NonNull String packageName)
      throws FirebaseProjectManagementException {
    return createAndroidApp(packageName, /* displayName= */ null);
  }

  /**
   * Creates a new Android App in the associated Firebase project and returns an {@link AndroidApp}
   * reference to it.
   *
   * @param packageName the package name of the Android App to be created
   * @param displayName a nickname for this Android App
   * @throws FirebaseProjectManagementException if there was an error during the RPC
   * @see AndroidApp
   */
  @NonNull
  public AndroidApp createAndroidApp(@NonNull String packageName, @Nullable String displayName)
      throws FirebaseProjectManagementException {
    return androidAppService.createAndroidApp(projectId, packageName, displayName);
  }

  /**
   * Asynchronously creates a new Android App in the associated Firebase project and returns an
   * {@code ApiFuture} that will eventually contain the {@link AndroidApp} reference to it.
   *
   * @param packageName the package name of the Android App to be created
   * @see AndroidApp
   */
  @NonNull
  public ApiFuture<AndroidApp> createAndroidAppAsync(@NonNull String packageName) {
    return createAndroidAppAsync(packageName, /* displayName= */ null);
  }

  /**
   * Asynchronously creates a new Android App in the associated Firebase project and returns an
   * {@code ApiFuture} that will eventually contain the {@link AndroidApp} reference to it.
   *
   * @param packageName the package name of the Android App to be created
   * @param displayName a nickname for this Android App
   * @see AndroidApp
   */
  @NonNull
  public ApiFuture<AndroidApp> createAndroidAppAsync(
      @NonNull String packageName, @Nullable String displayName) {
    return androidAppService.createAndroidAppAsync(projectId, packageName, displayName);
  }

  /* iOS App */

  /**
   * Obtains an {@link IosApp} reference to an iOS App in the associated Firebase project.
   *
   * @param appId the App ID that identifies this iOS App.
   * @see IosApp
   */
  @NonNull
  public IosApp getIosApp(@NonNull String appId) {
    return new IosApp(appId, iosAppService);
  }

  /**
   * Lists all iOS Apps in the associated Firebase project, returning a list of {@link IosApp}
   * references to each. This returned list is read-only and cannot be modified.
   *
   * @throws FirebaseProjectManagementException if there was an error during the RPC
   * @see IosApp
   */
  @NonNull
  public List<IosApp> listIosApps() throws FirebaseProjectManagementException {
    return iosAppService.listIosApps(projectId);
  }

  /**
   * Asynchronously lists all iOS Apps in the associated Firebase project, returning an {@code
   * ApiFuture} of a list of {@link IosApp} references to each. This returned list is read-only and
   * cannot be modified.
   *
   * @see IosApp
   */
  @NonNull
  public ApiFuture<List<IosApp>> listIosAppsAsync() {
    return iosAppService.listIosAppsAsync(projectId);
  }

  /**
   * Creates a new iOS App in the associated Firebase project and returns an {@link IosApp}
   * reference to it.
   *
   * @param bundleId the bundle ID of the iOS App to be created
   * @throws FirebaseProjectManagementException if there was an error during the RPC
   * @see IosApp
   */
  @NonNull
  public IosApp createIosApp(@NonNull String bundleId) throws FirebaseProjectManagementException {
    return createIosApp(bundleId, /* displayName= */ null);
  }

  /**
   * Creates a new iOS App in the associated Firebase project and returns an {@link IosApp}
   * reference to it.
   *
   * @param bundleId the bundle ID of the iOS App to be created
   * @param displayName a nickname for this iOS App
   * @throws FirebaseProjectManagementException if there was an error during the RPC
   * @see IosApp
   */
  @NonNull
  public IosApp createIosApp(@NonNull String bundleId, @Nullable String displayName)
      throws FirebaseProjectManagementException {
    return iosAppService.createIosApp(projectId, bundleId, displayName);
  }

  /**
   * Asynchronously creates a new iOS App in the associated Firebase project and returns an {@code
   * ApiFuture} that will eventually contain the {@link IosApp} reference to it.
   *
   * @param bundleId the bundle ID of the iOS App to be created
   * @see IosApp
   */
  @NonNull
  public ApiFuture<IosApp> createIosAppAsync(@NonNull String bundleId) {
    return createIosAppAsync(bundleId, /* displayName= */ null);
  }

  /**
   * Asynchronously creates a new iOS App in the associated Firebase project and returns an {@code
   * ApiFuture} that will eventually contain the {@link IosApp} reference to it.
   *
   * @param bundleId the bundle ID of the iOS App to be created
   * @param displayName a nickname for this iOS App
   * @see IosApp
   */
  @NonNull
  public ApiFuture<IosApp> createIosAppAsync(
      @NonNull String bundleId, @Nullable String displayName) {
    return iosAppService.createIosAppAsync(projectId, bundleId, displayName);
  }

  private static class FirebaseProjectManagementService
      extends FirebaseService<FirebaseProjectManagement> {
    private final FirebaseProjectManagementServiceImpl serviceImpl;

    private FirebaseProjectManagementService(FirebaseApp app) {
      super(SERVICE_ID, new FirebaseProjectManagement(ImplFirebaseTrampolines.getProjectId(app)));
      serviceImpl = new FirebaseProjectManagementServiceImpl(app);
      FirebaseProjectManagement serviceInstance = getInstance();
      serviceInstance.setAndroidAppService(serviceImpl);
      serviceInstance.setIosAppService(serviceImpl);
    }

    @Override
    public void destroy() {
      serviceImpl.destroy();
    }
  }
}
