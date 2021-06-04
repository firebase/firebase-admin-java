/* Copyright 2018 Google Inc.
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

package com.google.firebase.projectmanagement;

import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.database.annotations.Nullable;
import com.google.firebase.internal.NonNull;

/**
 * An exception encountered while interacting with the Firebase Project Management Service.
 */
public final class FirebaseProjectManagementException extends FirebaseException {

  FirebaseProjectManagementException(@NonNull FirebaseException base) {
    this(base, base.getMessage());
  }

  FirebaseProjectManagementException(@NonNull FirebaseException base, @NonNull String message) {
    super(base.getErrorCode(), message, base.getCause(), base.getHttpResponse());
  }

  FirebaseProjectManagementException(
      @NonNull ErrorCode code, @NonNull String message, @Nullable IncomingHttpResponse response) {
    super(code, message, null, response);
  }
}
