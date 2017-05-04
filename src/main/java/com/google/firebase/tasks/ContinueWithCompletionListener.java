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
 * A {@link TaskCompletionListener} that wraps a {@link Continuation}.
 */
class ContinueWithCompletionListener<T, R> implements TaskCompletionListener<T> {

  private final Executor executor;
  private final Continuation<T, R> continuation;
  private final TaskImpl<R> continuationTask;

  public ContinueWithCompletionListener(
      @NonNull Executor executor,
      @NonNull Continuation<T, R> continuation,
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
            R result;
            try {
              result = continuation.then(task);
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

            continuationTask.setResult(result);
          }
        });
  }

  @Override
  public void cancel() {
    throw new UnsupportedOperationException();
  }
}
