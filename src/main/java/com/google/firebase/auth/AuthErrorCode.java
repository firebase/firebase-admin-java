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

package com.google.firebase.auth;

/**
 * Error codes that can be raised by the Firebase Auth APIs.
 */
public enum AuthErrorCode {

  /**
   * A user already exists with the provided email.
   */
  EMAIL_ALREADY_EXISTS,

  /**
   * The provided dynamic link domain is not configured or authorized for the current project.
   */
  INVALID_DYNAMIC_LINK_DOMAIN,

  /**
   * A user already exists with the provided phone number.
   */
  PHONE_NUMBER_ALREADY_EXISTS,

  /**
   * A user already exists with the provided UID.
   */
  UID_ALREADY_EXISTS,

  /**
   * The domain of the continue URL is not whitelisted. Whitelist the domain in the Firebase
   * console.
   */
  UNAUTHORIZED_CONTINUE_URL,

  /**
   * No user record found for the given identifier.
   */
  USER_NOT_FOUND,
}
