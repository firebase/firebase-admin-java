/*
 * Copyright 2019 Google Inc.
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

package com.google.firebase.database;

public enum DatabaseErrorCode {

  DATA_STALE,
  /** The server indicated that this operation failed. */
  OPERATION_FAILED,
  /** This client does not have permission to perform this operation. */
  PERMISSION_DENIED,
  /** The operation had to be aborted due to a network disconnect. */
  DISCONNECTED,

  // Preempted was removed, this is for here for completeness and history
  // public static final int PREEMPTED = -5;

  /** The supplied auth token has expired. */
  EXPIRED_TOKEN,
  /**
   * The specified authentication token is invalid. This can occur when the token is malformed,
   * expired, or the secret that was used to generate it has been revoked.
   */
  INVALID_TOKEN,
  /** The transaction had too many retries */
  MAX_RETRIES,
  /** The transaction was overridden by a subsequent set */
  OVERRIDDEN_BY_SET,
  /** The service is unavailable. */
  UNAVAILABLE,
  /** An exception occurred in user code. */
  USER_CODE_EXCEPTION,

  // client codes
  /** The operation could not be performed due to a network error. */
  NETWORK_ERROR,

  /** The write was canceled locally. */
  WRITE_CANCELED,
  UNKNOWN_ERROR,

}
