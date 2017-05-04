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

import com.google.firebase.internal.GuardedBy;
import com.google.firebase.internal.NonNull;

import java.util.concurrent.Executor;

/** 
 * A {@link TaskCompletionListener} that wraps an {@link OnCompleteListener}.
 */
class OnCompleteCompletionListener<T> implements TaskCompletionListener<T> {

  private final Executor executor;
  private final Object lock = new Object();

  @GuardedBy("lock")
  private OnCompleteListener<T> onComplete;

  public OnCompleteCompletionListener(
      @NonNull Executor executor, @NonNull OnCompleteListener<T> onComplete) {
    this.executor = executor;
    this.onComplete = onComplete;
  }

  @Override
  public void onComplete(@NonNull final Task<T> task) {
    synchronized (lock) {
      if (onComplete == null) {
        return;
      }
    }
    executor.execute(
        new Runnable() {
          @Override
          public void run() {
            synchronized (lock) {
              if (onComplete != null) {
                onComplete.onComplete(task);
              }
            }
          }
        });
  }

  @Override
  public void cancel() {
    synchronized (lock) {
      onComplete = null;
    }
  }
}
