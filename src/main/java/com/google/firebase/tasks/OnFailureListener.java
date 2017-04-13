package com.google.firebase.tasks;

import com.google.firebase.internal.NonNull;

/**
 * Listener called when a {@link Task} fails with an exception.
 *
 * @see Task#addOnFailureListener(OnFailureListener)
 */
public interface OnFailureListener {

  /**
   * Called when the Task fails with an exception.
   *
   * @param e the exception that caused the Task to fail. Never null
   */
  void onFailure(@NonNull Exception e);
}
