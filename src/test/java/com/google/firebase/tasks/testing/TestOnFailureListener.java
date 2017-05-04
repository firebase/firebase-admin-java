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
import com.google.firebase.tasks.OnFailureListener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 
 * Implementation of {@link OnFailureListener} for use in tests.
 */
public class TestOnFailureListener implements OnFailureListener {

  private static final long TIMEOUT_MS = 500;

  private final CountDownLatch latch = new CountDownLatch(1);
  private Exception exception;
  private Thread thread;

  @Override
  public void onFailure(@NonNull Exception e) {
    exception = e;
    thread = Thread.currentThread();
    latch.countDown();
  }

  /** 
   * Blocks until the {@link #onFailure} is called.
   */
  public boolean await() throws InterruptedException {
    return latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  /** 
   * Returns the exception passed to {@link #onFailure}.
   */
  public Exception getException() {
    checkState(latch.getCount() == 0, "onFailure has not been called");
    return exception;
  }

  /** 
   * Returns the Thread that {@link #onFailure} was called on.
   */
  public Thread getThread() {
    checkState(latch.getCount() == 0, "onFailure has not been called");
    return thread;
  }
}
