package com.google.firebase;

import com.google.firebase.auth.FirebaseCredential;
import com.google.firebase.internal.AuthStateListener;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.GetTokenResult;
import com.google.firebase.internal.NonNull;
import com.google.firebase.tasks.Task;

/**
 * Provides trampolines into package-private APIs used by components of Firebase. Intentionally
 * scarily-named to dissuade people from actually trying to use the class and to make it less likely
 * to appear in code completion.
 */
public final class ImplFirebaseTrampolines {

  private ImplFirebaseTrampolines() {}

  public static FirebaseCredential getCredential(@NonNull FirebaseApp app) {
    return app.getOptions().getCredential();
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
