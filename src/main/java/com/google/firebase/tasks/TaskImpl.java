package com.google.firebase.tasks;

import com.google.firebase.internal.GuardedBy;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import com.google.firebase.internal.Preconditions;
import java.util.concurrent.Executor;

/**
 * Default implementation of {@link Task}.
 */
final class TaskImpl<TResult> extends Task<TResult> {

  private final Object mLock = new Object();
  private final TaskCompletionListenerQueue<TResult> mListenerQueue =
      new TaskCompletionListenerQueue<>();

  @GuardedBy("mLock")
  private boolean mComplete;

  @GuardedBy("mLock")
  private TResult mResult;

  @GuardedBy("mLock")
  private Exception mException;

  @Override
  public boolean isComplete() {
    synchronized (mLock) {
      return mComplete;
    }
  }

  @Override
  public boolean isSuccessful() {
    synchronized (mLock) {
      return mComplete && mException == null;
    }
  }

  @Override
  public TResult getResult() {
    synchronized (mLock) {
      checkCompleteLocked();

      if (mException != null) {
        throw new RuntimeExecutionException(mException);
      }

      return mResult;
    }
  }

  @Override
  public <X extends Throwable> TResult getResult(@NonNull Class<X> exceptionType) throws X {
    synchronized (mLock) {
      checkCompleteLocked();

      if (exceptionType.isInstance(mException)) {
        throw exceptionType.cast(mException);
      }
      if (mException != null) {
        throw new RuntimeExecutionException(mException);
      }

      return mResult;
    }
  }

  @Nullable
  @Override
  public Exception getException() {
    synchronized (mLock) {
      return mException;
    }
  }

  @NonNull
  @Override
  public Task<TResult> addOnSuccessListener(@NonNull OnSuccessListener<? super TResult> listener) {
    return addOnSuccessListener(TaskExecutors.DEFAULT_THREAD_POOL, listener);
  }

  @NonNull
  @Override
  public Task<TResult> addOnSuccessListener(
      @NonNull Executor executor, @NonNull OnSuccessListener<? super TResult> listener) {
    mListenerQueue.add(new OnSuccessCompletionListener<>(executor, listener));
    flushIfComplete();
    return this;
  }

  @NonNull
  @Override
  public Task<TResult> addOnFailureListener(@NonNull OnFailureListener listener) {
    return addOnFailureListener(TaskExecutors.DEFAULT_THREAD_POOL, listener);
  }

  @NonNull
  @Override
  public Task<TResult> addOnFailureListener(
      @NonNull Executor executor, @NonNull OnFailureListener listener) {
    mListenerQueue.add(new OnFailureCompletionListener<TResult>(executor, listener));
    flushIfComplete();
    return this;
  }

  @NonNull
  @Override
  public Task<TResult> addOnCompleteListener(@NonNull OnCompleteListener<TResult> listener) {
    return addOnCompleteListener(TaskExecutors.DEFAULT_THREAD_POOL, listener);
  }

  @NonNull
  @Override
  public Task<TResult> addOnCompleteListener(
      @NonNull Executor executor, @NonNull OnCompleteListener<TResult> listener) {
    mListenerQueue.add(new OnCompleteCompletionListener<>(executor, listener));
    flushIfComplete();
    return this;
  }

  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> continueWith(
      @NonNull Continuation<TResult, TContinuationResult> continuation) {
    return continueWith(TaskExecutors.DEFAULT_THREAD_POOL, continuation);
  }

  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> continueWith(
      @NonNull Executor executor,
      @NonNull Continuation<TResult, TContinuationResult> continuation) {
    TaskImpl<TContinuationResult> continuationTask = new TaskImpl<>();
    mListenerQueue.add(
        new ContinueWithCompletionListener<>(executor, continuation, continuationTask));
    flushIfComplete();
    return continuationTask;
  }

  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> continueWithTask(
      @NonNull Continuation<TResult, Task<TContinuationResult>> continuation) {
    return continueWithTask(TaskExecutors.DEFAULT_THREAD_POOL, continuation);
  }

  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> continueWithTask(
      @NonNull Executor executor,
      @NonNull Continuation<TResult, Task<TContinuationResult>> continuation) {
    TaskImpl<TContinuationResult> continuationTask = new TaskImpl<>();
    mListenerQueue.add(
        new ContinueWithTaskCompletionListener<>(executor, continuation, continuationTask));
    flushIfComplete();
    return continuationTask;
  }

  public void setResult(TResult result) {
    synchronized (mLock) {
      checkNotCompleteLocked();
      mComplete = true;
      mResult = result;
    }
    // Intentionally outside the lock.
    mListenerQueue.flush(this);
  }

  public boolean trySetResult(TResult result) {
    synchronized (mLock) {
      if (mComplete) {
        return false;
      }
      mComplete = true;
      mResult = result;
    }
    // Intentionally outside the lock.
    mListenerQueue.flush(this);
    return true;
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  public void setException(@NonNull Exception e) {
    Preconditions.checkNotNull(e, "Exception must not be null");
    synchronized (mLock) {
      checkNotCompleteLocked();
      mComplete = true;
      mException = e;
    }
    // Intentionally outside the lock.
    mListenerQueue.flush(this);
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  public boolean trySetException(@NonNull Exception e) {
    Preconditions.checkNotNull(e, "Exception must not be null");
    synchronized (mLock) {
      if (mComplete) {
        return false;
      }
      mComplete = true;
      mException = e;
    }
    // Intentionally outside the lock.
    mListenerQueue.flush(this);
    return true;
  }

  @GuardedBy("mLock")
  private void checkCompleteLocked() {
    Preconditions.checkState(mComplete, "Task is not yet complete");
  }

  @GuardedBy("mLock")
  private void checkNotCompleteLocked() {
    Preconditions.checkState(!mComplete, "Task is already complete");
  }

  private void flushIfComplete() {
    synchronized (mLock) {
      if (!mComplete) {
        return;
      }
    }
    // Intentionally outside the lock.
    mListenerQueue.flush(this);
  }
}
