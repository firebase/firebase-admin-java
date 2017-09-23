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

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.auth.oauth2.OAuth2Credentials.CredentialsChangedListener;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.firebase.internal.FirebaseAppStore;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.GaeThreadFactory;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;

import com.google.firebase.internal.RevivingScheduledExecutor;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.Tasks;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  private static final Logger logger = LoggerFactory.getLogger(FirebaseApp.class);

  /** A map of (name, FirebaseApp) instances. */
  private static final Map<String, FirebaseApp> instances = new HashMap<>();

  public static final String DEFAULT_APP_NAME = "[DEFAULT]";

  private static final TokenRefresher.Factory DEFAULT_TOKEN_REFRESHER_FACTORY =
      new TokenRefresher.Factory();

  /**
   * Global lock for synchronizing all SDK-wide application state changes. Specifically, any
   * accesses to instances map should be protected by this lock.
   */
  private static final Object appsLock = new Object();

  private final String name;
  private final FirebaseOptions options;
  private final TokenRefresher tokenRefresher;
  private final ThreadManager threadManager;

  private final AtomicBoolean deleted = new AtomicBoolean();
  private final Map<String, FirebaseService> services = new HashMap<>();

  private volatile ThreadManager.FirebaseExecutor executor;
  private volatile ScheduledExecutorService scheduledExecutor;

  /**
   * Per application lock for synchronizing all internal FirebaseApp state changes.
   */
  private final Object lock = new Object();

  /** Default constructor. */
  private FirebaseApp(String name, FirebaseOptions options, TokenRefresher.Factory factory) {
    checkArgument(!Strings.isNullOrEmpty(name));
    this.name = name;
    this.options = checkNotNull(options);
    this.tokenRefresher = checkNotNull(factory).create(this);
    this.threadManager = options.getThreadManager();
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
   * A factory method to initialize a {@link FirebaseApp}.
   *
   * @param options represents the global {@link FirebaseOptions}
   * @param name unique name for the app. It is an error to initialize an app with an already
   *     existing name. Starting and ending whitespace characters in the name are ignored (trimmed).
   * @return an instance of {@link FirebaseApp}
   * @throws IllegalStateException if an app with the same name has already been initialized.
   */
  public static FirebaseApp initializeApp(FirebaseOptions options, String name) {
    return initializeApp(options, name, DEFAULT_TOKEN_REFRESHER_FACTORY);
  }

  static FirebaseApp initializeApp(FirebaseOptions options, String name,
      TokenRefresher.Factory tokenRefresherFactory) {
    FirebaseAppStore appStore = FirebaseAppStore.initialize();
    String normalizedName = normalize(name);
    final FirebaseApp firebaseApp;
    synchronized (appsLock) {
      checkState(
          !instances.containsKey(normalizedName),
          "FirebaseApp name " + normalizedName + " already exists!");

      firebaseApp = new FirebaseApp(normalizedName, options, tokenRefresherFactory);
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

  /**
   * Returns the Google Cloud project ID associated with this app.
   *
   * @return A string project ID or null.
   */
  @Nullable
  String getProjectId() {
    // Try to get project ID from user-specified options.
    String projectId = options.getProjectId();

    // Try to get project ID from the credentials.
    if (Strings.isNullOrEmpty(projectId)) {
      GoogleCredentials credentials = options.getCredentials();
      if (credentials instanceof ServiceAccountCredentials) {
        projectId = ((ServiceAccountCredentials) credentials).getProjectId();
      }
    }

    // Try to get project ID from the environment.
    if (Strings.isNullOrEmpty(projectId)) {
      projectId = System.getenv("GCLOUD_PROJECT");
    }
    return projectId;
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
    return MoreObjects.toStringHelper(this).add("name", name).toString();
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
      tokenRefresher.cleanup();

      // Clean up and terminate the thread pool
      if (executor != null) {
        threadManager.releaseFirebaseExecutor(this, executor);
        executor = null;
      }
      if (scheduledExecutor != null) {
        scheduledExecutor.shutdownNow();
        scheduledExecutor = null;
      }
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

  private ListeningExecutorService ensureExecutorService() {
    if (executor == null) {
      synchronized (lock) {
        checkNotDeleted();
        if (executor == null) {
          executor = threadManager.getFirebaseExecutor(this);
        }
      }
    }
    return executor.getListeningExecutor();
  }

  private ScheduledExecutorService ensureScheduledExecutorService() {
    if (scheduledExecutor == null) {
      synchronized (lock) {
        checkNotDeleted();
        if (scheduledExecutor == null) {
          scheduledExecutor = new RevivingScheduledExecutor(threadManager.getThreadFactory(),
              "firebase-scheduled-worker", GaeThreadFactory.isAvailable());
        }
      }
    }
    return scheduledExecutor;
  }

  ThreadFactory getThreadFactory() {
    return threadManager.getThreadFactory();
  }

  // TODO: Return an ApiFuture once Task API is fully removed.
  <T> Task<T> submit(Callable<T> command) {
    checkNotNull(command);
    return Tasks.call(ensureExecutorService(), command);
  }

  <T> ScheduledFuture<T> schedule(Callable<T> command, long delayMillis) {
    checkNotNull(command);
    try {
      return ensureScheduledExecutorService().schedule(command, delayMillis, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      throw new UnsupportedOperationException("Scheduled tasks not supported", e);
    }
  }

  boolean isDefaultApp() {
    return DEFAULT_APP_NAME.equals(getName());
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
   * Utility class for scheduling proactive token refresh events. Each FirebaseApp should have
   * its own instance of this class. This class gets directly notified by GoogleCredentials
   * whenever the underlying OAuth2 token changes. TokenRefresher schedules subsequent token
   * refresh events when this happens.
   *
   * <p>This class is thread safe. It will handle only one token change event at a time. It also
   * cancels any pending token refresh events, before scheduling a new one.
   */
  static class TokenRefresher implements CredentialsChangedListener {

    private final FirebaseApp firebaseApp;
    private final GoogleCredentials credentials;
    private Future<Void> future;
    private boolean closed;

    TokenRefresher(FirebaseApp firebaseApp) {
      this.firebaseApp = checkNotNull(firebaseApp);
      this.credentials = firebaseApp.getOptions().getCredentials();
      this.credentials.addChangeListener(this);
    }

    @Override
    public final synchronized void onChanged(OAuth2Credentials credentials) throws IOException {
      if (closed) {
        return;
      }

      AccessToken accessToken = credentials.getAccessToken();
      long refreshDelay = accessToken.getExpirationTime().getTime()
          - System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5);
      if (refreshDelay > 0) {
        scheduleRefresh(refreshDelay);
      } else {
        logger.warn("Token expiry ({}) is less than 5 minutes in the future. Not "
            + "scheduling a proactive refresh.", accessToken.getExpirationTime());
      }
    }

    /**
     * Schedule a forced token refresh to be executed after a specified duration.
     *
     * @param delayMillis Duration in milliseconds, after which the token should be forcibly
     *     refreshed.
     */
    private void scheduleRefresh(final long delayMillis) {
      cancelPrevious();
      scheduleNext(
          new Callable<Void>() {
            @Override
            public Void call() throws Exception {
              logger.debug("Refreshing OAuth2 credential");
              credentials.refresh();
              return null;
            }
          },
          delayMillis);
    }

    protected void cancelPrevious() {
      if (future != null) {
        future.cancel(true);
      }
    }

    protected void scheduleNext(Callable<Void> task, long delayMillis) {
      logger.debug("Scheduling next token refresh in {} milliseconds", delayMillis);
      try {
        future = firebaseApp.schedule(task, delayMillis);
      } catch (UnsupportedOperationException ignored) {
        // Cannot support task scheduling in the current runtime.
      }
    }

    protected synchronized void cleanup() {
      cancelPrevious();
      closed = true;
    }

    static class Factory {
      TokenRefresher create(FirebaseApp app) {
        return new TokenRefresher(app);
      }
    }
  }
}
