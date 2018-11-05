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

import com.google.api.core.ApiFuture;
import java.util.List;

/**
 * An interface to interact with the iOS-specific functionalities in the Firebase Project Management
 * Service.
 *
 * <p>Note: Implementations of methods in this service may make RPCs.
 */
interface IosAppService {
  /**
   * Creates a new iOS App in the given project with the given display name.
   *
   * @param projectId the Project ID of the project in which to create the App
   * @param bundleId the bundle ID of the iOS App to create
   * @param displayName a nickname for this iOS App
   * @return an {@link IosApp} reference
   */
  IosApp createIosApp(String projectId, String bundleId, String displayName)
      throws FirebaseProjectManagementException;

  /**
   * Asynchronously creates a new iOS App in the given project with the given display name.
   *
   * @param projectId the Project ID of the project in which to create the App
   * @param bundleId the bundle ID of the iOS App to create
   * @param displayName a nickname for this iOS App
   * @return an {@link ApiFuture} of an {@link IosApp} reference
   */
  ApiFuture<IosApp> createIosAppAsync(String projectId, String bundleId, String displayName);

  /**
   * Retrieve information about an existing iOS App, identified by its App ID.
   *
   * @param appId the App ID of the iOS App
   * @return an {@link IosAppMetadata} instance describing the iOS App
   */
  IosAppMetadata getIosApp(String appId) throws FirebaseProjectManagementException;

  /**
   * Asynchronously retrieves information about an existing iOS App, identified by its App ID.
   *
   * @param appId the App ID of the iOS App
   * @return an {@link IosAppMetadata} instance describing the iOS App
   */
  ApiFuture<IosAppMetadata> getIosAppAsync(String appId);

  /**
   * Lists all the iOS Apps belonging to the given project. The returned list cannot be modified.
   *
   * @param projectId the Project ID of the project
   * @return a read-only list of {@link IosApp} references
   */
  List<IosApp> listIosApps(String projectId) throws FirebaseProjectManagementException;

  /**
   * Asynchronously lists all the iOS Apps belonging to the given project. The returned list cannot
   * be modified.
   *
   * @param projectId the project ID of the project
   * @return an {@link ApiFuture} of a read-only list of {@link IosApp} references
   */
  ApiFuture<List<IosApp>> listIosAppsAsync(String projectId);

  /**
   * Updates the Display Name of the given iOS App.
   *
   * @param appId the App ID of the iOS App
   * @param newDisplayName the new Display Name
   */
  void setIosDisplayName(String appId, String newDisplayName)
      throws FirebaseProjectManagementException;

  /**
   * Asynchronously updates the Display Name of the given iOS App.
   *
   * @param appId the App ID of the iOS App
   * @param newDisplayName the new Display Name
   */
  ApiFuture<Void> setIosDisplayNameAsync(String appId, String newDisplayName);

  /**
   * Retrieves the configuration artifact associated with the specified iOS App.
   *
   * @param appId the App ID of the iOS App
   * @return a modified UTF-8 encoded {@code String} containing the contents of the artifact
   */
  String getIosConfig(String appId) throws FirebaseProjectManagementException;

  /**
   * Asynchronously retrieves the configuration artifact associated with the specified iOS App.
   *
   * @param appId the App ID of the iOS App
   * @return an {@link ApiFuture} of a modified UTF-8 encoded {@code String} containing the contents
   *     of the artifact
   */
  ApiFuture<String> getIosConfigAsync(String appId);
}
