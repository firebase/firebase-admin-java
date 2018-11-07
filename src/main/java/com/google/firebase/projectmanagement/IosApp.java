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

import com.google.api.core.ApiFuture;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * An instance of this class is a reference to an iOS App within a Firebase Project; it can be used
 * to query detailed information about the App, modify the display name of the App, or download the
 * configuration file for the App.
 *
 * <p>Note: the methods in this class make RPCs.
 */
public class IosApp {
  private final String appId;
  private final IosAppService iosAppService;

  IosApp(String appId, IosAppService iosAppService) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(appId), "app ID cannot be null or empty");
    this.appId = appId;
    this.iosAppService = iosAppService;
  }

  String getAppId() {
    return appId;
  }

  /**
   * Retrieves detailed information about this iOS App.
   *
   * @return an {@link IosAppMetadata} instance describing this App
   * @throws FirebaseProjectManagementException if there was an error during the RPC
   */
  public IosAppMetadata getMetadata() throws FirebaseProjectManagementException {
    return iosAppService.getIosApp(appId);
  }

  /**
   * Asynchronously retrieves information about this iOS App.
   *
   * @return an {@code ApiFuture} containing an {@link IosAppMetadata} instance describing this App
   */
  public ApiFuture<IosAppMetadata> getMetadataAsync() {
    return iosAppService.getIosAppAsync(appId);
  }

  /**
   * Updates the Display Name attribute of this iOS App to the one given.
   *
   * @throws FirebaseProjectManagementException if there was an error during the RPC
   */
  public void setDisplayName(String newDisplayName) throws FirebaseProjectManagementException {
    iosAppService.setIosDisplayName(appId, newDisplayName);
  }

  /**
   * Asynchronously updates the Display Name attribute of this iOS App to the one given.
   */
  public ApiFuture<Void> setDisplayNameAsync(String newDisplayName) {
    return iosAppService.setIosDisplayNameAsync(appId, newDisplayName);
  }

  /**
   * Retrieves the configuration artifact associated with this iOS App.
   *
   * @return a modified UTF-8 encoded {@code String} containing the contents of the artifact
   * @throws FirebaseProjectManagementException if there was an error during the RPC
   */
  public String getConfig() throws FirebaseProjectManagementException {
    return iosAppService.getIosConfig(appId);
  }

  /**
   * Asynchronously retrieves the configuration artifact associated with this iOS App.
   *
   * @return an {@code ApiFuture} of a UTF-8 encoded {@code String} containing the contents of the
   *     artifact
   */
  public ApiFuture<String> getConfigAsync() {
    return iosAppService.getIosConfigAsync(appId);
  }

  @Override
  public String toString() {
    return String.format("iOS App %s", getAppId());
  }
}
