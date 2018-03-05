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

import com.google.firebase.internal.FirebaseScheduledExecutor;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** ThreadPoolEventTarget is an event target using a configurable thread pool. */
class ThreadPoolEventTarget implements EventTarget, UncaughtExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(ThreadPoolEventTarget.class);

  private final ThreadPoolExecutor executor;
  private UncaughtExceptionHandler exceptionHandler;

  ThreadPoolEventTarget(ThreadFactory threadFactory) {
    executor = new FirebaseScheduledExecutor(threadFactory, "firebase-database-event-target", this);
    executor.setKeepAliveTime(3, TimeUnit.SECONDS);
  }

  @Override
  public void postEvent(Runnable r) {
    executor.execute(r);
  }

  /**
   * Our implementation of shutdown is not immediate, it merely lowers the required number of
   * threads to 0. Depending on what we set as our timeout on the executor, this will reap the event
   * target thread after some amount of time if there's no activity
   */
  @Override
  public void shutdown() {
    executor.setCorePoolSize(0);
  }

  /**
   * Rather than launching anything, this method will ensure that our executor has at least one
   * thread available. This will keep the process alive and launch the thread if it has been reaped.
   * If the thread already exists, this is a no-op
   */
  @Override
  public void restart() {
    executor.setCorePoolSize(1);
  }

  synchronized UncaughtExceptionHandler getExceptionHandler() {
    return exceptionHandler;
  }

  synchronized void setExceptionHandler(UncaughtExceptionHandler exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
  }

  @Override
  public void uncaughtException(Thread t, Throwable e) {
    try {
      UncaughtExceptionHandler delegate;
      synchronized (this) {
        delegate = exceptionHandler;
      }
      if (delegate != null) {
        delegate.uncaughtException(t, e);
      }
    } finally {
      logger.error("Event handler threw an exception", e);
    }
  }
}
