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

package com.google.firebase;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;

/** Base class for all Firebase exceptions. */
public class FirebaseException extends Exception {

  private final ErrorCode errorCode;
  private final FirebaseHttpResponse response;

  public FirebaseException(@NonNull String detailMessage) {
    this(detailMessage, null);
  }

  public FirebaseException(@NonNull String detailMessage, Throwable cause) {
    this(ErrorCode.UNKNOWN, detailMessage, null, cause);
  }

  public FirebaseException(
      @NonNull ErrorCode errorCode,
      @NonNull String message,
      @Nullable FirebaseHttpResponse response,
      @Nullable Throwable cause) {
    super(message, cause);
    checkArgument(!Strings.isNullOrEmpty(message), "Message must not be null or empty");
    this.errorCode = checkNotNull(errorCode);
    this.response = response;
  }

  public ErrorCode getPlatformErrorCode() {
    return errorCode;
  }

  @Nullable public FirebaseHttpResponse getHttpResponse() {
    return response;
  }
}
