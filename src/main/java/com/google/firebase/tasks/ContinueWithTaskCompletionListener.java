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

import com.google.firebase.internal.NonNull;

import java.util.concurrent.Executor;

/**
 * A {@link TaskCompletionListener} that wraps a {@link Continuation} that returns a {@link Task}.
 */
class ContinueWithTaskCompletionListener<T, R>
    implements TaskCompletionListener<T>, OnSuccessListener<R>, OnFailureListener {

  private final Executor executor;
  private final Continuation<T, Task<R>> continuation;
  private final TaskImpl<R> continuationTask;

  public ContinueWithTaskCompletionListener(
      @NonNull Executor executor,
      @NonNull Continuation<T, Task<R>> continuation,
      @NonNull TaskImpl<R> continuationTask) {
    this.executor = executor;
    this.continuation = continuation;
    this.continuationTask = continuationTask;
  }

  @Override
  public void onComplete(@NonNull final Task<T> task) {
    executor.execute(
        new Runnable() {
          @Override
          public void run() {
            Task<R> resultTask;
            try {
              resultTask = continuation.then(task);
            } catch (RuntimeExecutionException e) {
              if (e.getCause() instanceof Exception) {
                continuationTask.setException((Exception) e.getCause());
              } else {
                continuationTask.setException(e);
              }
              return;
            } catch (Exception e) {
              continuationTask.setException(e);
              return;
            }

            if (resultTask == null) {
              onFailure(new NullPointerException("Continuation returned null"));
              return;
            }

            resultTask.addOnSuccessListener(
                TaskExecutors.DIRECT, ContinueWithTaskCompletionListener.this);
            resultTask.addOnFailureListener(
                TaskExecutors.DIRECT, ContinueWithTaskCompletionListener.this);
          }
        });
  }

  @Override
  public void onSuccess(R result) {
    continuationTask.setResult(result);
  }

  @Override
  public void onFailure(@NonNull Exception e) {
    continuationTask.setException(e);
  }

  @Override
  public void cancel() {
    throw new UnsupportedOperationException();
  }
}
