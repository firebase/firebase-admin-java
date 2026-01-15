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

package com.google.firebase.fpnv;

/**
 * Generic exception related to Firebase Phone Number Verification.
 * Check the error code and message for more
 * details.
 */
public class FirebasePnvException extends Exception {
  private final FirebasePnvErrorCode errorCode;

  /**
   * Exception that created from {@link FirebasePnvErrorCode},
   * {@link String} message and {@link Throwable} cause.
   *
   * @param errorCode {@link FirebasePnvErrorCode}
   * @param message {@link String}
   * @param cause {@link Throwable}
   */
  public FirebasePnvException(
      FirebasePnvErrorCode errorCode,
      String message,
      Throwable cause
  ) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  /**
   * Exception that created from {@link FirebasePnvErrorCode} and {@link String} message.
   *
   * @param errorCode {@link FirebasePnvErrorCode}
   * @param message {@link String}
   */
  public FirebasePnvException(
      FirebasePnvErrorCode errorCode,
      String message
  ) {
    this(errorCode, message, null);
  }

  public FirebasePnvErrorCode getFpnvErrorCode() {
    return errorCode;
  }
}
