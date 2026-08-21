/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.appcheck;

import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;

/**
 * Generic exception related to Firebase App Check. Check the error code and message for more
 * details.
 */
public class FirebaseAppCheckException extends FirebaseException {

  public FirebaseAppCheckException(
      @NonNull ErrorCode errorCode,
      @NonNull String message,
      @Nullable Throwable cause,
      @Nullable IncomingHttpResponse response) {
    super(errorCode, message, cause, response);
  }

  public FirebaseAppCheckException(
      @NonNull ErrorCode errorCode,
      @NonNull String message,
      @Nullable Throwable cause) {
    this(errorCode, message, cause, null);
  }

  public FirebaseAppCheckException(
      @NonNull ErrorCode errorCode,
      @NonNull String message) {
    this(errorCode, message, null, null);
  }

  public FirebaseAppCheckException(@NonNull FirebaseException base) {
    this(base.getErrorCode(), base.getMessage(), base.getCause(), base.getHttpResponse());
  }
}
