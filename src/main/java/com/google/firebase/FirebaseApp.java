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

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonParser;
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
import com.google.firebase.internal.FirebaseAppStore;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.GaeThreadFactory;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;

import com.google.firebase.internal.RevivingScheduledExecutor;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.Tasks;

import java.io.FileNotFoundException;
import java.io.FileReader;
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

import java.util.concurrent.atomic.AtomicReference;
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
  static final String FIREBASE_CONFIG_ENV_VAR = "FIREBASE_CONFIG";

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
  private final ThreadManager.FirebaseExecutors executors;

  private final AtomicBoolean deleted = new AtomicBoolean();
  private final Map<String, FirebaseService> services = new HashMap<>();

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
    this.executors = this.threadManager.getFirebaseExecutors(this);
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
   * Initializes the default {@link FirebaseApp} instance using Google Application Default
   * Credentials. Also attempts to load additional {@link FirebaseOptions} from the environment
   * by looking up the {@code FIREBASE_CONFIG} environment variable. If the value of
   * the variable starts with <code>'{'</code>, it is parsed as a JSON object. Otherwise it is
   * treated as a file name and the JSON content is read from the corresponding file.
   */
  public static FirebaseApp initializeApp() {
    return initializeApp(DEFAULT_APP_NAME);
  }

  /**
   * Initializes a named {@link FirebaseApp} instance using Google Application Default Credentials.
   * Loads additional {@link FirebaseOptions} from the environment in the same way as the
   * {@link #initializeApp()} method.
   */
  public static FirebaseApp initializeApp(String name) {
    return initializeApp(getOptionsFromEnvironment(), name);
  }

  /**
   * Initializes the default {@link FirebaseApp} instance using the given options.
   */
  public static FirebaseApp initializeApp(FirebaseOptions options) {
    return initializeApp(options, DEFAULT_APP_NAME);
  }

  /**
   * Initializes a named {@link FirebaseApp} instance using the given options.
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
      tokenRefresher.stop();

      // Clean up and terminate the thread pools
      threadManager.releaseFirebaseExecutors(this, executors);
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
    // Wrap the name argument in an array to ensure the invocation gets bound to the commonly
    // available checkState(boolean, String, Object...) overload. Otherwise the invocation may
    // get bound to the checkState(boolean, String, Object) overload, which is not present in older
    // guava versions.
    checkState(!deleted.get(), "FirebaseApp '%s' was deleted", new Object[]{name});
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
    return Tasks.call(executors.getListeningExecutor(), command);
  }

  <T> ScheduledFuture<T> schedule(Callable<T> command, long delayMillis) {
    checkNotNull(command);
    try {
      return ensureScheduledExecutorService().schedule(command, delayMillis, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      // This may fail if the underlying ThreadFactory does not support long-lived threads.
      throw new UnsupportedOperationException("Scheduled tasks not supported", e);
    }
  }

  void startTokenRefresher() {
    synchronized (lock) {
      checkNotDeleted();
      // TODO: Provide an option to disable this altogether.
      tokenRefresher.start();
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
    private final AtomicReference<State> state;

    private Future<Void> future;

    TokenRefresher(FirebaseApp firebaseApp) {
      this.firebaseApp = checkNotNull(firebaseApp);
      this.credentials = firebaseApp.getOptions().getCredentials();
      this.state = new AtomicReference<>(State.READY);
    }

    @Override
    public final synchronized void onChanged(OAuth2Credentials credentials) throws IOException {
      if (state.get() != State.STARTED) {
        return;
      }

      AccessToken accessToken = credentials.getAccessToken();
      long refreshDelay = getRefreshDelay(accessToken);
      if (refreshDelay > 0) {
        scheduleRefresh(refreshDelay);
      } else {
        logger.warn("Token expiry ({}) is less than 5 minutes in the future. Not "
            + "scheduling a proactive refresh.", accessToken.getExpirationTime());
      }
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
      } catch (UnsupportedOperationException e) {
        // Cannot support task scheduling in the current runtime.
        logger.debug("Failed to schedule token refresh event", e);
      }
    }

    /**
     * Starts the TokenRefresher if not already started. Starts listening to credentials changed
     * events, and schedules refresh events every time the OAuth2 token changes. If no active
     * token is present, or if the available token is set to expire soon, this will also schedule
     * a refresh event to be executed immediately.
     *
     * <p>This operation is idempotent. Calling it multiple times, or calling it after the
     * refresher has been stopped has no effect.
     */
    final synchronized void start() {
      // Allow starting only from the ready state.
      if (!state.compareAndSet(State.READY, State.STARTED)) {
        return;
      }

      logger.debug("Starting the proactive token refresher");
      credentials.addChangeListener(this);
      AccessToken accessToken = credentials.getAccessToken();
      long refreshDelay;
      if (accessToken != null) {
        // If the token is about to expire (i.e. expires in less than 5 minutes), schedule a
        // refresh event with 0 delay. Otherwise schedule a refresh event at the regular token
        // expiry time, minus 5 minutes.
        refreshDelay = Math.max(getRefreshDelay(accessToken), 0L);
      } else {
        // If there is no token fetched so far, fetch one immediately.
        refreshDelay = 0L;
      }
      scheduleRefresh(refreshDelay);
    }

    final synchronized void stop() {
      // Allow stopping from any state.
      State previous = state.getAndSet(State.STOPPED);
      if (previous == State.STARTED) {
        cancelPrevious();
        logger.debug("Stopped the proactive token refresher");
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
      scheduleNext(new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          logger.debug("Refreshing OAuth2 credential");
          credentials.refresh();
          return null;
        }
      }, delayMillis);
    }

    private long getRefreshDelay(AccessToken accessToken) {
      return accessToken.getExpirationTime().getTime() - System.currentTimeMillis()
          - TimeUnit.MINUTES.toMillis(5);
    }

    static class Factory {
      TokenRefresher create(FirebaseApp app) {
        return new TokenRefresher(app);
      }
    }

    enum State {
      READY,
      STARTED,
      STOPPED
    }
  }

  private static FirebaseOptions getOptionsFromEnvironment() {
    String defaultConfig = System.getenv(FIREBASE_CONFIG_ENV_VAR);
    try { 
      if (Strings.isNullOrEmpty(defaultConfig)) {
        return new FirebaseOptions.Builder()
          .setCredentials(GoogleCredentials.getApplicationDefault())
          .build();
      }
      JsonFactory jsonFactory = Utils.getDefaultJsonFactory();
      FirebaseOptions.Builder builder = new FirebaseOptions.Builder();
      JsonParser parser;
      if (defaultConfig.startsWith("{")) {
        parser = jsonFactory.createJsonParser(defaultConfig);
      } else {
        FileReader reader;
        reader = new FileReader(defaultConfig);
        parser = jsonFactory.createJsonParser(reader);    
      }
      parser.parseAndClose(builder);
      builder.setCredentials(GoogleCredentials.getApplicationDefault());
      
      return builder.build();

    } catch (FileNotFoundException e) {
      throw new IllegalStateException(e);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
