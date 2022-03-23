/*
 * Copyright 2022 Google LLC
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

/**
 * Error codes that can be raised by the App Check APIs.
 */
public enum AppCheckErrorCode {

  /**
   * One or more arguments specified in the request were invalid.
   */
  INVALID_ARGUMENT,

  /**
   * Internal server error.
   */
  INTERNAL,

  /**
   * User is not authenticated.
   */
  UNAUTHENTICATED,
}
