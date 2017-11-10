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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.firebase.testing.TestUtils;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import org.junit.Test;

public class DefaultRunLoopTest {

  @Test
  public void testLifecycle() {
    MockRunLoop runLoop = new MockRunLoop();
    try {
      assertEquals(0, runLoop.getThreadPool().getCorePoolSize());
      runLoop.scheduleNow(new Runnable() {
        @Override
        public void run() {
        }
      });
      assertEquals(1, runLoop.getThreadPool().getCorePoolSize());

      runLoop.shutdown();
      assertEquals(0, runLoop.getThreadPool().getCorePoolSize());

      runLoop.restart();
      assertEquals(1, runLoop.getThreadPool().getCorePoolSize());

      assertTrue(runLoop.errors.isEmpty());
    } finally {
      runLoop.getExecutorService().shutdownNow();
    }
  }

  @Test
  public void testScheduleWithDelay() throws ExecutionException, InterruptedException {
    MockRunLoop runLoop = new MockRunLoop();
    try {
      assertEquals(0, runLoop.getThreadPool().getCorePoolSize());
      ScheduledFuture future = runLoop.schedule(new Runnable() {
        @Override
        public void run() {
        }
      }, 500L);
      assertEquals(1, runLoop.getThreadPool().getCorePoolSize());

      future.get();
      assertTrue(runLoop.errors.isEmpty());
    } finally {
      runLoop.getExecutorService().shutdownNow();
    }
  }

  @Test
  public void testExceptionHandling() throws InterruptedException {
    MockRunLoop runLoop = new MockRunLoop();
    final Semaphore semaphore = new Semaphore(0);
    UncaughtExceptionHandler exceptionHandler = new UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        semaphore.release();
      }
    };
    runLoop.setExceptionHandler(exceptionHandler);
    assertSame(exceptionHandler, runLoop.getExceptionHandler());

    try {
      assertEquals(0, runLoop.getThreadPool().getCorePoolSize());
      runLoop.scheduleNow(new Runnable() {
        @Override
        public void run() {
          throw new RuntimeException("test error");
        }
      });
      assertEquals(1, runLoop.getThreadPool().getCorePoolSize());
      semaphore.acquire();

      synchronized (runLoop.errors) {
        if (runLoop.errors.isEmpty()) {
          runLoop.errors.wait(TestUtils.TEST_TIMEOUT_MILLIS);
        }
      }
      assertEquals(1, runLoop.errors.size());
    } finally {
      runLoop.getExecutorService().shutdownNow();
    }
  }

  private static class MockRunLoop extends DefaultRunLoop {

    private final List<Throwable> errors = new ArrayList<>();

    MockRunLoop() {
      super(Executors.defaultThreadFactory());
    }

    @Override
    public void handleException(Throwable e) {
      synchronized (errors) {
        errors.add(e);
        errors.notifyAll();
      }
    }

    ScheduledThreadPoolExecutor getThreadPool() {
      return (ScheduledThreadPoolExecutor) getExecutorService();
    }
  }
}
