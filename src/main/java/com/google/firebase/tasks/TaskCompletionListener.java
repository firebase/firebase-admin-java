package com.google.firebase.tasks;

import com.google.firebase.internal.NonNull;

/**
 * A listener that is called upon {@link Task} completion.
 *
 * @param <T> Task result type.
 */
interface TaskCompletionListener<T> {

  void onComplete(@NonNull Task<T> task);

  void cancel();
}
