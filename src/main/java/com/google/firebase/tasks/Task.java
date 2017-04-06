package com.google.firebase.tasks;

import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;

import java.util.concurrent.Executor;

/**
 * Represents an asynchronous operation.
 *
 * @param <T> the type of the result of the operation
 */
public abstract class Task<T> {

  /** 
   * Returns {@code true} if the Task is complete; {@code false} otherwise.
   */
  public abstract boolean isComplete();

  /** 
   * Returns {@code true} if the Task has completed successfully; {@code false} otherwise.
   */
  public abstract boolean isSuccessful();

  /**
   * Gets the result of the Task, if it has already completed.
   *
   * @throws IllegalStateException if the Task is not yet complete
   * @throws RuntimeExecutionException if the Task failed with an exception
   */
  public abstract T getResult();

  /**
   * Gets the result of the Task, if it has already completed.
   *
   * @throws IllegalStateException if the Task is not yet complete
   * @throws X if the Task failed with an exception of type X
   * @throws RuntimeExecutionException if the Task failed with an exception that was not of type X
   */
  public abstract <X extends Throwable> T getResult(@NonNull Class<X> exceptionType) throws X;

  /**
   * Returns the exception that caused the Task to fail. Returns {@code null} if the Task is not yet
   * complete, or completed successfully.
   */
  @Nullable
  public abstract Exception getException();

  /**
   * Adds a listener that is called if the Task completes successfully.
   *
   * <p>The listener will be called on a shared thread pool. If the Task has already completed
   * successfully, a call to the listener will be immediately scheduled. If multiple listeners are
   * added, they will be called in the order in which they were added.
   *
   * @return this Task
   */
  @NonNull
  public abstract Task<T> addOnSuccessListener(@NonNull OnSuccessListener<? super T> listener);

  /**
   * Adds a listener that is called if the Task completes successfully.
   *
   * <p>If multiple listeners are added, they will be called in the order in which they were added.
   * If the Task has already completed successfully, a call to the listener will be immediately
   * scheduled.
   *
   * @param executor the executor to use to call the listener
   * @return this Task
   */
  @NonNull
  public abstract Task<T> addOnSuccessListener(
      @NonNull Executor executor, @NonNull OnSuccessListener<? super T> listener);

  /**
   * Adds a listener that is called if the Task fails.
   *
   * <p>The listener will be called on a shared thread pool. If the Task has already failed, a call
   * to the listener will be immediately scheduled. If multiple listeners are added, they will be
   * called in the order in which they were added.
   *
   * @return this Task
   */
  @NonNull
  public abstract Task<T> addOnFailureListener(@NonNull OnFailureListener listener);

  /**
   * Adds a listener that is called if the Task fails.
   *
   * <p>If the Task has already failed, a call to the listener will be immediately scheduled. If
   * multiple listeners are added, they will be called in the order in which they were added.
   *
   * @param executor the executor to use to call the listener
   * @return this Task
   */
  @NonNull
  public abstract Task<T> addOnFailureListener(
      @NonNull Executor executor, @NonNull OnFailureListener listener);

  /**
   * Adds a listener that is called when the Task completes.
   *
   * <p>The listener will be called on a shared thread pool. If the Task is already complete, a call
   * to the listener will be immediately scheduled. If multiple listeners are added, they will be
   * called in the order in which they were added.
   *
   * @return this Task
   */
  @NonNull
  public Task<T> addOnCompleteListener(@NonNull OnCompleteListener<T> listener) {
    throw new UnsupportedOperationException("addOnCompleteListener is not implemented");
  }

  /**
   * Adds a listener that is called when the Task completes.
   *
   * <p>If the Task is already complete, a call to the listener will be immediately scheduled. If
   * multiple listeners are added, they will be called in the order in which they were added.
   *
   * @param executor the executor to use to call the listener
   * @return this Task
   */
  @NonNull
  public Task<T> addOnCompleteListener(
      @NonNull Executor executor, @NonNull OnCompleteListener<T> listener) {
    throw new UnsupportedOperationException("addOnCompleteListener is not implemented");
  }

  /**
   * Returns a new Task that will be completed with the result of applying the specified
   * Continuation to this Task.
   *
   * <p>If the Continuation throws an exception, the returned Task will fail with that exception.
   *
   * <p>The Continuation will be called on a shared thread pool.
   */
  @NonNull
  public <R> Task<R> continueWith(@NonNull Continuation<T, R> continuation) {
    throw new UnsupportedOperationException("continueWith is not implemented");
  }

  /**
   * Returns a new Task that will be completed with the result of applying the specified
   * Continuation to this Task.
   *
   * <p>If the Continuation throws an exception, the returned Task will fail with that exception.
   *
   * @param executor the executor to use to call the Continuation
   * @see Continuation#then(Task)
   */
  @NonNull
  public <R> Task<R> continueWith(
      @NonNull Executor executor, @NonNull Continuation<T, R> continuation) {
    throw new UnsupportedOperationException("continueWith is not implemented");
  }

  /**
   * Returns a new Task that will be completed with the result of applying the specified
   * Continuation to this Task.
   *
   * <p>If the Continuation throws an exception, the returned Task will fail with that exception.
   *
   * <p>The Continuation will be called on a shared thread pool.
   */
  @NonNull
  public <R> Task<R> continueWithTask(@NonNull Continuation<T, Task<R>> continuation) {
    throw new UnsupportedOperationException("continueWithTask is not implemented");
  }

  /**
   * Returns a new Task that will be completed with the result of applying the specified
   * Continuation to this Task.
   *
   * <p>If the Continuation throws an exception, the returned Task will fail with that exception.
   *
   * @param executor the executor to use to call the Continuation
   * @see Continuation#then(Task)
   */
  @NonNull
  public <R> Task<R> continueWithTask(
      @NonNull Executor executor, @NonNull Continuation<T, Task<R>> continuation) {
    throw new UnsupportedOperationException("continueWithTask is not implemented");
  }
}
