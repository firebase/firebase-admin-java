package com.google.firebase.tasks;

import com.google.firebase.internal.NonNull;

/**
 * A listener that is called upon {@link Task} completion.
 *
 * @param <TResult> Task result type.
 */
interface TaskCompletionListener<TResult> {

  void onComplete(@NonNull Task<TResult> task);

  void cancel();
}
