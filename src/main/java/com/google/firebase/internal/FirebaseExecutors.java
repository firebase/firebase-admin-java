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

package com.google.firebase.internal;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ThreadManager;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Default ThreadManager implementations used by the Admin SDK. */
public class FirebaseExecutors {

  private static final Logger logger = LoggerFactory.getLogger(FirebaseExecutors.class);

  public static final ThreadManager DEFAULT_THREAD_MANAGER;

  static {
    if (GaeThreadFactory.isAvailable()) {
      DEFAULT_THREAD_MANAGER = new GaeThreadManager();
    } else {
      DEFAULT_THREAD_MANAGER = new DefaultThreadManager();
    }
  }

  /**
   * An abstract ThreadManager implementation that uses the same executor service
   * across all active apps. The executor service is initialized when the first app is initialized,
   * and terminated when the last app is deleted. This class is thread safe.
   */
  abstract static class GlobalThreadManager extends ThreadManager {

    private final Object lock = new Object();
    private final Set<String> apps = new HashSet<>();

    private ScheduledExecutorService executorService;

    @Override
    protected ScheduledExecutorService getExecutor(FirebaseApp app) {
      synchronized (lock) {
        if (executorService == null) {
          executorService = doInit();
        }
        apps.add(app.getName());
        return executorService;
      }
    }

    @Override
    protected void releaseExecutor(FirebaseApp app, ScheduledExecutorService executor) {
      synchronized (lock) {
        if (apps.remove(app.getName()) && apps.isEmpty()) {
          doCleanup(executorService);
          executorService = null;
        }
      }
    }

    /**
     * Initializes the executor service. Called when the first application is initialized.
     */
    protected abstract ScheduledExecutorService doInit();

    /**
     * Cleans up the executor service. Called when the last application is deleted.
     */
    protected abstract void doCleanup(ScheduledExecutorService executorService);
  }

  private static class DefaultThreadManager extends GlobalThreadManager {

    @Override
    protected ScheduledExecutorService doInit() {
      int cores = Runtime.getRuntime().availableProcessors();
      // Create threads as daemons to ensure JVM exit when all foreground jobs are complete.
      ThreadFactory threadFactory = new ThreadFactoryBuilder()
          .setNameFormat("firebase-default-%d")
          .setDaemon(true)
          .setThreadFactory(getThreadFactory())
          .build();
      logger.debug("Initializing default executor with {} max threads", cores);
      return Executors.newScheduledThreadPool(cores, threadFactory);
    }

    @Override
    protected void doCleanup(ScheduledExecutorService executorService) {
      logger.debug("Shutting down default executor");
      executorService.shutdownNow();
    }

    @Override
    protected ThreadFactory getThreadFactory() {
      return Executors.defaultThreadFactory();
    }
  }

  private static class GaeThreadManager extends GlobalThreadManager {

    @Override
    protected ScheduledExecutorService doInit() {
      return new GaeScheduledExecutorService("gae-firebase-default");
    }

    @Override
    protected void doCleanup(ScheduledExecutorService executorService) {
      executorService.shutdownNow();
    }

    @Override

    protected ThreadFactory getThreadFactory() {
      GaeThreadFactory threadFactory = GaeThreadFactory.getInstance();
      checkState(threadFactory.isUsingBackgroundThreads(),
          "Failed to initialize a GAE background thread factory. Background thread support "
              + "is required to access the Realtime database from App Engine environment.");
      return threadFactory;
    }
  }
}
