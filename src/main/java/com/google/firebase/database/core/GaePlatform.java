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
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.connection.ConnectionContext;
import com.google.firebase.database.connection.HostInfo;
import com.google.firebase.database.connection.PersistentConnection;
import com.google.firebase.database.connection.PersistentConnectionImpl;
import com.google.firebase.database.core.persistence.PersistenceManager;
import com.google.firebase.database.logging.DefaultLogger;
import com.google.firebase.database.logging.LogWrapper;
import com.google.firebase.database.logging.Logger;
import com.google.firebase.database.utilities.DefaultRunLoop;
import com.google.firebase.internal.GaeThreadFactory;
import com.google.firebase.internal.RevivingScheduledExecutor;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Represents a Google AppEngine platform.
 *
 * <p>This class is not thread-safe.
 */
class GaePlatform implements Platform {

  private static final String PROCESS_PLATFORM = "AppEngine";
  private final FirebaseApp firebaseApp;

  public GaePlatform(FirebaseApp firebaseApp) {
    this.firebaseApp = firebaseApp;
  }

  public static boolean isActive() {
    return GaeThreadFactory.isAvailable();
  }

  @Override
  public Logger newLogger(Context ctx, Logger.Level level, List<String> components) {
    return new DefaultLogger(level, components);
  }

  private ThreadFactory getGaeThreadFactory() {
    return ImplFirebaseTrampolines.getThreadFactory(firebaseApp);
  }

  @Override
  public EventTarget newEventTarget(Context ctx) {
    RevivingScheduledExecutor eventExecutor =
        new RevivingScheduledExecutor(getGaeThreadFactory(), "FirebaseDatabaseEventTarget", true);
    return new ThreadPoolEventTarget(eventExecutor);
  }

  @Override
  public RunLoop newRunLoop(final Context context) {
    final LogWrapper logger = context.getLogger(RunLoop.class);
    return new DefaultRunLoop(getGaeThreadFactory(), /* periodicRestart= */ true, context) {
      @Override
      public void handleException(Throwable e) {
        logger.error(DefaultRunLoop.messageForException(e), e);
      }
    };
  }

  @Override
  public AuthTokenProvider newAuthTokenProvider(ScheduledExecutorService executorService) {
    return new JvmAuthTokenProvider(this.firebaseApp, executorService);
  }

  @Override
  public PersistentConnection newPersistentConnection(
      Context context,
      ConnectionContext connectionContext,
      HostInfo info,
      PersistentConnection.Delegate delegate) {
    return new PersistentConnectionImpl(context.getConnectionContext(), info, delegate);
  }

  @Override
  public String getUserAgent(Context ctx) {
    return PROCESS_PLATFORM + "/" + DEVICE;
  }

  @Override
  public String getPlatformVersion() {
    return "gae-" + FirebaseDatabase.getSdkVersion();
  }

  @Override
  public PersistenceManager createPersistenceManager(Context ctx, String namespace) {
    return null;
  }

  @Override
  public ThreadInitializer getThreadInitializer() {
    return new ThreadInitializer() {
      @Override
      public void setName(Thread t, String name) {
        // Unsupported by GAE
      }

      @Override
      public void setDaemon(Thread t, boolean isDaemon) {
        // Unsupported by GAE
      }

      @Override
      public void setUncaughtExceptionHandler(Thread t, Thread.UncaughtExceptionHandler handler) {
        // Unsupported by GAE
      }
    };
  }
}
