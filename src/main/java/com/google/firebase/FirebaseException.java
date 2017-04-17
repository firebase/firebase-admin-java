package com.google.firebase;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.firebase.internal.NonNull;

/** Base class for all Firebase exceptions. */
public class FirebaseException extends Exception {

  // TODO(b/27677218): Exceptions should have non-empty messages.
  @Deprecated
  protected FirebaseException() {}

  public FirebaseException(@NonNull String detailMessage) {
    super(detailMessage);
    checkArgument(!Strings.isNullOrEmpty(detailMessage), "Detail message must not be empty");
  }

  public FirebaseException(@NonNull String detailMessage, Throwable cause) {
    super(detailMessage, cause);
    checkArgument(!Strings.isNullOrEmpty(detailMessage), "Detail message must not be empty");
  }
}
