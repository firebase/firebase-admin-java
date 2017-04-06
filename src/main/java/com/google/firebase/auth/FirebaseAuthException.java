package com.google.firebase.auth;

// TODO(rahulrav/isachen): Move it out from firebase-common. Temporary host it their for
// database's integration.http://b/27624510.

// TODO(rahulrav/isachen): Decide if changing this not enforcing an error code. Need to align
// with the decision in http://b/27677218. Also, need to turn this into abstract later.

import com.google.firebase.FirebaseException;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Preconditions;

/**
 * Generic exception related to Firebase Authentication. Check the error code and message for more
 * details.
 */
public class FirebaseAuthException extends FirebaseException {

  private final String mErrorCode;

  public FirebaseAuthException(@NonNull String errorCode, @NonNull String detailMessage) {
    super(detailMessage);
    mErrorCode = Preconditions.checkNotEmpty(errorCode);
  }

  /**
   * Returns an error code that may provide more information about the error.
   */
  @NonNull
  public String getErrorCode() {
    return mErrorCode;
  }
}
