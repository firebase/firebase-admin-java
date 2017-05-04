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

package com.google.firebase.tasks.testing;

import static com.google.common.base.Preconditions.checkState;

import com.google.firebase.internal.NonNull;
import com.google.firebase.tasks.OnCompleteListener;
import com.google.firebase.tasks.Task;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 
 * Implementation of {@link OnCompleteListener} for use in tests.
 */
public class TestOnCompleteListener<T> implements OnCompleteListener<T> {

  private static final long TIMEOUT_MS = 500;

  private final CountDownLatch latch = new CountDownLatch(1);
  private Task<T> task;
  private Thread thread;

  @Override
  public void onComplete(@NonNull Task<T> task) {
    this.task = task;
    thread = Thread.currentThread();
    latch.countDown();
  }

  /** 
   * Blocks until the {@link #onComplete} is called.
   */
  public boolean await() throws InterruptedException {
    return latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  /** 
   * Returns the Task passed to {@link #onComplete}.
   */
  public Task<T> getTask() {
    checkState(latch.getCount() == 0, "onComplete has not been called");
    return task;
  }

  /** 
   * Returns the Thread that {@link #onComplete} was called on.
   */
  public Thread getThread() {
    checkState(latch.getCount() == 0, "onFailure has not been called");
    return thread;
  }
}
