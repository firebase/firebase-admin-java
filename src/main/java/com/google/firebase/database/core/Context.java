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
import com.google.firebase.database.utilities.DefaultRunLoop;

import java.util.concurrent.ScheduledExecutorService;

public class Context {

  private static final long DEFAULT_CACHE_SIZE = 10 * 1024 * 1024;

  FirebaseApp firebaseApp;

  EventTarget eventTarget;
  AuthTokenProvider authTokenProvider;
  RunLoop runLoop;
  String persistenceKey;
  boolean persistenceEnabled;
  long cacheSize = DEFAULT_CACHE_SIZE;

  private String userAgent;
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
      platform = new JvmPlatform(firebaseApp);
    }
    return platform;
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

  void assertUnfrozen() {
    if (frozen) {
      throw new DatabaseException(
          "Modifications to DatabaseConfig objects must occur before they are in use");
    }
  }

  public ConnectionContext getConnectionContext() {
    return new ConnectionContext(
        wrapAuthTokenProvider(this.getAuthTokenProvider()),
        this.getExecutorService(),
        this.isPersistenceEnabled(),
        FirebaseDatabase.getSdkVersion(),
        this.getUserAgent(),
        ImplFirebaseTrampolines.getThreadFactory(firebaseApp));
  }

  PersistenceManager getPersistenceManager(String firebaseId) {
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

  public EventTarget getEventTarget() {
    return eventTarget;
  }

  public RunLoop getRunLoop() {
    return runLoop;
  }

  public String getUserAgent() {
    return userAgent;
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
