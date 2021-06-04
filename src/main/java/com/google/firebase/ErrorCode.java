/*
 * Copyright 2020 Google Inc.
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

/**
 * Platform-wide error codes that can be raised by Admin SDK APIs.
 */
public enum ErrorCode {

  /**
   * Client specified an invalid argument.
   */
  INVALID_ARGUMENT,

  /**
   * Request cannot be executed in the current system state, such as deleting a non-empty
   * directory.
   */
  FAILED_PRECONDITION,

  /**
   * Client specified an invalid range.
   */
  OUT_OF_RANGE,

  /**
   * Request not authenticated due to missing, invalid, or expired OAuth token.
   */
  UNAUTHENTICATED,

  /**
   * Client does not have sufficient permission. This can happen because the OAuth token does
   * not have the right scopes, the client doesn't have permission, or the API has not been
   * enabled for the client project.
   */
  PERMISSION_DENIED,

  /**
   * A specified resource is not found, or the request is rejected for unknown reasons,
   * such as a blocked network address.
   */
  NOT_FOUND,

  /**
   * Concurrency conflict, such as read-modify-write conflict.
   */
  CONFLICT,

  /**
   * Concurrency conflict, such as read-modify-write conflict.
   */
  ABORTED,

  /**
   * The resource that a client tried to create already exists.
   */
  ALREADY_EXISTS,

  /**
   * Either out of resource quota or rate limited.
   */
  RESOURCE_EXHAUSTED,

  /**
   * Request cancelled by the client.
   */
  CANCELLED,

  /**
   * Unrecoverable data loss or data corruption. The client should report the error to the user.
   */
  DATA_LOSS,

  /**
   * Unknown server error. Typically a server bug.
   */
  UNKNOWN,

  /**
   * Internal server error. Typically a server bug.
   */
  INTERNAL,

  /**
   * Service unavailable. Typically the server is down.
   */
  UNAVAILABLE,

  /**
   * Request deadline exceeded. This happens only if the caller sets a deadline that is
   * shorter than the method's default deadline (i.e. requested deadline is not enough for the
   * server to process the request) and the request did not finish within the deadline.
   */
  DEADLINE_EXCEEDED,
}
