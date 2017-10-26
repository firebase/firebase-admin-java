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

package com.google.firebase.database.core;

import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.connection.ConnectionAuthTokenProvider;
import com.google.firebase.database.connection.ConnectionContext;
import com.google.firebase.database.connection.HostInfo;
import com.google.firebase.database.connection.PersistentConnection;
import com.google.firebase.database.core.persistence.NoopPersistenceManager;
import com.google.firebase.database.core.persistence.PersistenceManager;
import com.google.firebase.database.logging.LogWrapper;
import com.google.firebase.database.logging.Logger;
import com.google.firebase.database.tubesock.ThreadConfig;
import com.google.firebase.database.utilities.DefaultRunLoop;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

public class Context {

  private static final long DEFAULT_CACHE_SIZE = 10 * 1024 * 1024;

  protected Logger logger;
  protected EventTarget eventTarget;
  protected AuthTokenProvider authTokenProvider;
  protected RunLoop runLoop;
  protected String persistenceKey;
  protected List<String> loggedComponents;
  protected String userAgent;
  protected Logger.Level logLevel = Logger.Level.INFO;
  protected boolean persistenceEnabled;
  protected long cacheSize = DEFAULT_CACHE_SIZE;
  protected FirebaseApp firebaseApp;
  private PersistenceManager forcedPersistenceManager;
  private boolean frozen = false;
  private boolean stopped = false;

  private Platform platform;

  private static ConnectionAuthTokenProvider wrapAuthTokenProvider(
      final AuthTokenProvider provider) {
    return new ConnectionAuthTokenProvider() {
      @Override
      public void getToken(boolean forceRefresh, final GetTokenCallback callback) {
        provider.getToken(
            forceRefresh,
            new AuthTokenProvider.GetTokenCompletionListener() {
              @Override
              public void onSuccess(String token) {
                callback.onSuccess(token);
              }

              @Override
              public void onError(String error) {
                callback.onError(error);
              }
            });
      }
    };
  }

  private Platform getPlatform() {
    if (platform == null) {
      if (GaePlatform.isActive()) {
        platform = new GaePlatform(firebaseApp);
      } else {
        platform = new JvmPlatform(firebaseApp);
      }
    }
    return platform;
  }

  public boolean isFrozen() {
    return frozen;
  }

  public boolean isStopped() {
    return stopped;
  }

  synchronized void freeze() {
    if (!frozen) {
      frozen = true;
      initServices();
    }
  }

  public void requireStarted() {
    if (stopped) {
      restartServices();
      stopped = false;
    }
  }

  private void initServices() {
    // Do the logger first, so that other components can get a LogWrapper
    ensureLogger();
    // Cache platform
    getPlatform();
    ensureUserAgent();
    //ensureStorage();
    ensureEventTarget();
    ensureRunLoop();
    ensureSessionIdentifier();
    ensureAuthTokenProvider();
  }

  private void restartServices() {
    eventTarget.restart();
    runLoop.restart();
  }

  void stop() {
    stopped = true;
    if (eventTarget != null) {
      eventTarget.shutdown();
    }
    if (runLoop != null) {
      runLoop.shutdown();
    }
  }

  protected void assertUnfrozen() {
    if (isFrozen()) {
      throw new DatabaseException(
          "Modifications to DatabaseConfig objects must occur before they are in use");
    }
  }

  public LogWrapper getLogger(String component) {
    return new LogWrapper(logger, component, null);
  }

  public LogWrapper getLogger(Class component) {
    return new LogWrapper(logger, component);
  }

  public LogWrapper getLogger(Class component, String prefix) {
    return new LogWrapper(logger, component, prefix);
  }

  private ThreadConfig getThreadConfig() {
    return new ThreadConfig(ImplFirebaseTrampolines.getThreadFactory(firebaseApp),
        getPlatform().getThreadInitializer());
  }

  public ConnectionContext getConnectionContext() {
    return new ConnectionContext(
        this.logger,
        wrapAuthTokenProvider(this.getAuthTokenProvider()),
        this.getExecutorService(),
        this.isPersistenceEnabled(),
        FirebaseDatabase.getSdkVersion(),
        this.getUserAgent(),
        this.getThreadConfig());
  }

  PersistenceManager getPersistenceManager(String firebaseId) {
    // TODO[persistence]: Create this once and store it.
    if (forcedPersistenceManager != null) {
      return forcedPersistenceManager;
    }
    if (this.persistenceEnabled) {
      PersistenceManager cache = platform.createPersistenceManager(this, firebaseId);
      if (cache == null) {
        throw new IllegalArgumentException(
            "You have enabled persistence, but persistence is not supported on "
                + "this platform.");
      }
      return cache;
    } else {
      return new NoopPersistenceManager();
    }
  }

  public boolean isPersistenceEnabled() {
    return this.persistenceEnabled;
  }

  public long getPersistenceCacheSizeBytes() {
    return this.cacheSize;
  }

  // For testing
  void forcePersistenceManager(PersistenceManager persistenceManager) {
    this.forcedPersistenceManager = persistenceManager;
  }

  public EventTarget getEventTarget() {
    return eventTarget;
  }

  public RunLoop getRunLoop() {
    return runLoop;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public String getPlatformVersion() {
    return getPlatform().getPlatformVersion();
  }

  public String getSessionPersistenceKey() {
    return this.persistenceKey;
  }

  public AuthTokenProvider getAuthTokenProvider() {
    return this.authTokenProvider;
  }

  public PersistentConnection newPersistentConnection(
      HostInfo info, PersistentConnection.Delegate delegate) {
    return getPlatform().newPersistentConnection(this, this.getConnectionContext(), info, delegate);
  }

  private ScheduledExecutorService getExecutorService() {
    RunLoop loop = this.getRunLoop();
    if (!(loop instanceof DefaultRunLoop)) {
      // TODO: We really need to remove this option from the public DatabaseConfig
      // object
      throw new RuntimeException("Custom run loops are not supported!");
    }
    return ((DefaultRunLoop) loop).getExecutorService();
  }

  private void ensureLogger() {
    if (logger == null) {
      logger = getPlatform().newLogger(this, logLevel, loggedComponents);
    }
  }

  private void ensureRunLoop() {
    if (runLoop == null) {
      runLoop = platform.newRunLoop(this);
    }
  }

  private void ensureEventTarget() {
    if (eventTarget == null) {
      eventTarget = getPlatform().newEventTarget(this);
    }
  }

  private void ensureUserAgent() {
    if (userAgent == null) {
      userAgent = buildUserAgent(getPlatform().getUserAgent(this));
    }
  }

  private void ensureAuthTokenProvider() {
    if (authTokenProvider == null) {
      authTokenProvider = getPlatform().newAuthTokenProvider(this.getExecutorService());
    }
  }

  private void ensureSessionIdentifier() {
    if (persistenceKey == null) {
      persistenceKey = "default";
    }
  }

  private String buildUserAgent(String platformAgent) {
    StringBuilder sb =
        new StringBuilder()
            .append("Firebase/")
            .append(Constants.WIRE_PROTOCOL_VERSION)
            .append("/")
            .append(FirebaseDatabase.getSdkVersion())
            .append("/")
            .append(platformAgent);
    return sb.toString();
  }
}
