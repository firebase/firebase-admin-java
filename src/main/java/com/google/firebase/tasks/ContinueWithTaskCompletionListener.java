package com.google.firebase.tasks;

import com.google.firebase.internal.NonNull;
import java.util.concurrent.Executor;

/**
 * A {@link TaskCompletionListener} that wraps a {@link Continuation} that returns a {@link Task}.
 */
class ContinueWithTaskCompletionListener<TResult, TContinuationResult> implements
    TaskCompletionListener<TResult>, OnSuccessListener<TContinuationResult>, OnFailureListener {

  private final Executor mExecutor;
  private final Continuation<TResult, Task<TContinuationResult>> mContinuation;
  private final TaskImpl<TContinuationResult> mContinuationTask;

  public ContinueWithTaskCompletionListener(
      @NonNull Executor executor,
      @NonNull Continuation<TResult, Task<TContinuationResult>> continuation,
      @NonNull TaskImpl<TContinuationResult> continuationTask) {
    mExecutor = executor;
    mContinuation = continuation;
    mContinuationTask = continuationTask;
  }

  @Override
  public void onComplete(@NonNull final Task<TResult> task) {
    mExecutor.execute(new Runnable() {
      @Override
      public void run() {
        Task<TContinuationResult> resultTask;
        try {
          resultTask = mContinuation.then(task);
        } catch (RuntimeExecutionException e) {
          if (e.getCause() instanceof Exception) {
            mContinuationTask.setException((Exception) e.getCause());
          } else {
            mContinuationTask.setException(e);
          }
          return;
        } catch (Exception e) {
          mContinuationTask.setException(e);
          return;
        }

        if (resultTask == null) {
          onFailure(new NullPointerException("Continuation returned null"));
          return;
        }

        resultTask.addOnSuccessListener(
            TaskExecutors.DIRECT, ContinueWithTaskCompletionListener.this);
        resultTask.addOnFailureListener(
            TaskExecutors.DIRECT, ContinueWithTaskCompletionListener.this);
      }
    });
  }

  @Override
  public void onSuccess(TContinuationResult result) {
    mContinuationTask.setResult(result);
  }

  @Override
  public void onFailure(@NonNull Exception e) {
    mContinuationTask.setException(e);
  }

  @Override
  public void cancel() {
    throw new UnsupportedOperationException();
  }
}
