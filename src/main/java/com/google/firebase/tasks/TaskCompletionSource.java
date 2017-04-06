package com.google.firebase.tasks;

import com.google.firebase.internal.NonNull;

/**
 * Provides the ability to create an incomplete {@link Task} and later complete it by either calling
 * {@link #setResult} or {@link #setException}.
 */
public class TaskCompletionSource<TResult> {

  private final TaskImpl<TResult> mTask = new TaskImpl<>();

  /**
   * Completes the Task with the specified result.
   *
   * @throws IllegalStateException if the Task is already complete
   */
  public void setResult(TResult result) {
    mTask.setResult(result);
  }

  /**
   * Completes the Task with the specified result, unless the Task has already completed. If the
   * Task has already completed, the call does nothing.
   *
   * @return {@code true} if the result was set successfully, {@code false} otherwise
   */
  public boolean trySetResult(TResult result) {
    return mTask.trySetResult(result);
  }

  /**
   * Completes the Task with the specified exception.
   *
   * @throws IllegalStateException if the Task is already complete
   */
  public void setException(@NonNull Exception e) {
    mTask.setException(e);
  }

  /**
   * Completes the Task with the specified exception, unless the Task has already completed. If the
   * Task has already completed, the call does nothing.
   *
   * @return {@code true} if the exception was set successfully, {@code false} otherwise
   */
  public boolean trySetException(@NonNull Exception e) {
    return mTask.trySetException(e);
  }

  /**
   * Returns the Task.
   */
  @NonNull
  public Task<TResult> getTask() {
    return mTask;
  }
}
