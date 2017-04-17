package com.google.firebase.auth;

// TODO(rahulrav/isachen): Move it out from firebase-common. Temporary host it their for
// database's integration.http://b/27624510.

// TODO(rahulrav/isachen): Decide if changing this not enforcing an error code. Need to align
// with the decision in http://b/27677218. Also, need to turn this into abstract later.

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.firebase.FirebaseException;
import com.google.firebase.internal.NonNull;

/**
 * Generic exception related to Firebase Authentication. Check the error code and message for more
 * details.
 */
public class FirebaseAuthException extends FirebaseException {

  private final String errorCode;

  public FirebaseAuthException(@NonNull String errorCode, @NonNull String detailMessage) {
    super(detailMessage);
    checkArgument(!Strings.isNullOrEmpty(errorCode));
    this.errorCode = errorCode;
  }

  /** Returns an error code that may provide more information about the error. */
  @NonNull
  public String getErrorCode() {
    return errorCode;
  }
}
