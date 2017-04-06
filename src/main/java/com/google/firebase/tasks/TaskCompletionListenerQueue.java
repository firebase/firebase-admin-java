package com.google.firebase.tasks;

import com.google.firebase.internal.GuardedBy;
import com.google.firebase.internal.NonNull;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;

/**
 * A queue of listeners to call upon {@link Task} completion.
 *
 * @param <TResult> Task result type.
 */
class TaskCompletionListenerQueue<TResult> {

  private final Object mLock = new Object();

  /**
   * Lazily initialized, unbounded queue of listeners to call.
   */
  @GuardedBy("mLock")
  private Queue<TaskCompletionListener<TResult>> mQueue;

  /**
   * Indicates if a flush is already in progress. While this is true, further calls to flush()
   * will do nothing.
   */
  @GuardedBy("mLock")
  private boolean mFlushing;

  // TODO(jstembridge): Define behaviour for duplicate listeners.
  public void add(@NonNull TaskCompletionListener<TResult> listener) {
    synchronized (mLock) {
      if (mQueue == null) {
        mQueue = new ArrayDeque<>();
      }
      mQueue.add(listener);
    }
  }

  public boolean removeAll(@NonNull Collection<TaskCompletionListener<TResult>> listeners) {
    synchronized (mLock) {
      return mQueue == null || mQueue.removeAll(listeners);
    }
  }

  public void flush(@NonNull Task<TResult> task) {
    synchronized (mLock) {
      if (mQueue == null || mFlushing) {
        return;
      }
      mFlushing = true;
    }

    while (true) {
      TaskCompletionListener<TResult> next;
      synchronized (mLock) {
        next = mQueue.poll();
        if (next == null) {
          mFlushing = false;
          return;
        }
      }

      // Call outside the lock to avoid potential deadlocks with client code.
      next.onComplete(task);
    }
  }
}
