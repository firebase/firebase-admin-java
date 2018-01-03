package com.google.firebase.messaging;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.firebase.FirebaseException;
import com.google.firebase.internal.NonNull;

public class FirebaseMessagingException extends FirebaseException {

  private final String errorCode;

  FirebaseMessagingException(String errorCode, String message, Throwable cause) {
    super(message, cause);
    checkArgument(!Strings.isNullOrEmpty(errorCode));
    this.errorCode = errorCode;
  }


  /** Returns an error code that may provide more information about the error. */
  @NonNull
  public String getErrorCode() {
    return errorCode;
  }
}
