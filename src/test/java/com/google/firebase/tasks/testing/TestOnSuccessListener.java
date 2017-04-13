package com.google.firebase.tasks.testing;

import com.google.firebase.internal.Preconditions;
import com.google.firebase.tasks.OnSuccessListener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 
 * Implementation of {@link OnSuccessListener} for use in tests.
 */
public class TestOnSuccessListener<T> implements OnSuccessListener<T> {

  private static final long TIMEOUT_MS = 500;

  private final CountDownLatch latch = new CountDownLatch(1);
  private T result;
  private Thread thread;

  @Override
  public void onSuccess(T result) {
    this.result = result;
    this.thread = Thread.currentThread();
    latch.countDown();
  }

  /** 
   * Blocks until the {@link #onSuccess} is called.
   */
  public boolean await() throws InterruptedException {
    return latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  /** 
   * Returns the result passed to {@link #onSuccess}.
   */
  public T getResult() {
    Preconditions.checkState(latch.getCount() == 0, "onSuccess has not been called");
    return result;
  }

  /** 
   * Returns the Thread that {@link #onSuccess} was called on.
   */
  public Thread getThread() {
    Preconditions.checkState(latch.getCount() == 0, "onSuccess has not been called");
    return thread;
  }
}
