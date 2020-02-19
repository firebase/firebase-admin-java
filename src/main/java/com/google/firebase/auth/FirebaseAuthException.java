/*
 * Copyright 2017 Google Inc.
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

package com.google.firebase.auth;

import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;

/**
 * Generic exception related to Firebase Authentication. Check the error code and message for more
 * details.
 */
public class FirebaseAuthException extends FirebaseException {

  private final AuthErrorCode errorCode;

  public FirebaseAuthException(
      @NonNull ErrorCode errorCode,
      @NonNull String message,
      Throwable cause,
      IncomingHttpResponse response,
      AuthErrorCode authErrorCode) {
    super(errorCode, message, cause, response);
    this.errorCode = authErrorCode;
  }

  public FirebaseAuthException(FirebaseException base) {
    this(base.getErrorCode(), base.getMessage(), base.getCause(), base.getHttpResponse(), null);
  }

  @Nullable
  public AuthErrorCode getAuthErrorCode() {
    return errorCode;
  }
}
