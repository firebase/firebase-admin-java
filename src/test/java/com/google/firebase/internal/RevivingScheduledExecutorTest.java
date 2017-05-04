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

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;

public class RevivingScheduledExecutorTest {

  private static final ThreadFactory THREAD_FACTORY = new ExceptionCatchingThreadFactory();

  @Test
  public void testAppEngineRunnable() throws InterruptedException {
    final Semaphore semaphore = new Semaphore(0);
    final Set<Long> threadIds = new HashSet<>();

    RevivingScheduledExecutor executor =
        new RevivingScheduledExecutor(THREAD_FACTORY, "testAppEngineRunnable", 0, 100);

    for (int i = 0; i < 50; ++i) {
      // We delay the execution to give the cleanup handler a chance to run. Otherwise, the
      // Executor's BlockingQueue will execute all Runnables before the internal thread gets
      // replaced.
      Thread.sleep(10);
      executor.execute(
          new Runnable() {
            @Override
            public void run() {
              threadIds.add(Thread.currentThread().getId());
              semaphore.release();
            }
          });
    }

    try {
      Assert.assertTrue(semaphore.tryAcquire(50, 10, TimeUnit.SECONDS));
      Assert.assertTrue(threadIds.size() > 1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void testAppEnginePeriodicRunnable() throws InterruptedException {
    final Set<Long> threadIds = new HashSet<>();
    final Semaphore semaphore = new Semaphore(0);

    RevivingScheduledExecutor executor =
        new RevivingScheduledExecutor(THREAD_FACTORY, "testAppEnginePeriodicRunnable", 0, 100);

    ScheduledFuture<?> future =
        executor.scheduleAtFixedRate(
            new Runnable() {
              @Override
              public void run() {
                threadIds.add(Thread.currentThread().getId());
                semaphore.release();
              }
            },
            0,
            10,
            TimeUnit.MILLISECONDS);

    try {
      Assert.assertTrue(semaphore.tryAcquire(50, 10, TimeUnit.SECONDS));
      Assert.assertTrue(threadIds.size() > 1);
    } finally {
      future.cancel(true);
      executor.shutdownNow();
    }
  }

  @Test
  public void testAppEngineDelayedRunnable() throws InterruptedException {
    final Semaphore semaphore = new Semaphore(0);
    final AtomicInteger threads = new AtomicInteger(0);

    RevivingScheduledExecutor executor =
        new RevivingScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
              threads.incrementAndGet();
              return THREAD_FACTORY.newThread(r);
            }
          },
          "testAppEngineDelayedRunnable",
          0,
          100);

    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        executor.schedule(
            new Runnable() {
              @Override
              public void run() {
                semaphore.release();
              }
            },
            750,
            TimeUnit.MILLISECONDS);

    try {
      Assert.assertFalse(semaphore.tryAcquire(1, 500, TimeUnit.MILLISECONDS));
      Assert.assertTrue(semaphore.tryAcquire(1, 500, TimeUnit.MILLISECONDS));
      Assert.assertTrue(threads.get() >= 2);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void testAppEngineDelayedCallable()
      throws InterruptedException, TimeoutException, ExecutionException {
    final AtomicInteger threads = new AtomicInteger(0);

    RevivingScheduledExecutor executor =
        new RevivingScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
              threads.incrementAndGet();
              return THREAD_FACTORY.newThread(r);
            }
          },
          "testAppEngineDelayedCallable",
          0,
          100);

    ScheduledFuture<Boolean> future =
        executor.schedule(
            new Callable<Boolean>() {
              @Override
              public Boolean call() throws Exception {
                return true;
              }
            },
            750,
            TimeUnit.MILLISECONDS);

    try {
      Assert.assertTrue(future.get(1, TimeUnit.SECONDS));
      Assert.assertTrue(threads.get() >= 2);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void testAppEngineCleanup() throws InterruptedException {
    final Semaphore beforeSemaphore = new Semaphore(0);
    final Semaphore afterSemaphore = new Semaphore(0);
    final AtomicInteger threads = new AtomicInteger(0);

    RevivingScheduledExecutor executor =
        new RevivingScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
              threads.incrementAndGet();
              return THREAD_FACTORY.newThread(r);
            }
          },
          "testAppEngineCleanup",
          0,
          100) {
        @Override
        protected void beforeRestart() {
          beforeSemaphore.release();
        }

        @Override
        protected void afterRestart() {
          afterSemaphore.release();
        }
      };

    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        executor.submit(
            new Runnable() {
              @Override
              public void run() {}
            });

    try {
      Assert.assertTrue(beforeSemaphore.tryAcquire(2, 10, TimeUnit.SECONDS));
      Assert.assertTrue(afterSemaphore.tryAcquire(2, 10, TimeUnit.SECONDS));
      Assert.assertEquals(3, threads.get());
      Assert.assertEquals(0, beforeSemaphore.availablePermits());
      Assert.assertEquals(0, afterSemaphore.availablePermits());
    } finally {
      executor.shutdownNow();
    }
  }

  private static class ExceptionCatchingThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(Runnable r) {
      if (r == null) {
        return null;
      }
      Thread thread = Executors.defaultThreadFactory().newThread(r);
      thread.setUncaughtExceptionHandler(
          new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
              // ignore -- to prevent the test output from getting cluttered
            }
          });
      return thread;
    }
  }
}
