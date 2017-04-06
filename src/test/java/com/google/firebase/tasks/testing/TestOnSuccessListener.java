package com.google.firebase.tasks.testing;

import com.google.firebase.internal.Preconditions;
import com.google.firebase.tasks.OnSuccessListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Implementation of {@link OnSuccessListener} for use in tests. */
public class TestOnSuccessListener<TResult> implements OnSuccessListener<TResult> {

  private static final long TIMEOUT_MS = 500;

  private final CountDownLatch mLatch = new CountDownLatch(1);
  private TResult mResult;
  private Thread mThread;

  @Override
  public void onSuccess(TResult result) {
    mResult = result;
    mThread = Thread.currentThread();
    mLatch.countDown();
  }

  /** Blocks until the {@link #onSuccess} is called. */
  public boolean await() throws InterruptedException {
    return mLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  /** Returns the result passed to {@link #onSuccess}. */
  public TResult getResult() {
    Preconditions.checkState(mLatch.getCount() == 0, "onSuccess has not been called");
    return mResult;
  }

  /** Returns the Thread that {@link #onSuccess} was called on. */
  public Thread getThread() {
    Preconditions.checkState(mLatch.getCount() == 0, "onSuccess has not been called");
    return mThread;
  }
}
