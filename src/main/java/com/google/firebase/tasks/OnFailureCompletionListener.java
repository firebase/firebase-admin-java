package com.google.firebase.tasks;

import com.google.firebase.internal.GuardedBy;
import com.google.firebase.internal.NonNull;
import java.util.concurrent.Executor;

/**
 * A {@link TaskCompletionListener} that wraps an {@link OnFailureListener}.
 */
class OnFailureCompletionListener<TResult> implements TaskCompletionListener<TResult> {

  private final Executor mExecutor;
  private final Object mLock = new Object();

  @GuardedBy("mLock")
  private OnFailureListener mOnFailure;

  public OnFailureCompletionListener(
      @NonNull Executor executor, @NonNull OnFailureListener onFailure) {
    mExecutor = executor;
    mOnFailure = onFailure;
  }

  @Override
  public void onComplete(@NonNull final Task<TResult> task) {
    if (!task.isSuccessful()) {
      synchronized (mLock) {
        if (mOnFailure == null) {
          return;
        }
      }
      mExecutor.execute(new Runnable() {
        @Override
        public void run() {
          synchronized (mLock) {
            if (mOnFailure != null) {
              mOnFailure.onFailure(task.getException());
            }
          }
        }
      });
    }
  }

  @Override
  public void cancel() {
    synchronized (mLock) {
      mOnFailure = null;
    }
  }
}
