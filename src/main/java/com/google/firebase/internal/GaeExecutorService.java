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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An ExecutorService instance that can operate in the Google App Engine environment. When
 * available, uses background thread support to initialize an ExecutorService with long-lived
 * threads. Otherwise, creates an ExecutorService that spawns short-lived threads as tasks
 * are submitted. The actual ExecutorService implementation is lazy-loaded to prevent making
 * unnecessary RPC calls to the GAE's native ThreadFactory mechanism.
 */
class GaeExecutorService implements ExecutorService {

  private final AtomicReference<ExecutorService> executor = new AtomicReference<>();
  private final String threadName;
  private final ThreadFactory threadFactory;
  private boolean shutdown;

  GaeExecutorService(String threadName) {
    this(threadName, GaeThreadFactory.getInstance());
  }

  GaeExecutorService(String threadName, ThreadFactory threadFactory) {
    checkArgument(!Strings.isNullOrEmpty(threadName));
    this.threadName = threadName;
    this.threadFactory = threadFactory;
  }

  private ExecutorService ensureExecutorService() {
    ExecutorService executorService = executor.get();
    if (executorService == null) {
      synchronized (executor) {
        checkState(!shutdown);
        executorService = executor.get();
        if (executorService == null) {
          executorService = newExecutorService(threadFactory, threadName);
          executor.compareAndSet(null, executorService);
        }
      }
    }
    return executorService;
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    return ensureExecutorService().submit(task);
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return ensureExecutorService().submit(task, result);
  }

  @Override
  public Future<?> submit(Runnable task) {
    return ensureExecutorService().submit(task);
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    return ensureExecutorService().invokeAll(tasks);
  }

  @Override
  public <T> List<Future<T>> invokeAll(
      Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException {
    return ensureExecutorService().invokeAll(tasks, timeout, unit);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    return ensureExecutorService().invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return ensureExecutorService().invokeAny(tasks, timeout, unit);
  }

  @Override
  public void shutdown() {
    synchronized (executor) {
      ExecutorService executorService = executor.get();
      if (executorService != null && !shutdown) {
        executorService.shutdown();
      }
      shutdown = true;
    }
  }

  @Override
  public List<Runnable> shutdownNow() {
    synchronized (executor) {
      ExecutorService executorService = executor.get();
      List<Runnable> result;
      if (executorService != null && !shutdown) {
        result = executorService.shutdownNow();
      } else {
        result = ImmutableList.of();
      }
      shutdown = true;
      return result;
    }
  }

  @Override
  public boolean isShutdown() {
    synchronized (executor) {
      return shutdown;
    }
  }

  @Override
  public boolean isTerminated() {
    synchronized (executor) {
      if (!shutdown) {
        return false;
      }
      ExecutorService executorService = executor.get();
      return executorService == null || executorService.isTerminated();
    }
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    ExecutorService executorService;
    synchronized (executor) {
      executorService = executor.get();
    }
    // call await outside the lock
    return executorService == null || executorService.awaitTermination(timeout, unit);
  }

  @Override
  public void execute(Runnable command) {
    ensureExecutorService().execute(command);
  }

  private static ExecutorService newExecutorService(
      ThreadFactory threadFactory, String threadName) {
    boolean background = threadFactory instanceof GaeThreadFactory
        && ((GaeThreadFactory) threadFactory).isUsingBackgroundThreads();
    if (background) {
      // Create a thread pool with long-lived threads if background thread support is available.
      return new RevivingScheduledExecutor(threadFactory, threadName, true);
    } else {
      // Create an executor that creates a new thread for each submitted task, when background
      // thread support is not available.
      return new ThreadPoolExecutor(
          0,
          Integer.MAX_VALUE,
          0L,
          TimeUnit.SECONDS,
          new SynchronousQueue<Runnable>(),
          threadFactory);
    }
  }
}
