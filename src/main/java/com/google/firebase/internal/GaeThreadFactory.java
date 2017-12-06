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

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GaeThreadFactory is a thread factory that works on App Engine. It uses background threads on
 * manually-scaled GAE backends and request-scoped threads on automatically scaled instances.
 *
 * <p>This class is thread-safe.
 */
public class GaeThreadFactory implements ThreadFactory {

  private static final Logger logger = LoggerFactory.getLogger(GaeThreadFactory.class);

  public static final ExecutorService DEFAULT_EXECUTOR =
      new GaeExecutorService("LegacyFirebaseDefault");
  private static final String GAE_THREAD_MANAGER_CLASS = "com.google.appengine.api.ThreadManager";
  private static final GaeThreadFactory instance = new GaeThreadFactory();
  private final AtomicReference<ThreadFactoryWrapper> threadFactory = new AtomicReference<>(null);

  private GaeThreadFactory() {}

  public static GaeThreadFactory getInstance() {
    return instance;
  }

  /** Returns whether GaeThreadFactory can be used on this system (true for GAE). */
  public static boolean isAvailable() {
    try {
      Class.forName(GAE_THREAD_MANAGER_CLASS);
      return System.getProperty("com.google.appengine.runtime.environment") != null;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private static ThreadFactory createBackgroundFactory()
      throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
          IllegalAccessException {
    Class<?> gaeThreadManager = Class.forName(GAE_THREAD_MANAGER_CLASS);
    return (ThreadFactory) gaeThreadManager.getMethod("backgroundThreadFactory").invoke(null);
  }

  private static ThreadFactory createRequestScopedFactory()
      throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
          IllegalAccessException {
    Class<?> gaeThreadManager = Class.forName(GAE_THREAD_MANAGER_CLASS);
    return (ThreadFactory) gaeThreadManager.getMethod("currentRequestThreadFactory").invoke(null);
  }

  @Override
  public Thread newThread(Runnable r) {
    ThreadFactoryWrapper wrapper = threadFactory.get();
    if (wrapper != null) {
      return wrapper.getThreadFactory().newThread(r);
    }
    return initThreadFactory(r);
  }

  /**
   * Checks whether background thread support is available in the current environment. This method
   * forces the ThreadFactory to get fully initialized (if not already initialized), by running a
   * no-op thread.
   *
   * @return true if background thread support is available, and false otherwise.
   */
  boolean isUsingBackgroundThreads() {
    ThreadFactoryWrapper wrapper = threadFactory.get();
    if (wrapper != null) {
      return wrapper.isUsingBackgroundThreads();
    }

    // Create a no-op thread to force initialize the ThreadFactory implementation.
    // Start the resulting thread, since GAE code seems to expect that.
    initThreadFactory(new Runnable() {
      @Override
      public void run() {}
    }).start();
    return threadFactory.get().isUsingBackgroundThreads();
  }

  private Thread initThreadFactory(Runnable r) {
    ThreadFactory threadFactory;
    boolean usesBackgroundThreads = false;
    Thread thread;
    // Since we can't tell manually-scaled GAE instances apart until we spawn a thread (which
    // sends an RPC and thus is done after class initialization), we initialize both of GAE's
    // thread factories here and discard one once we detect that we are running in an
    // automatically scaled instance.
    //
    // Note: It's fine if multiple threads access this block at the same time.
    try {
      try {
        threadFactory = createBackgroundFactory();
        thread = threadFactory.newThread(r);
        usesBackgroundThreads = true;
      } catch (IllegalStateException e) {
        logger.info("Falling back to GAE's request-scoped threads. Firebase requires "
            + "manually-scaled instances for most operations.");
        threadFactory = createRequestScopedFactory();
        thread = threadFactory.newThread(r);
      }
    } catch (ClassNotFoundException
        | InvocationTargetException
        | NoSuchMethodException
        | IllegalAccessException e) {
      threadFactory =
          new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
              logger.warn("Failed to initialize native GAE thread factory. "
                  + "GaeThreadFactory cannot be used in a non-GAE environment.");
              return null;
            }
          };
      thread = null;
    }

    ThreadFactoryWrapper wrapper = new ThreadFactoryWrapper(threadFactory, usesBackgroundThreads);
    this.threadFactory.compareAndSet(null, wrapper);
    return thread;
  }

  private static class ThreadFactoryWrapper {

    private final ThreadFactory threadFactory;
    private final boolean usingBackgroundThreads;

    private ThreadFactoryWrapper(ThreadFactory threadFactory, boolean usingBackgroundThreads) {
      this.threadFactory = checkNotNull(threadFactory);
      this.usingBackgroundThreads = usingBackgroundThreads;
    }

    ThreadFactory getThreadFactory() {
      return threadFactory;
    }

    boolean isUsingBackgroundThreads() {
      return usingBackgroundThreads;
    }
  }
}
