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
 * An interface to interact with the Android-specific functionalities in the Firebase Project
 * Management Service.
 *
 * <p>Note: Implementations of methods in this service may make RPCs.
 */
interface AndroidAppService {
  /**
   * Creates a new Android App in the given project.
   *
   * @param projectId the Project ID of the project in which to create the App
   * @param packageName the package name of the Android App to be created
   * @param displayName the user-defined display name for the Android App to be created
   * @return an {@link AndroidApp} reference
   */
  AndroidApp createAndroidApp(String projectId, String packageName, String displayName)
      throws FirebaseProjectManagementException;

  /**
   * Creates a new Android App in the given project.
   *
   * @param projectId the Project ID of the project in which to create the App
   * @param packageName the package name of the Android App to be created
   * @param displayName the user-defined display name for the Android App to be created
   * @return an {@link AndroidApp} reference
   */
  ApiFuture<AndroidApp> createAndroidAppAsync(
      String projectId, String packageName, String displayName);

  /**
   * Retrieve information about an existing Android App, identified by its App ID.
   *
   * @param appId the App ID of the Android App
   * @return an {@link AndroidAppMetadata} instance describing the Android App
   */
  AndroidAppMetadata getAndroidApp(String appId) throws FirebaseProjectManagementException;

  /**
   * Asynchronously retrieves information about an existing Android App, identified by its App ID.
   *
   * @param appId the App ID of the iOS App
   * @return an {@link AndroidAppMetadata} instance describing the Android App
   */
  ApiFuture<AndroidAppMetadata> getAndroidAppAsync(String appId);

  /**
   * Lists all the Android Apps belonging to the given project. The returned list cannot be
   * modified.
   *
   * @param projectId the Project ID of the project
   * @return a read-only list of {@link AndroidApp} references
   */
  List<AndroidApp> listAndroidApps(String projectId) throws FirebaseProjectManagementException;

  /**
   * Asynchronously lists all the Android Apps belonging to the given project. The returned list
   * cannot be modified.
   *
   * @param projectId the project ID of the project
   * @return an {@link ApiFuture} of a read-only list of {@link AndroidApp} references
   */
  ApiFuture<List<AndroidApp>> listAndroidAppsAsync(String projectId);

  /**
   * Updates the Display Name of the given Android App.
   *
   * @param appId the App ID of the Android App
   * @param newDisplayName the new Display Name
   */
  void setAndroidDisplayName(String appId, String newDisplayName)
      throws FirebaseProjectManagementException;

  /**
   * Asynchronously updates the Display Name of the given Android App.
   *
   * @param appId the App ID of the iOS App
   * @param newDisplayName the new Display Name
   */
  ApiFuture<Void> setAndroidDisplayNameAsync(String appId, String newDisplayName);

  /**
   * Retrieves the configuration artifact associated with the specified Android App.
   *
   * @param appId the App ID of the Android App
   * @return a modified UTF-8 encoded {@code String} containing the contents of the artifact
   */
  String getAndroidConfig(String appId) throws FirebaseProjectManagementException;

  /**
   * Asynchronously retrieves the configuration artifact associated with the specified Android App.
   *
   * @param appId the App ID of the Android App
   * @return an {@link ApiFuture} of a modified UTF-8 encoded {@code String} containing the contents
   *     of the artifact
   */
  ApiFuture<String> getAndroidConfigAsync(String appId);

  /**
   * Retrieves the entire list of SHA certificates associated with this Android App.
   *
   * @param appId the App ID of the Android App
   * @return a list of {@link ShaCertificate} containing resource name, SHA hash and certificate
   *     type
   * @throws FirebaseProjectManagementException if there was an error during the RPC
   */
  List<ShaCertificate> getShaCertificates(String appId) throws FirebaseProjectManagementException;

  /**
   * Asynchronously retrieves the entire list of SHA certificates associated with this Android App.
   *
   * @param appId the App ID of the Android App
   * @return an {@link ApiFuture} of a list of {@link ShaCertificate} containing resource name,
   *     SHA hash and certificate type
   */
  ApiFuture<List<ShaCertificate>> getShaCertificatesAsync(String appId);


  /**
   * Adds a SHA certificate to this Android App.
   *
   * @param appId the App ID of the Android App
   * @param certificateToAdd the SHA certificate to be added to this Android App
   * @return a {@link ShaCertificate} that was created for this Android App, containing resource
   *     name, SHA hash, and certificate type
   * @throws FirebaseProjectManagementException if there was an error during the RPC
   */
  ShaCertificate createShaCertificate(String appId, ShaCertificate certificateToAdd)
      throws FirebaseProjectManagementException;

  /**
   * Asynchronously adds a SHA certificate to this Android App.
   *
   * @param appId the App ID of the Android App
   * @param certificateToAdd the SHA certificate to be added to this Android App
   * @return a {@link ApiFuture} of a {@link ShaCertificate} that was created for this Android App,
   *     containing resource name, SHA hash, and certificate type
   */
  ApiFuture<ShaCertificate> createShaCertificateAsync(
      String appId, ShaCertificate certificateToAdd);

  /**
   * Removes a SHA certificate from this Android App.
   *
   * @param resourceName the fully qualified resource name of the SHA certificate
   * @throws FirebaseProjectManagementException if there was an error during the RPC
   */
  void deleteShaCertificate(String resourceName) throws FirebaseProjectManagementException;

  /**
   * Asynchronously removes a SHA certificate from this Android App.
   *
   * @param resourceName the fully qualified resource name of the SHA certificate
   */
  ApiFuture<Void> deleteShaCertificateAsync(String resourceName);
}
