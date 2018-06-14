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

package com.google.firebase.auth.internal;

import com.google.firebase.internal.NonNull;
import java.io.IOException;

/**
 * Represents an  object that can be used to cryptographically sign data. Mainly used for signing
 * custom JWT tokens issued to Firebase users.
 *
 * <p>See {@link com.google.firebase.auth.FirebaseAuth#createCustomToken(String)}.
 */
interface CryptoSigner {

  /**
   * Signs the given payload.
   *
   * @param payload Data to be signed
   * @return Signature as a byte array
   * @throws IOException If an error occurs during signing
   */
  @NonNull
  byte[] sign(@NonNull byte[] payload) throws IOException;

  /**
   * Returns the client email of the service account used to sign payloads.
   *
   * @return A service account client email
   */
  @NonNull
  String getAccount();
}
