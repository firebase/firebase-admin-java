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

package com.google.firebase.database;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.Repo;
import com.google.firebase.database.core.RepoInfo;
import com.google.firebase.database.core.RepoManager;
import com.google.firebase.database.utilities.ParsedUrl;
import com.google.firebase.database.utilities.Utilities;
import com.google.firebase.database.utilities.Validation;
import com.google.firebase.internal.FirebaseService;

import com.google.firebase.internal.SdkUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The entry point for accessing a Firebase Database. You can get an instance by calling {@link
 * FirebaseDatabase#getInstance()}. To access a location in the database and read or write data, use
 * {@link FirebaseDatabase#getReference()}.
 */
public class FirebaseDatabase {

  private final FirebaseApp app;
  private final RepoInfo repoInfo;
  private final DatabaseConfig config;
  private Repo repo; // Usage must be guarded by a call to ensureRepo().

  private final AtomicBoolean destroyed = new AtomicBoolean(false);

  // Lock for synchronizing internal state changes. Protects accesses to repo and destroyed
  // members.
  private final Object lock = new Object();

  private FirebaseDatabase(FirebaseApp app, RepoInfo repoInfo, DatabaseConfig config) {
    this.app = app;
    this.repoInfo = repoInfo;
    this.config = config;
  }

  /**
   * Gets the default FirebaseDatabase instance.
   *
   * @return A FirebaseDatabase instance.
   */
  public static FirebaseDatabase getInstance() {
    FirebaseApp instance = FirebaseApp.getInstance();
    if (instance == null) {
      throw new DatabaseException("You must call FirebaseApp.initialize() first.");
    }
    return getInstance(instance, instance.getOptions().getDatabaseUrl());
  }

  /**
   * Gets a FirebaseDatabase instance for the specified URL.
   *
   * @param url The URL to the Firebase Database instance you want to access.
   * @return A FirebaseDatabase instance.
   */
  public static FirebaseDatabase getInstance(String url) {
    FirebaseApp instance = FirebaseApp.getInstance();
    if (instance == null) {
      throw new DatabaseException("You must call FirebaseApp.initialize() first.");
    }
    return getInstance(instance, url);
  }

  /**
   * Gets an instance of FirebaseDatabase for a specific FirebaseApp.
   *
   * @param app The FirebaseApp to get a FirebaseDatabase for.
   * @return A FirebaseDatabase instance.
   */
  public static FirebaseDatabase getInstance(FirebaseApp app) {
    return getInstance(app, app.getOptions().getDatabaseUrl());
  }

  /**
   * Gets a FirebaseDatabase instance for the specified URL, using the specified FirebaseApp.
   *
   * @param app The FirebaseApp to get a FirebaseDatabase for.
   * @param url The URL to the Firebase Database instance you want to access.
   * @return A FirebaseDatabase instance.
   */
  public static synchronized FirebaseDatabase getInstance(FirebaseApp app, String url) {
    FirebaseDatabaseService service = ImplFirebaseTrampolines.getService(app, SERVICE_ID,
        FirebaseDatabaseService.class);
    if (service == null) {
      service = ImplFirebaseTrampolines.addService(app, new FirebaseDatabaseService());
    }

    DatabaseInstances dbInstances = service.getInstance();
    if (url == null || url.isEmpty()) {
      throw new DatabaseException(
          "Failed to get FirebaseDatabase instance: Specify DatabaseURL within "
              + "FirebaseApp or from your getInstance() call.");
    }

    ParsedUrl parsedUrl = Utilities.parseUrl(url);
    if (!parsedUrl.path.isEmpty()) {
      throw new DatabaseException(
          "Specified Database URL '"
              + url
              + "' is invalid. It should point to the root of a "
              + "Firebase Database but it includes a path: "
              + parsedUrl.path.toString());
    }

    FirebaseDatabase database = dbInstances.get(parsedUrl.repoInfo);
    if (database == null) {
      DatabaseConfig config = new DatabaseConfig();
      // If this is the default app, don't set the session persistence key so that we use our
      // default ("default") instead of the FirebaseApp default ("[DEFAULT]") so that we
      // preserve the default location used by the legacy Firebase SDK.
      if (!ImplFirebaseTrampolines.isDefaultApp(app)) {
        config.setSessionPersistenceKey(app.getName());
      }
      config.setFirebaseApp(app);

      database = new FirebaseDatabase(app, parsedUrl.repoInfo, config);
      dbInstances.put(parsedUrl.repoInfo, database);
    }

    return database;
  }

  /** This exists so Repo can create FirebaseDatabase objects to keep legacy tests working. */
  static FirebaseDatabase createForTests(
      FirebaseApp app, RepoInfo repoInfo, DatabaseConfig config) {
    FirebaseDatabase db = new FirebaseDatabase(app, repoInfo, config);
    db.ensureRepo();
    return db;
  }

  /** 
   * @return The version for this build of the Firebase Database client
   */
  public static String getSdkVersion() {
    return SdkUtils.getVersion();
  }

  /**
   * Returns the FirebaseApp instance to which this FirebaseDatabase belongs.
   *
   * @return The FirebaseApp instance to which this FirebaseDatabase belongs.
   */
  public FirebaseApp getApp() {
    return this.app;
  }

  /**
   * Gets a DatabaseReference for the database root node.
   *
   * @return A DatabaseReference pointing to the root node.
   */
  public DatabaseReference getReference() {
    return new DatabaseReference(ensureRepo(), Path.getEmptyPath());
  }

  /**
   * Gets a DatabaseReference for the provided path.
   *
   * @param path Path to a location in your FirebaseDatabase.
   * @return A DatabaseReference pointing to the specified path.
   */
  public DatabaseReference getReference(String path) {
    checkNotNull(path,
        "Can't pass null for argument 'pathString' in FirebaseDatabase.getReference()");
    Validation.validateRootPathString(path);
    Path childPath = new Path(path);
    return new DatabaseReference(ensureRepo(), childPath);
  }

  /**
   * Gets a DatabaseReference for the provided URL. The URL must be a URL to a path within this
   * FirebaseDatabase. To create a DatabaseReference to a different database, create a {@link
   * FirebaseApp} with a {@link FirebaseOptions} object configured with the appropriate database
   * URL.
   *
   * @param url A URL to a path within your database.
   * @return A DatabaseReference for the provided URL.
   */
  public DatabaseReference getReferenceFromUrl(String url) {
    checkNotNull(url,
        "Can't pass null for argument 'url' in FirebaseDatabase.getReferenceFromUrl()");
    ParsedUrl parsedUrl = Utilities.parseUrl(url);
    Repo repo = ensureRepo();
    if (!parsedUrl.repoInfo.host.equals(repo.getRepoInfo().host)) {
      throw new DatabaseException(
          "Invalid URL ("
              + url
              + ") passed to getReference().  "
              + "URL was expected to match configured Database URL: "
              + getReference().toString());
    }
    return new DatabaseReference(repo, parsedUrl.path);
  }

  /**
   * The Firebase Database client automatically queues writes and sends them to the server at the
   * earliest opportunity, depending on network connectivity. In some cases (e.g. offline usage)
   * there may be a large number of writes waiting to be sent. Calling this method will purge all
   * outstanding writes so they are abandoned.
   *
   * <p>All writes will be purged, including transactions and {@link DatabaseReference#onDisconnect}
   * writes. The writes will be rolled back locally, perhaps triggering events for affected event
   * listeners, and the client will not (re-)send them to the Firebase backend.
   */
  public void purgeOutstandingWrites() {
    final Repo repo = ensureRepo();
    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            repo.purgeOutstandingWrites();
          }
        });
  }

  /**
   * Resumes our connection to the Firebase Database backend after a previous {@link #goOffline()}
   * call.
   */
  public void goOnline() {
    RepoManager.resume(ensureRepo());
  }

  /**
   * Shuts down our connection to the Firebase Database backend until {@link #goOnline()} is called.
   */
  public void goOffline() {
    RepoManager.interrupt(ensureRepo());
  }

  /**
   * By default, this is set to {@link Logger.Level#INFO INFO}. This includes any internal errors
   * ({@link Logger.Level#ERROR ERROR}) and any security debug messages ({@link Logger.Level#INFO
   * INFO}) that the client receives. Set to {@link Logger.Level#DEBUG DEBUG} to turn on the
   * diagnostic logging, and {@link Logger.Level#NONE NONE} to disable all logging.
   *
   * @param logLevel The desired minimum log level
   * @deprecated This method will be removed in a future release. Use SLF4J-based logging instead.
   *     For example, add the slf4j-simple.jar to the classpath to log to STDERR. See
   *     <a href="https://www.slf4j.org/manual.html">SLF4J user manual</a> for more details.
   */
  public synchronized void setLogLevel(Logger.Level logLevel) {
    synchronized (lock) {
      assertUnfrozen("setLogLevel");
      this.config.setLogLevel(logLevel);
    }
  }

  /**
   * The Firebase Database client will cache synchronized data and keep track of all writes you've
   * initiated while your application is running. It seamlessly handles intermittent network
   * connections and re-sends write operations when the network connection is restored.
   *
   * <p>However by default your write operations and cached data are only stored in-memory and will
   * be lost when your app restarts. By setting this value to `true`, the data will be persisted to
   * on-device (disk) storage and will thus be available again when the app is restarted (even when
   * there is no network connectivity at that time). Note that this method must be called before
   * creating your first Database reference and only needs to be called once per application.
   *
   * @param isEnabled Set to true to enable disk persistence, set to false to disable it.
   */
  public synchronized void setPersistenceEnabled(boolean isEnabled) {
    synchronized (lock) {
      assertUnfrozen("setPersistenceEnabled");
      this.config.setPersistenceEnabled(isEnabled);
    }
  }

  /**
   * By default Firebase Database will use up to 10MB of disk space to cache data. If the cache
   * grows beyond this size, Firebase Database will start removing data that hasn't been recently
   * used. If you find that your application caches too little or too much data, call this method to
   * change the cache size. This method must be called before creating your first Database reference
   * and only needs to be called once per application.
   *
   * <p>Note that the specified cache size is only an approximation and the size on disk may
   * temporarily exceed it at times. Cache sizes smaller than 1 MB or greater than 100 MB are not
   * supported.
   *
   * @param cacheSizeInBytes The new size of the cache in bytes.
   */
  public void setPersistenceCacheSizeBytes(long cacheSizeInBytes) {
    synchronized (lock) {
      assertUnfrozen("setPersistenceCacheSizeBytes");
      this.config.setPersistenceCacheSizeBytes(cacheSizeInBytes);
    }
  }

  private void assertUnfrozen(String methodCalled) {
    synchronized (lock) {
      checkNotDestroyed();
      if (this.repo != null) {
        throw new DatabaseException(
            "Calls to "
                + methodCalled
                + "() must be made before any "
                + "other usage of FirebaseDatabase instance.");
      }
    }
  }

  /**
   * Initializes the Repo if not already initialized.
   */
  private Repo ensureRepo() {
    synchronized (lock) {
      checkNotDestroyed();
      if (repo == null) {
        repo = RepoManager.createRepo(this.config, this.repoInfo, this);
      }
      return repo;
    }
  }

  void checkNotDestroyed() {
    synchronized (lock) {
      checkState(!destroyed.get(),
          "FirebaseDatabase instance is no longer alive. This happens when "
              + "the parent FirebaseApp instance has been deleted.");
    }
  }

  // for testing
  DatabaseConfig getConfig() {
    return this.config;
  }

  void destroy() {
    synchronized (lock) {
      if (destroyed.get()) {
        return;
      }

      if (repo != null) {
        RepoManager.interrupt(repo);
        repo = null;
      }
      RepoManager.interrupt(getConfig());
      destroyed.compareAndSet(false, true);
    }
  }

  private static final String SERVICE_ID = FirebaseDatabase.class.getName();

  private static class DatabaseInstances {
    private final Map<RepoInfo, FirebaseDatabase> databases =
        Collections.synchronizedMap(new HashMap<RepoInfo, FirebaseDatabase>());

    void put(RepoInfo repo, FirebaseDatabase database) {
      databases.put(repo, database);
    }

    FirebaseDatabase get(RepoInfo repo) {
      return databases.get(repo);
    }

    void destroy() {
      synchronized (databases) {
        for (FirebaseDatabase database : databases.values()) {
          database.destroy();
        }
        databases.clear();
      }
    }
  }

  private static class FirebaseDatabaseService extends FirebaseService<DatabaseInstances> {
    FirebaseDatabaseService() {
      super(SERVICE_ID, new DatabaseInstances());
    }

    @Override
    public void destroy() {
      instance.destroy();
    }
  }
}
