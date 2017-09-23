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

  private final AtomicReference<ExecutorWrapper> executor = new AtomicReference<>();
  private final String threadName;
  private boolean shutdown;

  GaeExecutorService(String threadName) {
    checkArgument(!Strings.isNullOrEmpty(threadName));
    this.threadName = threadName;
  }

  private ExecutorService ensureExecutorService() {
    ExecutorWrapper wrapper = executor.get();
    if (wrapper == null) {
      synchronized (executor) {
        checkState(!shutdown);
        wrapper = executor.get();
        if (wrapper == null) {
          wrapper = new ExecutorWrapper(threadName);
          executor.compareAndSet(null, wrapper);
        }
      }
    }
    return wrapper.getExecutorService();
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
      ExecutorWrapper wrapper = executor.get();
      if (wrapper != null && !shutdown) {
        wrapper.getExecutorService().shutdown();
      }
      shutdown = true;
    }
  }

  @Override
  public List<Runnable> shutdownNow() {
    synchronized (executor) {
      ExecutorWrapper wrapper = executor.get();
      List<Runnable> result;
      if (wrapper != null && !shutdown) {
        result = wrapper.getExecutorService().shutdownNow();
      } else {
        result = ImmutableList.of();
      }
      shutdown = true;
      return result;
    }
  }

  @Override
  public boolean isShutdown() {
    return ensureExecutorService().isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return ensureExecutorService().isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return ensureExecutorService().awaitTermination(timeout, unit);
  }

  @Override
  public void execute(Runnable command) {
    ensureExecutorService().execute(command);
  }

  private static class ExecutorWrapper {

    private final ExecutorService executorService;

    ExecutorWrapper(String threadName) {
      GaeThreadFactory threadFactory = GaeThreadFactory.getInstance();
      if (threadFactory.isUsingBackgroundThreads()) {
        // Create a thread pool with long-lived threads if background thread support is available.
        executorService = new RevivingScheduledExecutor(threadFactory, threadName, true);
      } else {
        // Create an executor that creates a new thread for each submitted task, when background
        // thread support is not available.
        executorService =
            new ThreadPoolExecutor(
                0,
                Integer.MAX_VALUE,
                0L,
                TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(),
                threadFactory);
      }
    }

    ExecutorService getExecutorService() {
      return executorService;
    }
  }
}
