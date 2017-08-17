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
import com.google.auth.oauth2.OAuth2Credentials.CredentialsChangedListener;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.NonNull;

/**
 * Provides trampolines into package-private APIs used by components of Firebase. Intentionally
 * scarily-named to dissuade people from actually trying to use the class and to make it less likely
 * to appear in code completion.
 *
 * @hide
 */
public final class ImplFirebaseTrampolines {

  private ImplFirebaseTrampolines() {}

  public static GoogleCredentials getCredentials(@NonNull FirebaseApp app) {
    return app.getOptions().getCredentials();
  }

  public static String getProjectId(@NonNull FirebaseApp app) {
    return getProjectId(app.getOptions());
  }

  public static String getProjectId(@NonNull FirebaseOptions options) {
    String projectId = options.getProjectId();
    if (projectId == null) {
      GoogleCredentials credentials = options.getCredentials();
      if (credentials instanceof ServiceAccountCredentials) {
        // TODO: Get project ID from credential when
        // TODO: https://github.com/google/google-auth-library-java/pull/118 is resolved.
      }

      if (projectId == null) {
        projectId = System.getenv("GCLOUD_PROJECT");
      }
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

  public static void addCredentialsChangedListener(
      @NonNull FirebaseApp app, @NonNull CredentialsChangedListener listener) {
    app.addCredentialsChangedListener(listener);
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
