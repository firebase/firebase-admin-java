/*
 * Copyright 2020 Google LLC
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

package com.google.firebase.remoteconfig;

import com.google.common.annotations.VisibleForTesting;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;

/**
 * Generic exception related to Firebase Remote Config. Check the error code and message for more
 * details.
 */
public final class FirebaseRemoteConfigException extends FirebaseException {

  private final RemoteConfigErrorCode errorCode;

  @VisibleForTesting
  FirebaseRemoteConfigException(@NonNull ErrorCode code, @NonNull String message) {
    this(code, message, null, null, null);
  }

  public FirebaseRemoteConfigException(
          @NonNull ErrorCode errorCode,
          @NonNull String message,
          @Nullable Throwable cause,
          @Nullable IncomingHttpResponse response,
          @Nullable RemoteConfigErrorCode remoteConfigErrorCode) {
    super(errorCode, message, cause, response);
    this.errorCode = remoteConfigErrorCode;
  }

  static FirebaseRemoteConfigException withRemoteConfigErrorCode(
          FirebaseException base, @Nullable RemoteConfigErrorCode errorCode) {
    return new FirebaseRemoteConfigException(
            base.getErrorCode(),
            base.getMessage(),
            base.getCause(),
            base.getHttpResponse(),
            errorCode);
  }

  @Nullable
  public RemoteConfigErrorCode getRemoteConfigErrorCode() {
    return errorCode;
  }
}
