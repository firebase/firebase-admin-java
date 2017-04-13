package com.google.firebase.tasks.testing;

import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Preconditions;
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
    Preconditions.checkState(latch.getCount() == 0, "onFailure has not been called");
    return exception;
  }

  /** 
   * Returns the Thread that {@link #onFailure} was called on.
   */
  public Thread getThread() {
    Preconditions.checkState(latch.getCount() == 0, "onFailure has not been called");
    return thread;
  }
}
