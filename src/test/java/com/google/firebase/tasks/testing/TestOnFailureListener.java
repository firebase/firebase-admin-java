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

  private final CountDownLatch mLatch = new CountDownLatch(1);
  private Exception mException;
  private Thread mThread;

  @Override
  public void onFailure(@NonNull Exception e) {
    mException = e;
    mThread = Thread.currentThread();
    mLatch.countDown();
  }

  /**
   * Blocks until the {@link #onFailure} is called.
   */
  public boolean await() throws InterruptedException {
    return mLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  /**
   * Returns the exception passed to {@link #onFailure}.
   */
  public Exception getException() {
    Preconditions.checkState(mLatch.getCount() == 0, "onFailure has not been called");
    return mException;
  }

  /**
   * Returns the Thread that {@link #onFailure} was called on.
   */
  public Thread getThread() {
    Preconditions.checkState(mLatch.getCount() == 0, "onFailure has not been called");
    return mThread;
  }
}
