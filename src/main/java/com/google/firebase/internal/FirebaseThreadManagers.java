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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Default ThreadManager implementations used by the Admin SDK. */
public class FirebaseThreadManagers {

  private static final Logger logger = LoggerFactory.getLogger(FirebaseThreadManagers.class);

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
    private ExecutorService executorService;

    @Override
    protected ExecutorService getExecutor(FirebaseApp app) {
      synchronized (lock) {
        if (executorService == null) {
          executorService = doInit();
        }
        apps.add(app.getName());
        return executorService;
      }
    }

    @Override
    protected void releaseExecutor(FirebaseApp app, ExecutorService executor) {
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
    protected abstract ExecutorService doInit();

    /**
     * Cleans up the executor service. Called when the last application is deleted.
     */
    protected abstract void doCleanup(ExecutorService executorService);
  }

  private static class DefaultThreadManager extends GlobalThreadManager {

    @Override
    protected ExecutorService doInit() {
      // Create threads as daemons to ensure JVM exit when all foreground jobs are complete.
      ThreadFactory threadFactory = new ThreadFactoryBuilder()
          .setNameFormat("firebase-default-%d")
          .setDaemon(true)
          .setThreadFactory(getThreadFactory())
          .build();
      return Executors.newCachedThreadPool(threadFactory);
    }

    @Override
    protected void doCleanup(ExecutorService executorService) {
      logger.debug("Shutting down default executor");
      executorService.shutdownNow();
    }

    @Override
    protected ThreadFactory getThreadFactory() {
      return Executors.defaultThreadFactory();
    }
  }

  /**
   * The ThreadManager implementation that will be used by default in the Google App Engine
   * environment.
   *
   * <p>Auto-scaling: Creates an ExecutorService backed by the request-scoped ThreadFactory. This
   * can be used for any short-lived task, such as the ones submitted by components like
   * FirebaseAuth. {@link #getThreadFactory()} throws an exception, since long-lived threads
   * cannot be supported. Therefore task scheduling and RTDB will not work.
   *
   * <p>Manual-scaling: Creates a single-threaded ExecutorService backed by the background
   * ThreadFactory. Keeps the threads alive indefinitely by periodically restarting them (see
   * {@link RevivingScheduledExecutor}). Threads will be terminated only when the method
   * {@link #releaseExecutor(FirebaseApp, ExecutorService)} is invoked. The
   * {@link #getThreadFactory()} also returns the background ThreadFactory enabling other
   * components in the SDK to start long-lived threads when necessary. Therefore task scheduling
   * and RTDB can be supported as if running on the regular JVM.
   *
   * <p>Basic-scaling: Behavior is similar to manual-scaling. Since the threads are kept alive
   * indefinitely, prevents the GAE idle instance shutdown. Developers are advised to use
   * a custom ThreadManager implementation if idle instance shutdown should be supported. In
   * general, a ThreadManager implementation that uses the request-scoped ThreadFactory, or the
   * background ThreadFactory with specific keep-alive times can easily facilitate GAE idle
   * instance shutdown. Note that this often comes at the cost of losing scheduled tasks and RTDB
   * support. Therefore, for these features, manual-scaling is the recommended GAE deployment mode
   * regardless of the ThreadManager implementation used.
   */
  private static class GaeThreadManager extends GlobalThreadManager {

    @Override
    protected ExecutorService doInit() {
      return new GaeExecutorService("gae-firebase-default");
    }

    @Override
    protected void doCleanup(ExecutorService executorService) {
      executorService.shutdownNow();
    }

    @Override
    protected ThreadFactory getThreadFactory() {
      GaeThreadFactory threadFactory = GaeThreadFactory.getInstance();
      checkState(threadFactory.isUsingBackgroundThreads(),
          "Failed to initialize a GAE background thread factory. Background thread support "
              + "is required to create long-lived threads.");
      return threadFactory;
    }
  }
}
