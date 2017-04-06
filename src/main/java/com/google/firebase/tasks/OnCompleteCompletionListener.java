package com.google.firebase.tasks;

import com.google.firebase.internal.GuardedBy;
import com.google.firebase.internal.NonNull;
import java.util.concurrent.Executor;

/**
 * A {@link TaskCompletionListener} that wraps an {@link OnCompleteListener}.
 */
class OnCompleteCompletionListener<TResult> implements TaskCompletionListener<TResult> {

  private final Executor mExecutor;
  private final Object mLock = new Object();

  @GuardedBy("mLock")
  private OnCompleteListener<TResult> mOnComplete;

  public OnCompleteCompletionListener(
      @NonNull Executor executor, @NonNull OnCompleteListener<TResult> onComplete) {
    mExecutor = executor;
    mOnComplete = onComplete;
  }

  @Override
  public void onComplete(@NonNull final Task<TResult> task) {
    synchronized (mLock) {
      if (mOnComplete == null) {
        return;
      }
    }
    mExecutor.execute(new Runnable() {
      @Override
      public void run() {
        synchronized (mLock) {
          if (mOnComplete != null) {
            mOnComplete.onComplete(task);
          }
        }
      }
    });
  }

  @Override
  public void cancel() {
    synchronized (mLock) {
      mOnComplete = null;
    }
  }
}
