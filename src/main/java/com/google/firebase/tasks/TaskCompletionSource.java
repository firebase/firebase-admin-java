package com.google.firebase.tasks;

import com.google.firebase.internal.NonNull;

/**
 * Provides the ability to create an incomplete {@link Task} and later complete it by either calling
 * {@link #setResult} or {@link #setException}.
 */
public class TaskCompletionSource<T> {

  private final TaskImpl<T> task = new TaskImpl<>();

  /**
   * Completes the Task with the specified result.
   *
   * @throws IllegalStateException if the Task is already complete
   */
  public void setResult(T result) {
    task.setResult(result);
  }

  /**
   * Completes the Task with the specified result, unless the Task has already completed. If the
   * Task has already completed, the call does nothing.
   *
   * @return {@code true} if the result was set successfully, {@code false} otherwise
   */
  public boolean trySetResult(T result) {
    return task.trySetResult(result);
  }

  /**
   * Completes the Task with the specified exception.
   *
   * @throws IllegalStateException if the Task is already complete
   */
  public void setException(@NonNull Exception e) {
    task.setException(e);
  }

  /**
   * Completes the Task with the specified exception, unless the Task has already completed. If the
   * Task has already completed, the call does nothing.
   *
   * @return {@code true} if the exception was set successfully, {@code false} otherwise
   */
  public boolean trySetException(@NonNull Exception e) {
    return task.trySetException(e);
  }

  /** Returns the Task. */
  @NonNull
  public Task<T> getTask() {
    return task;
  }
}
