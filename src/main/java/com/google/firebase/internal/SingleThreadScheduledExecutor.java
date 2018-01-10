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
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

public class SingleThreadScheduledExecutor extends ScheduledThreadPoolExecutor {

  public SingleThreadScheduledExecutor(String name, ThreadFactory threadFactory) {
    super(1, new ThreadFactoryBuilder()
        .setNameFormat(name)
        .setDaemon(true)
        .setThreadFactory(threadFactory)
        .build());
    setRemoveOnCancelPolicy(true);
  }

  @Override
  protected final void afterExecute(Runnable runnable, Throwable throwable) {
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
      handleException(throwable);
    }
  }

  protected void handleException(Throwable t) {

  }
}
