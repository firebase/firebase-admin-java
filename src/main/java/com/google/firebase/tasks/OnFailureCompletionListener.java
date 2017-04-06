package com.google.firebase.tasks;

import com.google.firebase.internal.GuardedBy;
import com.google.firebase.internal.NonNull;

import java.util.concurrent.Executor;

/**
 * A {@link TaskCompletionListener} that wraps an {@link OnFailureListener}.
 */
class OnFailureCompletionListener<T> implements TaskCompletionListener<T> {

  private final Executor executor;
  private final Object lock = new Object();

  @GuardedBy("lock")
  private OnFailureListener onFailure;

  public OnFailureCompletionListener(
      @NonNull Executor executor, @NonNull OnFailureListener onFailure) {
    this.executor = executor;
    this.onFailure = onFailure;
  }

  @Override
  public void onComplete(@NonNull final Task<T> task) {
    if (!task.isSuccessful()) {
      synchronized (lock) {
        if (onFailure == null) {
          return;
        }
      }
      executor.execute(new Runnable() {
        @Override
        public void run() {
          synchronized (lock) {
            if (onFailure != null) {
              onFailure.onFailure(task.getException());
            }
          }
        }
      });
    }
  }

  @Override
  public void cancel() {
    synchronized (lock) {
      onFailure = null;
    }
  }
}
