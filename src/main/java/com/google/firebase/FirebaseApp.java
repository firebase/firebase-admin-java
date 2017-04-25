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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.firebase.auth.GoogleOAuthAccessToken;
import com.google.firebase.internal.AuthStateListener;
import com.google.firebase.internal.FirebaseAppStore;
import com.google.firebase.internal.FirebaseExecutors;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.GetTokenResult;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import com.google.firebase.tasks.Continuation;
import com.google.firebase.tasks.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The entry point of Firebase SDKs. It holds common configuration and state for Firebase APIs. Most
 * applications don't need to directly interact with FirebaseApp.
 *
 * <p>Firebase APIs use the default FirebaseApp by default, unless a different one is explicitly
 * passed to the API via FirebaseFoo.getInstance(firebaseApp).
 *
 * <p>{@link FirebaseApp#initializeApp(FirebaseOptions)} initializes the default app instance. This
 * method should be invoked at startup.
 */
public class FirebaseApp {

  /** A map of (name, FirebaseApp) instances. */
  private static final Map<String, FirebaseApp> instances = new HashMap<>();

  public static final String DEFAULT_APP_NAME = "[DEFAULT]";
  private static final long TOKEN_REFRESH_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(55);

  static final TokenRefresher.Factory DEFAULT_TOKEN_REFRESHER_FACTORY =
      new TokenRefresher.Factory();
  static final Clock DEFAULT_CLOCK = new Clock();

  /**
   * Global lock for synchronizing all SDK-wide application state changes. Specifically, any
   * accesses to instances map should be protected by this lock.
   */
  private static final Object appsLock = new Object();

  private final String name;
  private final FirebaseOptions options;
  private final TokenRefresher tokenRefresher;
  private final Clock clock;

  private final AtomicBoolean deleted = new AtomicBoolean();
  private final List<AuthStateListener> authStateListeners = new ArrayList<>();
  private final AtomicReference<GetTokenResult> currentToken = new AtomicReference<>();
  private final Map<String, FirebaseService> services = new HashMap<>();

  private Task<GoogleOAuthAccessToken> previousTokenTask;

  /**
   * Per application lock for synchronizing all internal FirebaseApp state changes.
   */
  private final Object lock = new Object();

  /** Default constructor. */
  private FirebaseApp(String name, FirebaseOptions options,
      TokenRefresher.Factory factory, Clock clock) {
    checkArgument(!Strings.isNullOrEmpty(name));
    this.name = name;
    this.options = checkNotNull(options);
    this.tokenRefresher = checkNotNull(factory).create(this);
    this.clock = checkNotNull(clock);
  }

  /** Returns a list of all FirebaseApps. */
  public static List<FirebaseApp> getApps() {
    // TODO: reenable persistence. See b/28158809.
    synchronized (appsLock) {
      return ImmutableList.copyOf(instances.values());
    }
  }

  /**
   * Returns the default (first initialized) instance of the {@link FirebaseApp}.
   *
   * @throws IllegalStateException if the default app was not initialized.
   */
  @Nullable
  public static FirebaseApp getInstance() {
    return getInstance(DEFAULT_APP_NAME);
  }

  /**
   * Returns the instance identified by the unique name, or throws if it does not exist.
   *
   * @param name represents the name of the {@link FirebaseApp} instance.
   * @return the {@link FirebaseApp} corresponding to the name.
   * @throws IllegalStateException if the {@link FirebaseApp} was not initialized, either via {@link
   *     #initializeApp(FirebaseOptions, String)} or {@link #getApps()}.
   */
  public static FirebaseApp getInstance(@NonNull String name) {
    synchronized (appsLock) {
      FirebaseApp firebaseApp = instances.get(normalize(name));
      if (firebaseApp != null) {
        return firebaseApp;
      }

      List<String> availableAppNames = getAllAppNames();
      String availableAppNamesMessage;
      if (availableAppNames.isEmpty()) {
        availableAppNamesMessage = "";
      } else {
        availableAppNamesMessage =
            "Available app names: " + Joiner.on(", ").join(availableAppNames);
      }
      String errorMessage =
          String.format(
              "FirebaseApp with name %s doesn't exist. %s", name, availableAppNamesMessage);
      throw new IllegalStateException(errorMessage);
    }
  }

  /**
   * Initializes the default {@link FirebaseApp} instance. Same as {@link
   * #initializeApp(FirebaseOptions, String)}, but it uses {@link #DEFAULT_APP_NAME} as name. *
   *
   * <p>The creation of the default instance is automatically triggered at app startup time, if
   * Firebase configuration values are available from resources - populated from
   * google-services.json.
   */
  public static FirebaseApp initializeApp(FirebaseOptions options) {
    return initializeApp(options, DEFAULT_APP_NAME);
  }

  /**
   * A factory method to intialize a {@link FirebaseApp}.
   *
   * @param options represents the global {@link FirebaseOptions}
   * @param name unique name for the app. It is an error to initialize an app with an already
   *     existing name. Starting and ending whitespace characters in the name are ignored (trimmed).
   * @return an instance of {@link FirebaseApp}
   * @throws IllegalStateException if an app with the same name has already been initialized.
   */
  public static FirebaseApp initializeApp(FirebaseOptions options, String name) {
    return initializeApp(options, name, DEFAULT_TOKEN_REFRESHER_FACTORY, DEFAULT_CLOCK);
  }

  static FirebaseApp initializeApp(FirebaseOptions options, String name,
      TokenRefresher.Factory tokenRefresherFactory, Clock clock) {
    FirebaseAppStore appStore = FirebaseAppStore.initialize();
    String normalizedName = normalize(name);
    final FirebaseApp firebaseApp;
    synchronized (appsLock) {
      checkState(
          !instances.containsKey(normalizedName),
          "FirebaseApp name " + normalizedName + " already exists!");

      firebaseApp = new FirebaseApp(normalizedName, options, tokenRefresherFactory, clock);
      instances.put(normalizedName, firebaseApp);
    }

    appStore.persistApp(firebaseApp);

    return firebaseApp;
  }

  @VisibleForTesting
  static void clearInstancesForTest() {
    synchronized (appsLock) {
      // Copy the instances list before iterating, as delete() would attempt to remove from the
      // original list.
      for (FirebaseApp app : ImmutableList.copyOf(instances.values())) {
        app.delete();
      }
      instances.clear();
    }
  }

  /**
   * Returns persistence key. Exists to support getting {@link FirebaseApp} persistence key after
   * the app has been deleted.
   */
  static String getPersistenceKey(String name, FirebaseOptions options) {
    return BaseEncoding.base64Url().omitPadding().encode(name.getBytes(UTF_8));
  }

  /** Use this key to store data per FirebaseApp. */
  String getPersistenceKey() {
    return FirebaseApp.getPersistenceKey(getName(), getOptions());
  }

  private static List<String> getAllAppNames() {
    Set<String> allAppNames = new HashSet<>();
    synchronized (appsLock) {
      for (FirebaseApp app : instances.values()) {
        allAppNames.add(app.getName());
      }
      FirebaseAppStore appStore = FirebaseAppStore.getInstance();
      if (appStore != null) {
        allAppNames.addAll(appStore.getAllPersistedAppNames());
      }
    }
    List<String> sortedNameList = new ArrayList<>(allAppNames);
    Collections.sort(sortedNameList);
    return sortedNameList;
  }

  /** Normalizes the app name. */
  private static String normalize(@NonNull String name) {
    return checkNotNull(name).trim();
  }

  /** Returns the unique name of this app. */
  @NonNull
  public String getName() {
    checkNotDeleted();
    return name;
  }

  /** 
   * Returns the specified {@link FirebaseOptions}. 
   */
  @NonNull
  public FirebaseOptions getOptions() {
    checkNotDeleted();
    return options;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof FirebaseApp)) {
      return false;
    }
    return name.equals(((FirebaseApp) o).getName());
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("name", name).add("options", options).toString();
  }

  /**
   * Deletes the {@link FirebaseApp} and all its data. All calls to this {@link FirebaseApp}
   * instance will throw once it has been called.
   *
   * <p>A no-op if delete was called before.
   */
  public void delete() {
    synchronized (lock) {
      boolean valueChanged = deleted.compareAndSet(false /* expected */, true);
      if (!valueChanged) {
        return;
      }

      for (FirebaseService service : services.values()) {
        service.destroy();
      }
      services.clear();
      authStateListeners.clear();
      tokenRefresher.cleanup();
    }

    synchronized (appsLock) {
      instances.remove(name);
    }

    FirebaseAppStore appStore = FirebaseAppStore.getInstance();
    if (appStore != null) {
      appStore.removeApp(name);
    }
  }

  private void checkNotDeleted() {
    checkState(!deleted.get(), "FirebaseApp was deleted %s", this);
  }

  private boolean refreshRequired(
      @NonNull Task<GoogleOAuthAccessToken> previousTask, boolean forceRefresh) {
    return (previousTask.isComplete()
        && (forceRefresh || !previousTask.isSuccessful()
        || previousTask.getResult().getExpiryTime() <= clock.now()));
  }

  /**
   * Internal-only method to fetch a valid Service Account OAuth2 Token.
   *
   * @param forceRefresh force refreshes the token. Should only be set to <code>true</code> if the
   *     token is invalidated out of band.
   * @return a {@link Task}
   */
  Task<GetTokenResult> getToken(boolean forceRefresh) {
    synchronized (lock) {
      checkNotDeleted();
      if (previousTokenTask == null || refreshRequired(previousTokenTask, forceRefresh)) {
        previousTokenTask = options.getCredential().getAccessToken();
      }

      return previousTokenTask.continueWith(
          new Continuation<GoogleOAuthAccessToken, GetTokenResult>() {
            @Override
            public GetTokenResult then(@NonNull Task<GoogleOAuthAccessToken> task)
                throws Exception {
              GetTokenResult newToken = new GetTokenResult(task.getResult().getAccessToken());
              GetTokenResult oldToken = currentToken.get();
              List<AuthStateListener> listenersCopy = null;
              if (!newToken.equals(oldToken)) {
                synchronized (lock) {
                  if (deleted.get()) {
                    return newToken;
                  }

                  // Grab the lock before compareAndSet to avoid a potential race
                  // condition with addAuthStateListener. The same lock also ensures serial
                  // access to the token refresher.
                  if (currentToken.compareAndSet(oldToken, newToken)) {
                    listenersCopy = ImmutableList.copyOf(authStateListeners);
                    tokenRefresher.scheduleRefresh(TOKEN_REFRESH_INTERVAL_MILLIS);
                  }
                }
              }

              if (listenersCopy != null) {
                for (AuthStateListener listener : listenersCopy) {
                  listener.onAuthStateChanged(newToken);
                }
              }
              return newToken;
            }
          });
    }
  }

  boolean isDefaultApp() {
    return DEFAULT_APP_NAME.equals(getName());
  }

  void addAuthStateListener(@NonNull final AuthStateListener listener) {
    GetTokenResult currentToken;
    synchronized (lock) {
      checkNotDeleted();
      authStateListeners.add(checkNotNull(listener));
      currentToken = this.currentToken.get();
    }

    if (currentToken != null) {
      // Task has copied the authStateListeners before the listener was added.
      // Notify this listener explicitly.
      listener.onAuthStateChanged(currentToken);
    }
  }

  void removeAuthStateListener(@NonNull AuthStateListener listener) {
    synchronized (lock) {
      checkNotDeleted();
      authStateListeners.remove(checkNotNull(listener));
    }
  }

  void addService(FirebaseService service) {
    synchronized (lock) {
      checkNotDeleted();
      checkArgument(!services.containsKey(checkNotNull(service).getId()));
      services.put(service.getId(), service);
    }
  }

  FirebaseService getService(String id) {
    synchronized (lock) {
      checkArgument(!Strings.isNullOrEmpty(id));
      return services.get(id);
    }
  }

  /**
   * Utility class for scheduling proactive token refresh events.  Each FirebaseApp should have
   * its own instance of this class. This class is not thread safe. The caller (FirebaseApp) must
   * ensure that methods are called serially.
   */
  static class TokenRefresher {

    private final FirebaseApp firebaseApp;
    private ScheduledFuture<Task<GetTokenResult>> future;

    TokenRefresher(FirebaseApp app) {
      this.firebaseApp = checkNotNull(app);
    }

    /**
     * Schedule a forced token refresh to be executed after a specified duration.
     *
     * @param delayMillis Duration in milliseconds, after which the token should be forcibly
     *     refreshed.
     */
    final void scheduleRefresh(long delayMillis) {
      cancelPrevious();
      scheduleNext(
          new Callable<Task<GetTokenResult>>() {
            @Override
            public Task<GetTokenResult> call() throws Exception {
              return firebaseApp.getToken(true);
            }
          },
          delayMillis);
    }

    protected void cancelPrevious() {
      if (future != null) {
        future.cancel(true);
      }
    }

    protected void scheduleNext(Callable<Task<GetTokenResult>> task, long delayMillis) {
      try {
        future =
            FirebaseExecutors.DEFAULT_SCHEDULED_EXECUTOR.schedule(
                task, delayMillis, TimeUnit.MILLISECONDS);
      } catch (UnsupportedOperationException ignored) {
        // Cannot support task scheduling in the current runtime.
      }
    }

    protected void cleanup() {
      if (future != null) {
        future.cancel(true);
      }
    }

    static class Factory {
      TokenRefresher create(FirebaseApp app) {
        return new TokenRefresher(app);
      }
    }
  }

  static class Clock {
    long now() {
      return System.currentTimeMillis();
    }
  }
}
