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

import com.google.common.annotations.VisibleForTesting;

import java.security.AccessControlException;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RevivingScheduledExecutor is an implementation of ScheduledThreadPoolExecutor that uses one
 * periodically restarting worker thread as its work queue. This allows customers of this class to
 * use this executor on App Engine despite App Engine's thread-lifetime limitations.
 */
public class RevivingScheduledExecutor extends ScheduledThreadPoolExecutor {

  private static final Logger logger = LoggerFactory.getLogger(RevivingScheduledExecutor.class);

  /** Exception to throw to shut down the core threads. */
  private static final RuntimeException REVIVE_THREAD_EXCEPTION =
      new RuntimeException("Restarting Firebase Worker Thread");

  /** The lifetime of a thread. Maximum lifetime of a thread on GAE is 24 hours. */
  private static final long PERIODIC_RESTART_INTERVAL_MS = TimeUnit.HOURS.toMillis(12);

  /**
   * Time by which we offset restarts to ensure that not all threads die at the same time. This is
   * meant to decrease cross-thread liveliness issues during restarts.
   */
  private static final long PERIODIC_RESTART_OFFSET_MS = TimeUnit.MINUTES.toMillis(5);

  private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);

  private final long initialDelayMs;
  private final long timeoutMs;

  // Flag set before throwing a REVIVE_THREAD_EXCEPTION and unset once a new thread has been
  // created.  Used to call afterRestart() appropriately.
  private AtomicBoolean requestedRestart = new AtomicBoolean();

  /**
   * Creates a new RevivingScheduledExecutor that optionally restarts its worker thread every twelve
   * hours.
   *
   * @param threadFactory Thread factory to use to restart threads.
   * @param threadName Name of the threads in the pool.
   * @param periodicRestart Periodically restart its worked threads.
   */
  public RevivingScheduledExecutor(
      final ThreadFactory threadFactory, final String threadName, final boolean periodicRestart) {
    this(
        threadFactory,
        threadName,
        periodicRestart ? PERIODIC_RESTART_OFFSET_MS * INSTANCE_COUNTER.get() : 0,
        periodicRestart ? PERIODIC_RESTART_INTERVAL_MS : -1);
  }

  @VisibleForTesting
  RevivingScheduledExecutor(
      final ThreadFactory threadFactory,
      final String threadName,
      final long initialDelayMs,
      final long timeoutMs) {
    super(0);
    INSTANCE_COUNTER.incrementAndGet();
    this.initialDelayMs = initialDelayMs;
    this.timeoutMs = timeoutMs;
    setThreadFactory(
        new ThreadFactory() {
          @Override
          public Thread newThread(Runnable r) {
            logger.debug("Creating new thread for: {}", threadName);
            Thread thread = threadFactory.newThread(r);
            try {
              thread.setName(threadName);
              thread.setDaemon(true);
            } catch (AccessControlException ignore) {
              // Unsupported on App Engine.
            }
            if (requestedRestart.getAndSet(false)) {
              afterRestart();
            }
            return thread;
          }
        });
  }

  @Override
  public void execute(Runnable runnable) {
    // This gets called when the execute() method from Executor is directly invoked.
    ensureRunning();
    super.execute(runnable);
  }

  @Override
  protected <V> RunnableScheduledFuture<V> decorateTask(
      Runnable runnable, RunnableScheduledFuture<V> task) {
    // This gets called by ScheduledThreadPoolExecutor before scheduling a Runnable.
    ensureRunning();
    return task;
  }

  @Override
  protected <V> RunnableScheduledFuture<V> decorateTask(
      Callable<V> callable, RunnableScheduledFuture<V> task) {
    // This gets called by ScheduledThreadPoolExecutor before scheduling a Callable.
    ensureRunning();
    return task;
  }

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

    if (throwable == REVIVE_THREAD_EXCEPTION) {
      // Re-throwing this exception will kill the thread and cause
      // ScheduledThreadPoolExecutor to
      // spawn a new thread.
      throw (RuntimeException) throwable;
    } else if (throwable != null) {
      handleException(throwable);
    }
  }

  /**
   * Called when an exception occurs during execution of a Runnable/Callable. The default
   * implementation does nothing.
   */
  protected void handleException(Throwable throwable) {}

  /** Called before the worker thread gets shutdown before a restart. */
  protected void beforeRestart() {}

  /** Called after the worker thread got recreated after a restart. */
  protected void afterRestart() {}

  private synchronized void ensureRunning() {
    if (getCorePoolSize() == 0) {
      setCorePoolSize(1);
      schedulePeriodicShutdown();
    }
  }

  private void schedulePeriodicShutdown() {
    if (timeoutMs >= 0) {
      @SuppressWarnings("unused")
      Future<?> possiblyIgnoredError =
          schedule(
              new Runnable() {
                @Override
                public void run() {
                  // We have to manually reschedule this task here as periodic tasks get
                  // cancelled after
                  // throwing exceptions.
                  @SuppressWarnings("unused")
                  Future<?> possiblyIgnoredError1 =
                      RevivingScheduledExecutor.this.schedule(
                          this, timeoutMs, TimeUnit.MILLISECONDS);
                  requestedRestart.set(true);
                  beforeRestart();
                  throw REVIVE_THREAD_EXCEPTION;
                }
              },
              initialDelayMs + timeoutMs,
              TimeUnit.MILLISECONDS);
    }
  }
}
