/*
 * Copyright 2026 Google LLC
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.core.ApiFuture;
import com.google.common.annotations.VisibleForTesting;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.appcheck.internal.AppCheckTokenVerifier;
import com.google.firebase.internal.CallableOperation;
import com.google.firebase.internal.FirebaseService;

/**
 * This class is the entry point for the Firebase App Check service.
 *
 * <p>You can get an instance of {@link FirebaseAppCheck} via {@link #getInstance()}
 * or {@link #getInstance(FirebaseApp)}.
 */
public final class FirebaseAppCheck {

  private static final String SERVICE_ID = FirebaseAppCheck.class.getName();

  private final FirebaseApp app;
  private final AppCheckTokenVerifier tokenVerifier;

  private FirebaseAppCheck(FirebaseApp app) {
    this(app, new AppCheckTokenVerifier(app));
  }

  @VisibleForTesting
  FirebaseAppCheck(FirebaseApp app, AppCheckTokenVerifier tokenVerifier) {
    this.app = checkNotNull(app, "FirebaseApp must not be null");
    this.tokenVerifier = checkNotNull(tokenVerifier, "AppCheckTokenVerifier must not be null");
  }

  /**
   * Gets the {@link FirebaseAppCheck} instance for the default {@link FirebaseApp}.
   *
   * @return The {@link FirebaseAppCheck} instance for the default {@link FirebaseApp}.
   */
  public static FirebaseAppCheck getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }

  /**
   * Gets the {@link FirebaseAppCheck} instance for the specified {@link FirebaseApp}.
   *
   * @param app The {@link FirebaseApp} instance.
   * @return The {@link FirebaseAppCheck} instance for the specified {@link FirebaseApp}.
   */
  public static synchronized FirebaseAppCheck getInstance(FirebaseApp app) {
    FirebaseAppCheckService service =
        ImplFirebaseTrampolines.getService(app, SERVICE_ID, FirebaseAppCheckService.class);
    if (service == null) {
      service = ImplFirebaseTrampolines.addService(app, new FirebaseAppCheckService(app));
    }
    return service.getInstance();
  }

  /**
   * Verifies an App Check token string.
   *
   * @param appCheckToken The App Check token string to verify.
   * @return A {@link VerifyAppCheckTokenResponse} containing the decoded token.
   * @throws FirebaseAppCheckException If verification fails.
   */
  public VerifyAppCheckTokenResponse verifyToken(String appCheckToken)
      throws FirebaseAppCheckException {
    return verifyToken(appCheckToken, null);
  }

  /**
   * Verifies an App Check token string with options.
   *
   * @param appCheckToken The App Check token string to verify.
   * @param options Verification options specified via {@link VerifyAppCheckTokenOptions}.
   * @return A {@link VerifyAppCheckTokenResponse} containing the decoded token
   *     and consumption status.
   * @throws FirebaseAppCheckException If verification fails.
   */
  public VerifyAppCheckTokenResponse verifyToken(
      String appCheckToken, VerifyAppCheckTokenOptions options) throws FirebaseAppCheckException {
    return this.tokenVerifier.verifyToken(appCheckToken, options);
  }

  /**
   * Asynchronously verifies an App Check token string.
   *
   * @param appCheckToken The App Check token string to verify.
   * @return An {@link ApiFuture} containing the {@link VerifyAppCheckTokenResponse}.
   */
  public ApiFuture<VerifyAppCheckTokenResponse> verifyTokenAsync(String appCheckToken) {
    return verifyTokenAsync(appCheckToken, null);
  }

  /**
   * Asynchronously verifies an App Check token string with options.
   *
   * @param appCheckToken The App Check token string to verify.
   * @param options Verification options specified via {@link VerifyAppCheckTokenOptions}.
   * @return An {@link ApiFuture} containing the {@link VerifyAppCheckTokenResponse}.
   */
  public ApiFuture<VerifyAppCheckTokenResponse> verifyTokenAsync(
      String appCheckToken, VerifyAppCheckTokenOptions options) {
    return verifyTokenOp(appCheckToken, options).callAsync(this.app);
  }

  private CallableOperation<VerifyAppCheckTokenResponse, FirebaseAppCheckException> verifyTokenOp(
      final String appCheckToken, final VerifyAppCheckTokenOptions options) {
    return new CallableOperation<VerifyAppCheckTokenResponse, FirebaseAppCheckException>() {
      @Override
      protected VerifyAppCheckTokenResponse execute() throws FirebaseAppCheckException {
        return verifyToken(appCheckToken, options);
      }
    };
  }

  private static class FirebaseAppCheckService extends FirebaseService<FirebaseAppCheck> {
    FirebaseAppCheckService(FirebaseApp app) {
      super(SERVICE_ID, new FirebaseAppCheck(app));
    }
  }
}
