package com.google.firebase.tasks;

import com.google.firebase.internal.NonNull;
import java.util.concurrent.Executor;

/**
 * A {@link TaskCompletionListener} that wraps a {@link Continuation}.
 */
class ContinueWithCompletionListener<TResult, TContinuationResult>
    implements TaskCompletionListener<TResult> {

  private final Executor mExecutor;
  private final Continuation<TResult, TContinuationResult> mContinuation;
  private final TaskImpl<TContinuationResult> mContinuationTask;

  public ContinueWithCompletionListener(
      @NonNull Executor executor,
      @NonNull Continuation<TResult, TContinuationResult> continuation,
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
        TContinuationResult result;
        try {
          result = mContinuation.then(task);
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

        mContinuationTask.setResult(result);
      }
    });
  }

  @Override
  public void cancel() {
    throw new UnsupportedOperationException();
  }
}
