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

import com.google.api.core.ApiFuture;
import com.google.firebase.tasks.OnCompleteListener;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.Tasks;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An ApiFuture implementation that wraps a {@link Task}. This is an interim solution that enables
 * us to expose Tasks as ApiFutures, until we fully remove the Task API.
 *
 * @param <T> Type of the result produced by this Future.
 */
public class TaskToApiFuture<T> implements ApiFuture<T> {

  private final Task<T> task;
  private boolean cancelled;

  public TaskToApiFuture(Task<T> task) {
    this.task = checkNotNull(task, "task must not be null");
  }

  @Override
  public void addListener(final Runnable runnable, Executor executor) {
    task.addOnCompleteListener(executor, new OnCompleteListener<T>() {
      @Override
      public void onComplete(Task<T> task) {
        runnable.run();
      }
    });
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    // Cannot be supported with Tasks
    cancelled = true;
    return false;
  }

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public boolean isDone() {
    return cancelled || task.isComplete();
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    return Tasks.await(task);
  }

  @Override
  public T get(long timeout, @NonNull TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return Tasks.await(task, timeout, unit);
  }
}
