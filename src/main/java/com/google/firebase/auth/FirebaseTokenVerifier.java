/*
 * Copyright  2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
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
 * An interface for verifying Firebase token strings.  Exists mainly to facilitate easy testing
 * and extension/decoration of the token verification functionality.
 */
interface FirebaseTokenVerifier {

  /**
   * Verifies that the given token string is a valid Firebase JWT.
   *
   * @param token The token string to be verified.
   * @return A decoded representation of the input token string.
   * @throws FirebaseAuthException If the input token string fails to verify due to any reason.
   */
  FirebaseToken verifyToken(String token) throws FirebaseAuthException;

}
