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

package com.google.firebase.database.utilities;

import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.core.RunLoop;

import com.google.firebase.internal.FirebaseScheduledExecutor;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public abstract class DefaultRunLoop implements RunLoop {

  private final ScheduledThreadPoolExecutor executor;
  private UncaughtExceptionHandler exceptionHandler;

  /**
   * Creates a DefaultRunLoop that optionally restarts its threads periodically. If 'context' is
   * provided, these restarts will automatically interrupt and resume all Repo connections.
   */
  protected DefaultRunLoop(ThreadFactory threadFactory) {
    executor = new FirebaseScheduledExecutor(threadFactory, "firebase-database-worker") {
      @Override
      protected void afterExecute(Runnable runnable, Throwable throwable) {
        super.afterExecute(runnable, throwable);
        if (throwable == null && runnable instanceof Future<?>) {
          Future<?> future = (Future<?>) runnable;
          try {
            // Not all Futures will be done, e.g. when used with scheduledAtFixedRate
            if (future.isDone()) {
              future.get();
            }
          } catch (CancellationException ce) {
            // Cancellation exceptions are okay, we expect them to happen sometimes
          } catch (ExecutionException ee) {
            throwable = ee.getCause();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }

        if (throwable != null) {
          handleExceptionInternal(throwable);
        }
      }
    };

    // Core threads don't time out, this only takes effect when we drop the number of required
    // core threads
    executor.setKeepAliveTime(3, TimeUnit.SECONDS);
  }

  public static String messageForException(Throwable t) {
    if (t instanceof OutOfMemoryError) {
      return "Firebase Database encountered an OutOfMemoryError. You may need to reduce the"
          + " amount of data you are syncing to the client (e.g. by using queries or syncing"
          + " a deeper path). See "
          + "https://firebase.google"
          + ".com/docs/database/ios/structure-data#best_practices_for_data_structure"
          + " and "
          + "https://firebase.google.com/docs/database/android/retrieve-data#filtering_data";
    } else if (t instanceof DatabaseException) {
      // Exception should be self-explanatory and they shouldn't contact support.
      return "";
    } else {
      return "Uncaught exception in Firebase Database runloop ("
          + FirebaseDatabase.getSdkVersion()
          + "). Please report to firebase-database-client@google.com";
    }
  }

  private void handleExceptionInternal(Throwable e) {
    UncaughtExceptionHandler exceptionHandler;
    exceptionHandler = getExceptionHandler();
    try {
      if (exceptionHandler != null) {
        exceptionHandler.uncaughtException(Thread.currentThread(), e);
      }
    } finally {
      handleException(e);
    }
  }

  public abstract void handleException(Throwable e);

  public ScheduledExecutorService getExecutorService() {
    return this.executor;
  }

  @Override
  public void scheduleNow(final Runnable runnable) {
    executor.execute(runnable);
  }

  @Override
  @SuppressWarnings("rawtypes")
  public ScheduledFuture schedule(final Runnable runnable, long milliseconds) {
    return executor.schedule(runnable, milliseconds, TimeUnit.MILLISECONDS);
  }

  @Override
  public void shutdown() {
    executor.setCorePoolSize(0);
  }

  @Override
  public void restart() {
    executor.setCorePoolSize(1);
  }

  /**
   * Returns the exception handler currently set on this run loop. This is to be
   * used during integration testing.
   */
  public synchronized UncaughtExceptionHandler getExceptionHandler() {
    return exceptionHandler;
  }

  /**
   * Sets the specified exception handler for intercepting run loop errors. This is to be
   * used during integration testing for handling errors that may occur in the run loop's
   * worker thread.
   */
  public synchronized void setExceptionHandler(UncaughtExceptionHandler exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
  }
}
