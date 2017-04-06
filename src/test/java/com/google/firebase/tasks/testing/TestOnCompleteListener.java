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
public class TestOnCompleteListener<TResult> implements OnCompleteListener<TResult> {

  private static final long TIMEOUT_MS = 500;

  private final CountDownLatch mLatch = new CountDownLatch(1);
  private Task<TResult> mTask;
  private Thread mThread;

  @Override
  public void onComplete(@NonNull Task<TResult> task) {
    mTask = task;
    mThread = Thread.currentThread();
    mLatch.countDown();
  }

  /**
   * Blocks until the {@link #onComplete} is called.
   */
  public boolean await() throws InterruptedException {
    return mLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  /**
   * Returns the Task passed to {@link #onComplete}.
   */
  public Task<TResult> getTask() {
    Preconditions.checkState(mLatch.getCount() == 0, "onComplete has not been called");
    return mTask;
  }

  /**
   * Returns the Thread that {@link #onComplete} was called on.
   */
  public Thread getThread() {
    Preconditions.checkState(mLatch.getCount() == 0, "onFailure has not been called");
    return mThread;
  }
}
