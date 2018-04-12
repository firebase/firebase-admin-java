/*
 * Copyright 2017 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.util.Clock;
import com.google.common.annotations.VisibleForTesting;
import com.google.firebase.FirebaseApp;
import com.google.firebase.internal.NonNull;

/**
 * A utility for initializing and keeping tack of the various public key manager instances
 * used by {@link com.google.firebase.auth.FirebaseAuth}.
 */
public class KeyManagers {

  private final GooglePublicKeysManager idTokenKeysManager;
  private final GooglePublicKeysManager sessionCookieKeysManager;

  private KeyManagers(Builder builder) {
    this.idTokenKeysManager = checkNotNull(builder.idTokenKeysManager);
    this.sessionCookieKeysManager = checkNotNull(builder.sessionCookieKeysManager);
  }

  /**
   * Returns the key manager that should be used for ID token verification.
   */
  GooglePublicKeysManager getIdTokenKeysManager() {
    return idTokenKeysManager;
  }

  /**
   * Returns the key manager that should be used for session cookie verification.
   */
  GooglePublicKeysManager getSessionCookieKeysManager() {
    return sessionCookieKeysManager;
  }

  /**
   * Initialize a new set of key managers for the specified app using the given clock.
   *
   * @param app A {@link FirebaseApp} instance.
   * @param clock A {@code Clock} to be used with Google API client.
   * @return A new {@link KeyManagers} instance.
   */
  public static KeyManagers getDefault(@NonNull FirebaseApp app, @NonNull Clock clock) {
    HttpTransport transport = app.getOptions().getHttpTransport();
    return getDefault(transport, clock);
  }

  @VisibleForTesting
  static KeyManagers getDefault(HttpTransport transport, Clock clock) {
    return new Builder()
        .setIdTokenKeysManager(FirebaseTokenVerifier.createKeysManager(
            transport, clock, FirebaseTokenVerifier.ID_TOKEN_CERT_URL))
        .setSessionCookieKeysManager(FirebaseTokenVerifier.createKeysManager(
            transport, clock, FirebaseTokenVerifier.SESSION_COOKIE_CERT_URL))
        .build();
  }

  public static class Builder {

    private GooglePublicKeysManager idTokenKeysManager;
    private GooglePublicKeysManager sessionCookieKeysManager;

    public Builder setIdTokenKeysManager(
        GooglePublicKeysManager idTokenKeysManager) {
      this.idTokenKeysManager = idTokenKeysManager;
      return this;
    }

    public Builder setSessionCookieKeysManager(
        GooglePublicKeysManager sessionCookieKeysManager) {
      this.sessionCookieKeysManager = sessionCookieKeysManager;
      return this;
    }

    public KeyManagers build() {
      return new KeyManagers(this);
    }
  }
}
