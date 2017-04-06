package com.google.firebase.tasks;

import com.google.firebase.internal.GuardedBy;
import com.google.firebase.internal.NonNull;
import java.util.concurrent.Executor;

/**
 * A {@link TaskCompletionListener} that wraps an {@link OnSuccessListener}.
 */
class OnSuccessCompletionListener<TResult> implements TaskCompletionListener<TResult> {

  private final Executor mExecutor;
  private final Object mLock = new Object();

  @GuardedBy("mLock")
  private OnSuccessListener<? super TResult> mOnSuccess;

  public OnSuccessCompletionListener(
      @NonNull Executor executor, @NonNull OnSuccessListener<? super TResult> onSuccess) {
    mExecutor = executor;
    mOnSuccess = onSuccess;
  }

  @Override
  public void onComplete(@NonNull final Task<TResult> task) {
    if (task.isSuccessful()) {
      synchronized (mLock) {
        if (mOnSuccess == null) {
          return;
        }
      }
      mExecutor.execute(new Runnable() {
        @Override
        public void run() {
          synchronized (mLock) {
            if (mOnSuccess != null) {
              mOnSuccess.onSuccess(task.getResult());
            }
          }
        }
      });
    }
  }

  @Override
  public void cancel() {
    synchronized (mLock) {
      mOnSuccess = null;
    }
  }
}
