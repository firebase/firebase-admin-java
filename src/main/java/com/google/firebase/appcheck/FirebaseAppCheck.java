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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.core.ApiFuture;
import com.google.common.annotations.VisibleForTesting;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.internal.CallableOperation;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.NonNull;

/**
 * This class is the entry point for all server-side Firebase App Check actions.
 *
 * <p>You can get an instance of {@link FirebaseAppCheck} via {@link #getInstance(FirebaseApp)},
 * and then use it to access App Check services.
 */
public final class FirebaseAppCheck {

  private static final String SERVICE_ID = FirebaseAppCheck.class.getName();
  private final FirebaseApp app;
  private final FirebaseAppCheckClient appCheckClient;

  @VisibleForTesting
  FirebaseAppCheck(FirebaseApp app, FirebaseAppCheckClient client) {
    this.app = checkNotNull(app);
    this.appCheckClient = checkNotNull(client);
  }

  private FirebaseAppCheck(FirebaseApp app) {
    this(app, FirebaseAppCheckClientImpl.fromApp(app));
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
   * @return The {@link FirebaseAppCheck} instance for the specified {@link FirebaseApp}.
   */
  public static synchronized FirebaseAppCheck getInstance(FirebaseApp app) {
    FirebaseAppCheck.FirebaseAppCheckService service = ImplFirebaseTrampolines.getService(app,
            SERVICE_ID,
            FirebaseAppCheck.FirebaseAppCheckService.class);
    if (service == null) {
      service = ImplFirebaseTrampolines.addService(app,
              new FirebaseAppCheck.FirebaseAppCheckService(app));
    }
    return service.getInstance();
  }

  /**
   * Verifies a given App Check Token.
   *
   * @param token The App Check token to be verified.
   * @return A {@link VerifyAppCheckTokenResponse}.
   * @throws FirebaseAppCheckException If an error occurs while getting the template.
   */
  public VerifyAppCheckTokenResponse verifyToken(
          @NonNull String token) throws FirebaseAppCheckException {
    return verifyTokenOp(token).call();
  }

  /**
   * Similar to {@link #verifyToken(String token)} but performs the operation
   * asynchronously.
   *
   * @param token The App Check token to be verified.
   * @return An {@code ApiFuture} that completes with a {@link VerifyAppCheckTokenResponse} when
   *     the provided token is valid.
   */
  public ApiFuture<VerifyAppCheckTokenResponse> verifyTokenAsync(@NonNull String token)
          throws FirebaseAppCheckException {
    return verifyTokenOp(token).callAsync(app);
  }

  private CallableOperation<VerifyAppCheckTokenResponse, FirebaseAppCheckException> verifyTokenOp(
          final String token) {
    final FirebaseAppCheckClient appCheckClient = getAppCheckClient();
    return new CallableOperation<VerifyAppCheckTokenResponse, FirebaseAppCheckException>() {
      @Override
      protected VerifyAppCheckTokenResponse execute() throws FirebaseAppCheckException {
        return appCheckClient.verifyToken(token);
      }
    };
  }

  @VisibleForTesting
  FirebaseAppCheckClient getAppCheckClient() {
    return appCheckClient;
  }

  private static class FirebaseAppCheckService extends FirebaseService<FirebaseAppCheck> {
    FirebaseAppCheckService(FirebaseApp app) {
      super(SERVICE_ID, new FirebaseAppCheck(app));
    }
  }
}
