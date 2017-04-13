package com.google.firebase.tasks;

/**
 * Listener called when a {@link Task} completes successfully.
 *
 * @param <T> the Task's result type
 * @see Task#addOnSuccessListener(OnSuccessListener)
 */
public interface OnSuccessListener<T> {

  /**
   * Called when the {@link Task} completes successfully.
   *
   * @param result the result of the Task
   */
  void onSuccess(T result);
}
