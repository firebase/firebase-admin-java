/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.messaging;

import com.google.common.annotations.VisibleForTesting;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;

public final class FirebaseMessagingException extends FirebaseException {

  private final MessagingErrorCode errorCode;

  @VisibleForTesting
  FirebaseMessagingException(@NonNull ErrorCode code, @NonNull String message) {
    this(code, message, null, null, null);
  }

  private FirebaseMessagingException(
      @NonNull ErrorCode code,
      @NonNull String message,
      @Nullable Throwable cause,
      @Nullable IncomingHttpResponse response,
      @Nullable MessagingErrorCode errorCode) {
    super(code, message, cause, response);
    this.errorCode = errorCode;
  }

  static FirebaseMessagingException withMessagingErrorCode(
      FirebaseException base, @Nullable MessagingErrorCode errorCode) {
    return new FirebaseMessagingException(
        base.getErrorCode(),
        base.getMessage(),
        base.getCause(),
        base.getHttpResponse(),
        errorCode);
  }

  static FirebaseMessagingException withCustomMessage(FirebaseException base, String message) {
    return new FirebaseMessagingException(
        base.getErrorCode(),
        message,
        base.getCause(),
        base.getHttpResponse(),
        null);
  }

  /** Returns an error code that may provide more information about the error. */
  @Nullable
  public MessagingErrorCode getMessagingErrorCode() {
    return errorCode;
  }
}
