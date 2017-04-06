package com.google.firebase.tasks;

/**
 * Listener called when a {@link Task} completes successfully.
 *
 * @param <TResult> the Task's result type
 * @see Task#addOnSuccessListener(OnSuccessListener)
 */
public interface OnSuccessListener<TResult> {

  /**
   * Called when the {@link Task} completes successfully.
   *
   * @param result the result of the Task
   */
  void onSuccess(TResult result);
}
