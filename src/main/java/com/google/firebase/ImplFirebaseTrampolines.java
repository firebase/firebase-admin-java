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
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.NonNull;

import com.google.firebase.tasks.Task;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadFactory;

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
    return app.getProjectId();
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

  public static <T extends FirebaseService> T getService(
      @NonNull FirebaseApp app, @NonNull String id, @NonNull Class<T> type) {
    return type.cast(app.getService(id));
  }

  public static <T extends FirebaseService> T addService(
      @NonNull FirebaseApp app, @NonNull T service) {
    app.addService(service);
    return service;
  }

  public static ThreadFactory getThreadFactory(@NonNull FirebaseApp app) {
    return app.getThreadFactory();
  }

  public static <T> Task<T> submitCallable(@NonNull FirebaseApp app, @NonNull Callable<T> command) {
    return app.submit(command);
  }

  public static void startTokenRefresher(@NonNull FirebaseApp app) {
    app.startTokenRefresher();
  }
}
