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
 * An interface for managing Firebase App Check services.
 */
interface FirebaseAppCheckClient {

  /**
   * Gets the current active version of the App Check template.
   *
   * @return A {@link VerifyAppCheckTokenResponse}.
   * @throws FirebaseAppCheckException If an error occurs while getting the template.
   */
  VerifyAppCheckTokenResponse verifyToken(String token) throws FirebaseAppCheckException;
}
