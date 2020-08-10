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

package com.google.firebase.auth;

import com.google.common.collect.ImmutableMap;
import java.util.concurrent.atomic.AtomicReference;

public class MockTokenVerifier implements FirebaseTokenVerifier {

  private final FirebaseToken result;
  private final FirebaseAuthException exception;
  private final AtomicReference<String> lastTokenString = new AtomicReference<>();

  private MockTokenVerifier(FirebaseToken result, FirebaseAuthException exception) {
    this.result = result;
    this.exception = exception;
  }

  @Override
  public FirebaseToken verifyToken(String token) throws FirebaseAuthException {
    lastTokenString.set(token);
    if (exception != null) {
      throw exception;
    }
    return result;
  }

  public String getLastTokenString() {
    return this.lastTokenString.get();
  }

  public static MockTokenVerifier fromUid(String uid) {
    long iat = System.currentTimeMillis() / 1000;
    return fromResult(new FirebaseToken(ImmutableMap.<String, Object>of(
        "sub", uid, "iat", iat)));
  }

  public static MockTokenVerifier fromResult(FirebaseToken result) {
    return new MockTokenVerifier(result, null);
  }

  public static MockTokenVerifier fromException(FirebaseAuthException exception) {
    return new MockTokenVerifier(null, exception);
  }
}
