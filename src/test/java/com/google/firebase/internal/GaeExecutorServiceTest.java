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

import static com.google.firebase.database.TestHelpers.waitFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.firebase.testing.TestUtils;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class GaeExecutorServiceTest {

  @Test
  public void testShutdownBeforeUse() throws InterruptedException {
    CountingThreadFactory threadFactory = new CountingThreadFactory();
    GaeExecutorService executorService = new GaeExecutorService("test", threadFactory);
    assertFalse(executorService.isShutdown());
    assertFalse(executorService.isTerminated());

    assertEquals(ImmutableList.of(), executorService.shutdownNow());
    assertTrue(executorService.isShutdown());
    assertTrue(executorService.isTerminated());
    assertTrue(executorService.awaitTermination(1, TimeUnit.SECONDS));
    assertEquals(0, threadFactory.counter.get());

    executorService = new GaeExecutorService("test", threadFactory);
    assertFalse(executorService.isShutdown());
    assertFalse(executorService.isTerminated());

    executorService.shutdownNow();
    assertTrue(executorService.isShutdown());
    assertTrue(executorService.isTerminated());
    assertTrue(executorService.awaitTermination(
        TestUtils.TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
    assertEquals(0, threadFactory.counter.get());
  }

  @Test
  public void testSubmit() throws InterruptedException, ExecutionException {
    CountingThreadFactory threadFactory = new CountingThreadFactory();
    GaeExecutorService executorService = new GaeExecutorService("test", threadFactory);

    final Semaphore semaphore = new Semaphore(0);
    Future future = executorService.submit(new Runnable() {
      @Override
      public void run() {
        semaphore.release();
      }
    });
    assertNotNull(future);
    waitFor(semaphore);

    future = executorService.submit(new Runnable() {
      @Override
      public void run() {
        semaphore.release();
      }
    }, "result");
    assertNotNull(future);
    waitFor(semaphore);
    assertEquals("result", future.get());

    future = executorService.submit(new Callable() {
      @Override
      public Object call() throws Exception {
        semaphore.release();
        return "result2";
      }
    });
    assertNotNull(future);
    waitFor(semaphore);
    assertEquals("result2", future.get());

    executorService.execute(new Runnable() {
      @Override
      public void run() {
        semaphore.release();
      }
    });
    waitFor(semaphore);

    assertEquals(4, threadFactory.counter.get());

    executorService.shutdown();
    assertTrue(executorService.awaitTermination(
        TestUtils.TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
    assertTrue(executorService.isShutdown());
    assertTrue(executorService.isTerminated());
  }

  private static class CountingThreadFactory implements ThreadFactory {

    private final AtomicInteger counter = new AtomicInteger(0);
    private final ThreadFactory delegate = Executors.defaultThreadFactory();

    @Override
    public Thread newThread(Runnable r) {
      counter.incrementAndGet();
      return delegate.newThread(r);
    }
  }
}
