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

package com.google.firebase.tasks;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.google.firebase.internal.GuardedBy;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;

import java.util.concurrent.Executor;

/** 
 * Default implementation of {@link Task}.
 */
final class TaskImpl<T> extends Task<T> {

  private final Object lock = new Object();
  private final TaskCompletionListenerQueue<T> listenerQueue = new TaskCompletionListenerQueue<>();

  @GuardedBy("lock")
  private boolean complete;

  @GuardedBy("lock")
  private T result;

  @GuardedBy("lock")
  private Exception exception;

  @Override
  public boolean isComplete() {
    synchronized (lock) {
      return complete;
    }
  }

  @Override
  public boolean isSuccessful() {
    synchronized (lock) {
      return complete && exception == null;
    }
  }

  @Override
  public T getResult() {
    synchronized (lock) {
      checkCompleteLocked();

      if (exception != null) {
        throw new RuntimeExecutionException(exception);
      }

      return result;
    }
  }

  @Override
  public <X extends Throwable> T getResult(@NonNull Class<X> exceptionType) throws X {
    synchronized (lock) {
      checkCompleteLocked();

      if (exceptionType.isInstance(exception)) {
        throw exceptionType.cast(exception);
      }
      if (exception != null) {
        throw new RuntimeExecutionException(exception);
      }

      return result;
    }
  }

  public void setResult(T result) {
    synchronized (lock) {
      checkNotCompleteLocked();
      complete = true;
      this.result = result;
    }
    // Intentionally outside the lock.
    listenerQueue.flush(this);
  }

  @Nullable
  @Override
  public Exception getException() {
    synchronized (lock) {
      return exception;
    }
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  public void setException(@NonNull Exception e) {
    checkNotNull(e, "Exception must not be null");
    synchronized (lock) {
      checkNotCompleteLocked();
      complete = true;
      exception = e;
    }
    // Intentionally outside the lock.
    listenerQueue.flush(this);
  }

  @NonNull
  @Override
  public Task<T> addOnSuccessListener(@NonNull OnSuccessListener<? super T> listener) {
    return addOnSuccessListener(TaskExecutors.DEFAULT_THREAD_POOL, listener);
  }

  @NonNull
  @Override
  public Task<T> addOnSuccessListener(
      @NonNull Executor executor, @NonNull OnSuccessListener<? super T> listener) {
    listenerQueue.add(new OnSuccessCompletionListener<>(executor, listener));
    flushIfComplete();
    return this;
  }

  @NonNull
  @Override
  public Task<T> addOnFailureListener(@NonNull OnFailureListener listener) {
    return addOnFailureListener(TaskExecutors.DEFAULT_THREAD_POOL, listener);
  }

  @NonNull
  @Override
  public Task<T> addOnFailureListener(
      @NonNull Executor executor, @NonNull OnFailureListener listener) {
    listenerQueue.add(new OnFailureCompletionListener<T>(executor, listener));
    flushIfComplete();
    return this;
  }

  @NonNull
  @Override
  public Task<T> addOnCompleteListener(@NonNull OnCompleteListener<T> listener) {
    return addOnCompleteListener(TaskExecutors.DEFAULT_THREAD_POOL, listener);
  }

  @NonNull
  @Override
  public Task<T> addOnCompleteListener(
      @NonNull Executor executor, @NonNull OnCompleteListener<T> listener) {
    listenerQueue.add(new OnCompleteCompletionListener<>(executor, listener));
    flushIfComplete();
    return this;
  }

  @NonNull
  @Override
  public <R> Task<R> continueWith(@NonNull Continuation<T, R> continuation) {
    return continueWith(TaskExecutors.DEFAULT_THREAD_POOL, continuation);
  }

  @NonNull
  @Override
  public <R> Task<R> continueWith(
      @NonNull Executor executor, @NonNull Continuation<T, R> continuation) {
    TaskImpl<R> continuationTask = new TaskImpl<>();
    listenerQueue.add(
        new ContinueWithCompletionListener<>(executor, continuation, continuationTask));
    flushIfComplete();
    return continuationTask;
  }

  @NonNull
  @Override
  public <R> Task<R> continueWithTask(@NonNull Continuation<T, Task<R>> continuation) {
    return continueWithTask(TaskExecutors.DEFAULT_THREAD_POOL, continuation);
  }

  @NonNull
  @Override
  public <R> Task<R> continueWithTask(
      @NonNull Executor executor, @NonNull Continuation<T, Task<R>> continuation) {
    TaskImpl<R> continuationTask = new TaskImpl<>();
    listenerQueue.add(
        new ContinueWithTaskCompletionListener<>(executor, continuation, continuationTask));
    flushIfComplete();
    return continuationTask;
  }

  public boolean trySetResult(T result) {
    synchronized (lock) {
      if (complete) {
        return false;
      }
      complete = true;
      this.result = result;
    }
    // Intentionally outside the lock.
    listenerQueue.flush(this);
    return true;
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  public boolean trySetException(@NonNull Exception e) {
    checkNotNull(e, "Exception must not be null");
    synchronized (lock) {
      if (complete) {
        return false;
      }
      complete = true;
      exception = e;
    }
    // Intentionally outside the lock.
    listenerQueue.flush(this);
    return true;
  }

  @GuardedBy("lock")
  private void checkCompleteLocked() {
    Preconditions.checkState(complete, "Task is not yet complete");
  }

  @GuardedBy("lock")
  private void checkNotCompleteLocked() {
    Preconditions.checkState(!complete, "Task is already complete");
  }

  private void flushIfComplete() {
    synchronized (lock) {
      if (!complete) {
        return;
      }
    }
    // Intentionally outside the lock.
    listenerQueue.flush(this);
  }
}
