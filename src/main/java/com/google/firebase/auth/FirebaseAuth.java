/*
 * Copyright 2017 Google LLC
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.util.Clock;
import com.google.api.core.ApiFuture;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.auth.AbstractFirebaseAuth.Builder;
import com.google.firebase.auth.internal.FirebaseTokenFactory;
import com.google.firebase.internal.CallableOperation;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.NonNull;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class is the entry point for all server-side Firebase Authentication actions.
 *
 * <p>You can get an instance of FirebaseAuth via {@link FirebaseAuth#getInstance(FirebaseApp)} and
 * then use it to perform a variety of authentication-related operations, including generating
 * custom tokens for use by client-side code, verifying Firebase ID Tokens received from clients, or
 * creating new FirebaseApp instances that are scoped to a particular authentication UID.
 */
public class FirebaseAuth extends AbstractFirebaseAuth {

  private static final String SERVICE_ID = FirebaseAuth.class.getName();

  private final Supplier<TenantManager> tenantManager;
  private final AtomicBoolean tenantManagerCreated = new AtomicBoolean(false);

  FirebaseAuth(final Builder builder) {
    super(builder);
    tenantManager = threadSafeMemoize(new Supplier<TenantManager>() {
      @Override
      public TenantManager get() {
        tenantManagerCreated.set(true);
        return new TenantManager(builder.firebaseApp, getUserManager());
      }
    });
  }

  public TenantManager getTenantManager() {
    return tenantManager.get();
  }

  /**
   * Gets the FirebaseAuth instance for the default {@link FirebaseApp}.
   *
   * @return The FirebaseAuth instance for the default {@link FirebaseApp}.
   */
  public static FirebaseAuth getInstance() {
    return FirebaseAuth.getInstance(FirebaseApp.getInstance());
  }

  /**
   * Gets an instance of FirebaseAuth for a specific {@link FirebaseApp}.
   *
   * @param app The {@link FirebaseApp} to get a FirebaseAuth instance for.
   * @return A FirebaseAuth instance.
   */
  public static synchronized FirebaseAuth getInstance(FirebaseApp app) {
    FirebaseAuthService service =
        ImplFirebaseTrampolines.getService(app, SERVICE_ID, FirebaseAuthService.class);
    if (service == null) {
      service = ImplFirebaseTrampolines.addService(app, new FirebaseAuthService(app));
    }
    return service.getInstance();
  }

  /**
   * Creates a new Firebase session cookie from the given ID token and options. The returned JWT can
   * be set as a server-side session cookie with a custom cookie policy.
   *
   * @param idToken The Firebase ID token to exchange for a session cookie.
   * @param options Additional options required to create the cookie.
   * @return A Firebase session cookie string.
   * @throws IllegalArgumentException If the ID token is null or empty, or if options is null.
   * @throws FirebaseAuthException If an error occurs while generating the session cookie.
   */
  public String createSessionCookie(@NonNull String idToken, @NonNull SessionCookieOptions options)
      throws FirebaseAuthException {
    return createSessionCookieOp(idToken, options).call();
  }

  /**
   * Similar to {@link #createSessionCookie(String, SessionCookieOptions)} but performs the
   * operation asynchronously.
   *
   * @param idToken The Firebase ID token to exchange for a session cookie.
   * @param options Additional options required to create the cookie.
   * @return An {@code ApiFuture} which will complete successfully with a session cookie string. If
   *     an error occurs while generating the cookie or if the specified ID token is invalid, the
   *     future throws a {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the ID token is null or empty, or if options is null.
   */
  public ApiFuture<String> createSessionCookieAsync(
      @NonNull String idToken, @NonNull SessionCookieOptions options) {
    return createSessionCookieOp(idToken, options).callAsync(getFirebaseApp());
  }

  private CallableOperation<String, FirebaseAuthException> createSessionCookieOp(
      final String idToken, final SessionCookieOptions options) {
    checkNotDestroyed();
    checkArgument(!Strings.isNullOrEmpty(idToken), "idToken must not be null or empty");
    checkNotNull(options, "options must not be null");
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<String, FirebaseAuthException>() {
      @Override
      protected String execute() throws FirebaseAuthException {
        return userManager.createSessionCookie(idToken, options);
      }
    };
  }

  /**
   * Parses and verifies a Firebase session cookie.
   *
   * <p>If verified successfully, returns a parsed version of the cookie from which the UID and the
   * other claims can be read. If the cookie is invalid, throws a {@link FirebaseAuthException}.
   *
   * <p>This method does not check whether the cookie has been revoked. See {@link
   * #verifySessionCookie(String, boolean)}.
   *
   * @param cookie A Firebase session cookie string to verify and parse.
   * @return A {@link FirebaseToken} representing the verified and decoded cookie.
   */
  public FirebaseToken verifySessionCookie(String cookie) throws FirebaseAuthException {
    return verifySessionCookie(cookie, false);
  }

  /**
   * Parses and verifies a Firebase session cookie.
   *
   * <p>If {@code checkRevoked} is true, additionally verifies that the cookie has not been revoked.
   *
   * <p>If verified successfully, returns a parsed version of the cookie from which the UID and the
   * other claims can be read. If the cookie is invalid or has been revoked while {@code
   * checkRevoked} is true, throws a {@link FirebaseAuthException}.
   *
   * @param cookie A Firebase session cookie string to verify and parse.
   * @param checkRevoked A boolean indicating whether to check if the cookie was explicitly revoked.
   * @return A {@link FirebaseToken} representing the verified and decoded cookie.
   */
  public FirebaseToken verifySessionCookie(String cookie, boolean checkRevoked)
      throws FirebaseAuthException {
    return verifySessionCookieOp(cookie, checkRevoked).call();
  }

  /**
   * Similar to {@link #verifySessionCookie(String)} but performs the operation asynchronously.
   *
   * @param cookie A Firebase session cookie string to verify and parse.
   * @return An {@code ApiFuture} which will complete successfully with the parsed cookie, or
   *     unsuccessfully with the failure Exception.
   */
  public ApiFuture<FirebaseToken> verifySessionCookieAsync(String cookie) {
    return verifySessionCookieAsync(cookie, false);
  }

  /**
   * Similar to {@link #verifySessionCookie(String, boolean)} but performs the operation
   * asynchronously.
   *
   * @param cookie A Firebase session cookie string to verify and parse.
   * @param checkRevoked A boolean indicating whether to check if the cookie was explicitly revoked.
   * @return An {@code ApiFuture} which will complete successfully with the parsed cookie, or
   *     unsuccessfully with the failure Exception.
   */
  public ApiFuture<FirebaseToken> verifySessionCookieAsync(String cookie, boolean checkRevoked) {
    return verifySessionCookieOp(cookie, checkRevoked).callAsync(getFirebaseApp());
  }

  private CallableOperation<FirebaseToken, FirebaseAuthException> verifySessionCookieOp(
      final String cookie, final boolean checkRevoked) {
    checkNotDestroyed();
    checkArgument(!Strings.isNullOrEmpty(cookie), "Session cookie must not be null or empty");
    final FirebaseTokenVerifier sessionCookieVerifier = getSessionCookieVerifier(checkRevoked);
    return new CallableOperation<FirebaseToken, FirebaseAuthException>() {
      @Override
      public FirebaseToken execute() throws FirebaseAuthException {
        return sessionCookieVerifier.verifyToken(cookie);
      }
    };
  }

  @VisibleForTesting
  FirebaseTokenVerifier getSessionCookieVerifier(boolean checkRevoked) {
    FirebaseTokenVerifier verifier = getCookieVerifier();
    if (checkRevoked) {
      FirebaseUserManager userManager = getUserManager();
      verifier = RevocationCheckDecorator.decorateSessionCookieVerifier(verifier, userManager);
    }
    return verifier;
  }

  @Override
  protected void doDestroy() {
    // Only destroy the tenant manager if it has been created.
    if (tenantManagerCreated.get()) {
      getTenantManager().destroy();
    }
  }

  private static FirebaseAuth fromApp(final FirebaseApp app) {
    return new FirebaseAuth(
        AbstractFirebaseAuth.builder()
          .setFirebaseApp(app)
          .setTokenFactory(
              new Supplier<FirebaseTokenFactory>() {
                @Override
                public FirebaseTokenFactory get() {
                  return FirebaseTokenUtils.createTokenFactory(app, Clock.SYSTEM);
                }
              })
          .setIdTokenVerifier(
              new Supplier<FirebaseTokenVerifier>() {
                @Override
                public FirebaseTokenVerifier get() {
                  return FirebaseTokenUtils.createIdTokenVerifier(app, Clock.SYSTEM);
                }
              })
          .setCookieVerifier(
              new Supplier<FirebaseTokenVerifier>() {
                @Override
                public FirebaseTokenVerifier get() {
                  return FirebaseTokenUtils.createSessionCookieVerifier(app, Clock.SYSTEM);
                }
            })
          .setUserManager(
              new Supplier<FirebaseUserManager>() {
                @Override
                public FirebaseUserManager get() {
                  return FirebaseUserManager.builder().setFirebaseApp(app).build();
                }
              }));
  }

  private static class FirebaseAuthService extends FirebaseService<FirebaseAuth> {

    FirebaseAuthService(FirebaseApp app) {
      super(SERVICE_ID, FirebaseAuth.fromApp(app));
    }

    @Override
    public void destroy() {
      instance.destroy();
    }
  }
}
