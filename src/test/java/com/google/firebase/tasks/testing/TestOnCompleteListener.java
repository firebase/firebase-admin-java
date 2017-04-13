package com.google.firebase.tasks.testing;

import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Preconditions;
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
    Preconditions.checkState(latch.getCount() == 0, "onComplete has not been called");
    return task;
  }

  /** 
   * Returns the Thread that {@link #onComplete} was called on.
   */
  public Thread getThread() {
    Preconditions.checkState(latch.getCount() == 0, "onFailure has not been called");
    return thread;
  }
}
