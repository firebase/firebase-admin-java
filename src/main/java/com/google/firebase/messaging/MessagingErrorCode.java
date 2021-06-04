  <<<<<<< hkj-error-handling
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

package com.google.firebase.messaging;

public enum MessagingErrorCode {

  THIRD_PARTY_AUTH_ERROR,

  INVALID_ARGUMENT,

  INTERNAL,

  QUOTA_EXCEEDED,

  SENDER_ID_MISMATCH,

  UNAVAILABLE,

  =======
package com.google.firebase.messaging;

/**
 * Error codes that can be raised by the Cloud Messaging APIs.
 */
public enum MessagingErrorCode {

  /**
   * APNs certificate or web push auth key was invalid or missing.
   */
  THIRD_PARTY_AUTH_ERROR,

  /**
   * One or more arguments specified in the request were invalid.
   */
  INVALID_ARGUMENT,

  /**
   * Internal server error.
   */
  INTERNAL,

  /**
   * Sending limit exceeded for the message target.
   */
  QUOTA_EXCEEDED,

  /**
   * The authenticated sender ID is different from the sender ID for the registration token.
   */
  SENDER_ID_MISMATCH,

  /**
   * Cloud Messaging service is temporarily unavailable.
   */
  UNAVAILABLE,

  /**
   * App instance was unregistered from FCM. This usually means that the token used is no longer
   * valid and a new one must be used.
   */
  >>>>>>> master
  UNREGISTERED,
}
