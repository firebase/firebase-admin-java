package com.google.firebase;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.firebase.internal.AuthStateListener;
import com.google.firebase.internal.FirebaseAppStore;
import com.google.firebase.internal.FirebaseExecutors;
import com.google.firebase.internal.GetTokenResult;
import com.google.firebase.internal.GuardedBy;
import com.google.firebase.internal.Joiner;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import com.google.firebase.internal.Objects;
import com.google.firebase.internal.Preconditions;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.firebase.internal.Base64Utils.encodeUrlSafeNoPadding;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The entry point of Firebase SDKs. It holds common configuration and state for Firebase APIs. Most
 * applications don't need to directly interact with FirebaseApp. * <p>Firebase APIs use the default
 * FirebaseApp by default, unless a different one is explicitly passed to the API via
 * FirebaseFoo.getInstance(firebaseApp). * <p>{@link FirebaseApp#initializeApp(FirebaseOptions)}
 * initializes the default app instance. This method should be invoked at startup.
 */
public class FirebaseApp {

  /**
   * A map of (name, FirebaseApp) instances.
   */
  @GuardedBy("sLock")
  private static final Map<String, FirebaseApp> instances = new HashMap<>();
  private static final String DEFAULT_APP_NAME = "[DEFAULT]";
  private static final long TOKEN_REFRESH_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(55);
  private static final TokenRefresher.Factory DEFAULT_TOKEN_REFRESHER_FACTORY =
      new TokenRefresher.Factory();
  private static final Object sLock = new Object();

  private final String name;
  private final FirebaseOptions options;
  private final TokenRefresher tokenRefresher;

  private final AtomicBoolean deleted = new AtomicBoolean();

  private final List<FirebaseAppLifecycleListener> lifecycleListeners =
      new CopyOnWriteArrayList<>();

  private final List<AuthStateListener> authStateListeners = new ArrayList<>();

  private final AtomicReference<GetTokenResult> currentToken = new AtomicReference<>();

  /**
   * Default constructor.
   */
  private FirebaseApp(String name, FirebaseOptions options, TokenRefresher.Factory factory) {
    this.name = Preconditions.checkNotEmpty(name);
    this.options = Preconditions.checkNotNull(options);
    tokenRefresher = Preconditions.checkNotNull(factory).create(this);
  }

  /**
   * Returns a mutable list of all FirebaseApps.
   */
  public static List<FirebaseApp> getApps() {
    // TODO(arondeak): reenable persistence. See b/28158809.
    return new ArrayList<>(instances.values());
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
   * #initializeApp(FirebaseOptions, String)} or {@link #getApps()}.
   */
  public static FirebaseApp getInstance(@NonNull String name) {
    synchronized (sLock) {
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
   * #initializeApp(FirebaseOptions, String)}, but it uses {@link #DEFAULT_APP_NAME} as name.
   * * <p>The creation of the default instance is automatically triggered at app startup time, if
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
   * existing name. Starting and ending whitespace characters in the name are ignored (trimmed).
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
    synchronized (sLock) {
      Preconditions.checkState(
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
    // TODO(arondeak): also delete, once functionality is implemented.
    synchronized (sLock) {
      instances.clear();
    }
  }

  /**
   * Returns persistence key. Exists to support getting {@link FirebaseApp} persistence key after
   * the app has been deleted.
   */
  static String getPersistenceKey(String name, FirebaseOptions options) {
    return encodeUrlSafeNoPadding(name.getBytes(UTF_8));
  }

  private static List<String> getAllAppNames() {
    Set<String> allAppNames = new HashSet<>();
    synchronized (sLock) {
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

  /**
   * Normalizes the app name.
   */
  private static String normalize(@NonNull String name) {
    return name.trim();
  }

  /**
   * Returns the unique name of this app.
   */
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
    return Objects.toStringHelper(this).add("name", name)
        .add("options", options).toString();
  }

  /**
   * Deletes the {@link FirebaseApp} and all its data. All calls to this {@link FirebaseApp}
   * instance will throw once it has been called.
   *
   * <p>A no-op if delete was called before.
   */
  void delete() {
    boolean valueChanged = deleted.compareAndSet(false /* expected */, true);
    if (!valueChanged) {
      return;
    }
    tokenRefresher.cleanup();

    synchronized (sLock) {
      instances.remove(this.name);
    }

    FirebaseAppStore appStore = FirebaseAppStore.getInstance();
    if (appStore != null) {
      appStore.removeApp(name);
    }

    notifyOnAppDeleted();
  }

  private void checkNotDeleted() {
    Preconditions.checkState(!deleted.get(), "FirebaseApp was deleted");
  }

  /**
   * Internal-only method to fetch a valid Service Account OAuth2 Token.
   *
   * @param forceRefresh force refreshes the token. Should only be set to <code>true</code> if the
   * token is invalidated out of band.
   * @return a {@link Task}
   */
  Task<GetTokenResult> getToken(boolean forceRefresh) {
    checkNotDeleted();
    return options.getCredential().getAccessToken(forceRefresh).continueWith(
        new Continuation<String, GetTokenResult>() {
          @Override
          public GetTokenResult then(@NonNull Task<String> task) throws Exception {
            GetTokenResult newToken = new GetTokenResult(task.getResult());
            GetTokenResult oldToken = currentToken.get();
            List<AuthStateListener> listenersCopy = null;
            if (!newToken.equals(oldToken)) {
              synchronized (authStateListeners) {
                // Grab the lock before compareAndSet to avoid a potential race
                // condition
                // with addAuthStateListener
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

  boolean isDefaultApp() {
    return DEFAULT_APP_NAME.equals(getName());
  }

  /**
   * Use this key to store data per FirebaseApp.
   */
  String getPersistenceKey() {
    return FirebaseApp.getPersistenceKey(getName(), getOptions());
  }

  /**
   * If an API has locally stored data it must register lifecycle listeners at initialization
   * time.
   */
  // TODO(arondeak): make sure that all APIs that are interested in these events are
  // initialized using reflection when an app is deleted (for v5).
  void addLifecycleEventListener(@NonNull FirebaseAppLifecycleListener listener) {
    checkNotDeleted();
    Preconditions.checkNotNull(listener);
    lifecycleListeners.add(listener);
  }

  void removeLifecycleEventListener(@NonNull FirebaseAppLifecycleListener listener) {
    checkNotDeleted();
    Preconditions.checkNotNull(listener);
    lifecycleListeners.remove(listener);
  }

  void addAuthStateListener(@NonNull final AuthStateListener listener) {
    checkNotDeleted();
    Preconditions.checkNotNull(listener);

    GetTokenResult currentToken;
    synchronized (authStateListeners) {
      authStateListeners.add(listener);
      currentToken = this.currentToken.get();
    }

    if (currentToken != null) {
      // Task has copied the mAuthStateListeners before the listener was added.
      // Notify this listener explicitly.
      listener.onAuthStateChanged(currentToken);
    }
  }

  void removeAuthStateListener(@NonNull AuthStateListener listener) {
    checkNotDeleted();
    Preconditions.checkNotNull(listener);
    synchronized (authStateListeners) {
      authStateListeners.remove(listener);
    }
  }

  /**
   * Notifies all listeners with the name and options of the deleted {@link FirebaseApp} instance.
   */
  private void notifyOnAppDeleted() {
    for (FirebaseAppLifecycleListener listener : lifecycleListeners) {
      listener.onDeleted(name, options);
    }
  }

  static class TokenRefresher {

    private final FirebaseApp firebaseApp;
    private ScheduledFuture<Task<GetTokenResult>> future;

    TokenRefresher(FirebaseApp app) {
      this.firebaseApp = Preconditions.checkNotNull(app);
    }

    /**
     * Schedule a forced token refresh to be executed after a specified duration.
     *
     * @param delayMillis Duration in milliseconds, after which the token should be forcibly
     * refreshed.
     */
    final synchronized void scheduleRefresh(long delayMillis) {
      cancelPrevious();
      scheduleNext(new Callable<Task<GetTokenResult>>() {
        @Override
        public Task<GetTokenResult> call() throws Exception {
          return firebaseApp.getToken(true);
        }
      }, delayMillis);
    }

    protected void cancelPrevious() {
      if (future != null) {
        future.cancel(true);
      }
    }

    protected void scheduleNext(Callable<Task<GetTokenResult>> task, long delayMillis) {
      try {
        future = FirebaseExecutors.DEFAULT_SCHEDULED_EXECUTOR.schedule(
            task, delayMillis, TimeUnit.MILLISECONDS);
      } catch (UnsupportedOperationException ignored) {
        // Cannot support task scheduling in the current runtime.
      }
    }

    protected synchronized void cleanup() {
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
}
