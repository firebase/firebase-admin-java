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
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.List;

/**
 * An instance of this class is a reference to an Android App within a Firebase Project; it can be
 * used to query detailed information about the App, modify the display name of the App, or download
 * the configuration file for the App.
 *
 * <p>Note: the methods in this class make RPCs.
 */
public class AndroidApp {

  private final AndroidAppService androidAppService;
  private final String appId;

  AndroidApp(String appId, AndroidAppService androidAppService) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(appId), "app ID cannot be null or empty");
    this.appId = appId;
    this.androidAppService = androidAppService;
  }

  String getAppId() {
    return appId;
  }

  /**
   * Retrieves detailed information about this Android App.
   *
   * @return an {@link AndroidAppMetadata} instance describing this App
   * @throws FirebaseProjectManagementException if there was an error during the RPC
   */
  public AndroidAppMetadata getMetadata() throws Exception {
    return androidAppService.getAndroidApp(appId);
  }

  /**
   * Asynchronously retrieves information about this Android App.
   *
   * @return an {@code ApiFuture} containing an {@link AndroidAppMetadata} instance describing this
   *     App
   */
  public ApiFuture<AndroidAppMetadata> getMetadataAsync() {
    return androidAppService.getAndroidAppAsync(appId);
  }

  /**
   * Updates the Display Name attribute of this Android App to the one given.
   *
   * @throws FirebaseProjectManagementException if there was an error during the RPC
   */
  public void setDisplayName(String newDisplayName) throws FirebaseProjectManagementException {
    androidAppService.setAndroidDisplayName(appId, newDisplayName);
  }

  /**
   * Asynchronously updates the Display Name attribute of this Android App to the one given.
   */
  public ApiFuture<Void> setDisplayNameAsync(String newDisplayName) {
    return androidAppService.setAndroidDisplayNameAsync(appId, newDisplayName);
  }

  /**
   * Retrieves the configuration artifact associated with this Android App.
   *
   * @return a modified UTF-8 encoded {@code String} containing the contents of the artifact
   * @throws FirebaseProjectManagementException if there was an error during the RPC
   */
  public String getConfig() throws FirebaseProjectManagementException {
    return androidAppService.getAndroidConfig(appId);
  }

  /**
   * Asynchronously retrieves the configuration artifact associated with this Android App.
   *
   * @return an {@code ApiFuture} of a UTF-8 encoded {@code String} containing the contents of the
   *     artifact
   */
  public ApiFuture<String> getConfigAsync() {
    return androidAppService.getAndroidConfigAsync(appId);
  }

  /**
   * Retrieves the entire list of SHA certificates associated with this Android app.
   *
   * @return a list of {@link ShaCertificate} containing resource name, SHA hash and certificate
   *     type
   * @throws FirebaseProjectManagementException if there was an error during the RPC
   */
  public List<ShaCertificate> getShaCertificates() throws FirebaseProjectManagementException {
    return androidAppService.getShaCertificates(appId);
  }

  /**
   * Asynchronously retrieves the entire list of SHA certificates associated with this Android app.
   *
   * @return an {@code ApiFuture} of a list of {@link ShaCertificate} containing resource name,
   *     SHA hash and certificate type
   */
  public ApiFuture<List<ShaCertificate>> getShaCertificatesAsync() {
    return androidAppService.getShaCertificatesAsync(appId);
  }

  /**
   * Adds a SHA certificate to this Android app.
   *
   * @param certificateToAdd the SHA certificate to be added to this Android app
   * @return a {@link ShaCertificate} that was created for this Android app, containing resource
   *     name, SHA hash, and certificate type
   * @throws FirebaseProjectManagementException if there was an error during the RPC
   */
  public ShaCertificate createShaCertificate(ShaCertificate certificateToAdd)
      throws FirebaseProjectManagementException {
    return androidAppService.createShaCertificate(appId, certificateToAdd);
  }

  /**
   * Asynchronously adds a SHA certificate to this Android app.
   *
   * @param certificateToAdd the SHA certificate to be added to this Android app
   * @return a {@code ApiFuture} of a {@link ShaCertificate} that was created for this Android app,
   *     containing resource name, SHA hash, and certificate type
   */
  public ApiFuture<ShaCertificate> createShaCertificateAsync(ShaCertificate certificateToAdd) {
    return androidAppService.createShaCertificateAsync(appId, certificateToAdd);
  }

  /**
   * Removes a SHA certificate from this Android app.
   *
   * @param certificateToRemove the SHA certificate to be removed from this Android app
   * @throws FirebaseProjectManagementException if there was an error during the RPC
   */
  public void deleteShaCertificate(ShaCertificate certificateToRemove)
      throws FirebaseProjectManagementException {
    androidAppService.deleteShaCertificate(certificateToRemove.getName());
  }

  /**
   * Asynchronously removes a SHA certificate from this Android app.
   *
   * @param certificateToRemove the SHA certificate to be removed from this Android app
   */
  public ApiFuture<Void> deleteShaCertificateAsync(ShaCertificate certificateToRemove) {
    return androidAppService.deleteShaCertificateAsync(certificateToRemove.getName());
  }

}
