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

  public static final ThreadManager DEFAULT_THREAD_MANAGER = new GlobalThreadManager();

  /**
   * A {@code ThreadManager} implementation that uses the same executor service
   * across all active apps. The executor service is initialized when the first app is initialized,
   * and terminated when the last app is deleted. This class is thread safe.
   */
  static class GlobalThreadManager extends ThreadManager {

    private final Object lock = new Object();
    private final Set<String> apps = new HashSet<>();
    private ExecutorService executorService;

    @Override
    protected ExecutorService getExecutor(FirebaseApp app) {
      synchronized (lock) {
        if (executorService == null) {
          logger.debug("Initializing default global executor");
          ThreadFactory threadFactory = new ThreadFactoryBuilder()
              .setNameFormat("firebase-default-%d")
              .setDaemon(true)
              .setThreadFactory(getThreadFactory())
              .build();
          executorService = Executors.newCachedThreadPool(threadFactory);
        }
        apps.add(app.getName());
        return executorService;
      }
    }

    @Override
    protected void releaseExecutor(FirebaseApp app, ExecutorService executor) {
      synchronized (lock) {
        if (apps.remove(app.getName()) && apps.isEmpty()) {
          logger.debug("Shutting down default global executor");
          executorService.shutdownNow();
          executorService = null;
        }
      }
    }

    protected ThreadFactory getThreadFactory() {
      return Executors.defaultThreadFactory();
    }
  }
}
