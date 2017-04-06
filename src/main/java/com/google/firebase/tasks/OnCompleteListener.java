package com.google.firebase.tasks;

import com.google.firebase.internal.NonNull;

/**
 * Listener called when a {@link Task} completes.
 *
 * @param <T> the Task's result type
 * @see Task#addOnCompleteListener(OnCompleteListener)
 */
public interface OnCompleteListener<T> {

  /**
   * Called when the Task completes.
   *
   * @param task the completed Task. Never null
   */
  void onComplete(@NonNull Task<T> task);
}
