package com.google.firebase;

import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Preconditions;

/** Base class for all Firebase exceptions. */
public class FirebaseException extends Exception {

  // TODO(b/27677218): Exceptions should have non-empty messages.
  @Deprecated
  protected FirebaseException() {}

  public FirebaseException(@NonNull String detailMessage) {
    super(Preconditions.checkNotEmpty(detailMessage, "Detail message must not be empty"));
  }

  public FirebaseException(@NonNull String detailMessage, Throwable cause) {
    super(Preconditions.checkNotEmpty(detailMessage, "Detail message must not be empty"), cause);
  }
}
