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

package com.google.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.base.Strings;
import com.google.firebase.auth.FirebaseCredential;
import com.google.firebase.auth.ImplFirebaseAuthTrampolines;
import com.google.firebase.internal.AuthStateListener;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.GetTokenResult;
import com.google.firebase.internal.NonNull;
import com.google.firebase.tasks.Task;

/**
 * Provides trampolines into package-private APIs used by components of Firebase. Intentionally
 * scarily-named to dissuade people from actually trying to use the class and to make it less likely
 * to appear in code completion.
 *
 * @hide
 */
public final class ImplFirebaseTrampolines {

  private ImplFirebaseTrampolines() {}

  public static FirebaseCredential getCredential(@NonNull FirebaseApp app) {
    return app.getOptions().getCredential();
  }

  public static String getProjectId(@NonNull FirebaseApp app) {
    return getProjectId(app.getOptions());
  }

  public static String getProjectId(@NonNull FirebaseOptions options) {
    // Try to get project ID from user-specified options.
    String projectId = options.getProjectId();

    // Try to get project ID from the credentials.
    if (Strings.isNullOrEmpty(projectId)) {
      FirebaseCredential credential = options.getCredential();
      projectId = ImplFirebaseAuthTrampolines.getProjectId(credential);
    }

    // Try to get project ID from the environment.
    if (Strings.isNullOrEmpty(projectId)) {
      projectId = System.getenv("GCLOUD_PROJECT");
    }
    return projectId;
  }

  public static boolean isDefaultApp(@NonNull FirebaseApp app) {
    return app.isDefaultApp();
  }

  public static String getPersistenceKey(@NonNull FirebaseApp app) {
    return app.getPersistenceKey();
  }
  
  public static String getPersistenceKey(String name, FirebaseOptions options) {
    return FirebaseApp.getPersistenceKey(name, options);
  }

  public static void addAuthStateChangeListener(
      @NonNull FirebaseApp app, @NonNull AuthStateListener listener) {
    app.addAuthStateListener(listener);
  }

  public static void removeAuthStateChangeListener(
      @NonNull FirebaseApp app, @NonNull AuthStateListener listener) {
    app.removeAuthStateListener(listener);
  }

  public static Task<GetTokenResult> getToken(@NonNull FirebaseApp app, boolean forceRefresh) {
    return app.getToken(forceRefresh);
  }

  public static <T extends FirebaseService> T getService(
      @NonNull FirebaseApp app, @NonNull String id, @NonNull Class<T> type) {
    return type.cast(app.getService(id));
  }

  public static <T extends FirebaseService> T addService(
      @NonNull FirebaseApp app, @NonNull T service) {
    app.addService(service);
    return service;
  }
}
