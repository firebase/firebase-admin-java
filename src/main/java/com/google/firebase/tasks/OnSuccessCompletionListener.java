package com.google.firebase.tasks;

import com.google.firebase.internal.GuardedBy;
import com.google.firebase.internal.NonNull;

import java.util.concurrent.Executor;

/**
 * A {@link TaskCompletionListener} that wraps an {@link OnSuccessListener}.
 */
class OnSuccessCompletionListener<T> implements TaskCompletionListener<T> {

  private final Executor executor;
  private final Object lock = new Object();

  @GuardedBy("lock")
  private OnSuccessListener<? super T> onSuccess;

  public OnSuccessCompletionListener(
      @NonNull Executor executor, @NonNull OnSuccessListener<? super T> onSuccess) {
    this.executor = executor;
    this.onSuccess = onSuccess;
  }

  @Override
  public void onComplete(@NonNull final Task<T> task) {
    if (task.isSuccessful()) {
      synchronized (lock) {
        if (onSuccess == null) {
          return;
        }
      }
      executor.execute(new Runnable() {
        @Override
        public void run() {
          synchronized (lock) {
            if (onSuccess != null) {
              onSuccess.onSuccess(task.getResult());
            }
          }
        }
      });
    }
  }

  @Override
  public void cancel() {
    synchronized (lock) {
      onSuccess = null;
    }
  }
}
