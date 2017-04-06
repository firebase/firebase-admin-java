package com.google.firebase.tasks;

import com.google.firebase.internal.GuardedBy;
import com.google.firebase.internal.NonNull;

import java.util.concurrent.Executor;

/** 
 * A {@link TaskCompletionListener} that wraps an {@link OnCompleteListener}.
 */
class OnCompleteCompletionListener<T> implements TaskCompletionListener<T> {

  private final Executor executor;
  private final Object lock = new Object();

  @GuardedBy("lock")
  private OnCompleteListener<T> onComplete;

  public OnCompleteCompletionListener(
      @NonNull Executor executor, @NonNull OnCompleteListener<T> onComplete) {
    this.executor = executor;
    this.onComplete = onComplete;
  }

  @Override
  public void onComplete(@NonNull final Task<T> task) {
    synchronized (lock) {
      if (onComplete == null) {
        return;
      }
    }
    executor.execute(
        new Runnable() {
          @Override
          public void run() {
            synchronized (lock) {
              if (onComplete != null) {
                onComplete.onComplete(task);
              }
            }
          }
        });
  }

  @Override
  public void cancel() {
    synchronized (lock) {
      onComplete = null;
    }
  }
}
