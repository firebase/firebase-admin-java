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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.api.client.http.HttpResponseException;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseException;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import com.google.firebase.messaging.internal.MessagingServiceErrorResponse;
import java.util.Map;

public class FirebaseMessagingException extends FirebaseException {

  private static final String INTERNAL_ERROR = "internal-error";
  private static final String UNKNOWN_ERROR = "unknown-error";
  private static final Map<String, String> FCM_ERROR_CODES =
      ImmutableMap.<String, String>builder()
        // FCM v1 canonical error codes
        .put("NOT_FOUND", "registration-token-not-registered")
        .put("PERMISSION_DENIED", "mismatched-credential")
        .put("RESOURCE_EXHAUSTED", "message-rate-exceeded")
        .put("UNAUTHENTICATED", "invalid-apns-credentials")

        // FCM v1 new error codes
        .put("APNS_AUTH_ERROR", "invalid-apns-credentials")
        .put("INTERNAL", INTERNAL_ERROR)
        .put("INVALID_ARGUMENT", "invalid-argument")
        .put("QUOTA_EXCEEDED", "message-rate-exceeded")
        .put("SENDER_ID_MISMATCH", "mismatched-credential")
        .put("UNAVAILABLE", "server-unavailable")
        .put("UNREGISTERED", "registration-token-not-registered")
        .build();

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

  static FirebaseMessagingException fromErrorResponse(MessagingServiceErrorResponse response) {
    return fromErrorResponse(response, null);
  }

  static FirebaseMessagingException fromErrorResponse(
      MessagingServiceErrorResponse response, @Nullable HttpResponseException e) {
    String code = FCM_ERROR_CODES.get(response.getErrorCode());
    if (code == null) {
      code = UNKNOWN_ERROR;
    }
    String msg = response.getErrorMessage();
    if (Strings.isNullOrEmpty(msg)) {
      if (e != null) {
        msg = String.format("Unexpected HTTP response with status: %d; body: %s",
            e.getStatusCode(), e.getContent());
      } else {
        msg = String.format("Unexpected HTTP response: %s", response.toString());
      }
    }
    return new FirebaseMessagingException(code, msg, e);
  }
}
