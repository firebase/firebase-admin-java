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

import com.google.firebase.FirebaseApp;
import com.google.firebase.ThreadManager;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Default ThreadManager implementations used by the Admin SDK. */
public class FirebaseThreadManagers {

  private static final Logger logger = LoggerFactory.getLogger(FirebaseThreadManagers.class);

  public static final ThreadManager DEFAULT_THREAD_MANAGER = new DefaultThreadManager();

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
      ThreadFactory threadFactory = FirebaseScheduledExecutor.getThreadFactoryWithName(
          getThreadFactory(), "firebase-default-%d");

      ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(100, 100, 60L,
          TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), threadFactory);
      threadPoolExecutor.allowCoreThreadTimeOut(true);
      return threadPoolExecutor;
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
}
