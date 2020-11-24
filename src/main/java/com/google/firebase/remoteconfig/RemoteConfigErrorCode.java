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

/**
 * Error codes that can be raised by the Remote Config APIs.
 */
public enum RemoteConfigErrorCode {

  /**
   * One or more arguments specified in the request were invalid.
   */
  INVALID_ARGUMENT,

  /**
   * Internal server error.
   */
  INTERNAL,

  /**
   * Request cannot be executed in the current system state, such as deleting a non-empty
   * directory.
   */
  FAILED_PRECONDITION,

  /**
   * User is not authenticated.
   */
  UNAUTHENTICATED,

  /**
   * The resource that a client tried to create already exists.
   */
  ALREADY_EXISTS,

  /**
   * Failed to validate Remote Config data.
   */
  VALIDATION_ERROR,

  /**
   * The current version specified in an update request
   * did not match the actual version in the database.
   */
  VERSION_MISMATCH,
}
