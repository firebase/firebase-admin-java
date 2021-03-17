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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.Clock;
import com.google.api.core.ApiFuture;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseUserManager.EmailLinkType;
import com.google.firebase.auth.FirebaseUserManager.UserImportRequest;
import com.google.firebase.auth.ListProviderConfigsPage.DefaultOidcProviderConfigSource;
import com.google.firebase.auth.ListProviderConfigsPage.DefaultSamlProviderConfigSource;
import com.google.firebase.auth.ListUsersPage.DefaultUserSource;
import com.google.firebase.auth.internal.FirebaseTokenFactory;
import com.google.firebase.internal.CallableOperation;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This is the abstract class for server-side Firebase Authentication actions.
 */
public abstract class AbstractFirebaseAuth {

  private final Object lock = new Object();
  private final AtomicBoolean destroyed = new AtomicBoolean(false);

  private final FirebaseApp firebaseApp;
  private final Supplier<FirebaseTokenFactory> tokenFactory;
  private final Supplier<? extends FirebaseTokenVerifier> idTokenVerifier;
  private final Supplier<? extends FirebaseTokenVerifier> cookieVerifier;
  private final Supplier<? extends FirebaseUserManager> userManager;
  private final JsonFactory jsonFactory;

  protected AbstractFirebaseAuth(Builder<?> builder) {
    this.firebaseApp = checkNotNull(builder.firebaseApp);
    this.tokenFactory = threadSafeMemoize(builder.tokenFactory);
    this.idTokenVerifier = threadSafeMemoize(builder.idTokenVerifier);
    this.cookieVerifier = threadSafeMemoize(builder.cookieVerifier);
    this.userManager = threadSafeMemoize(builder.userManager);
    this.jsonFactory = firebaseApp.getOptions().getJsonFactory();
  }

  /**
   * Creates a Firebase custom token for the given UID. This token can then be sent back to a client
   * application to be used with the <a
   * href="/docs/auth/admin/create-custom-tokens#sign_in_using_custom_tokens_on_clients">signInWithCustomToken</a>
   * authentication API.
   *
   * <p>{@link FirebaseApp} must have been initialized with service account credentials to use call
   * this method.
   *
   * @param uid The UID to store in the token. This identifies the user to other Firebase services
   *     (Realtime Database, Firebase Auth, etc.). Should be less than 128 characters.
   * @return A Firebase custom token string.
   * @throws IllegalArgumentException If the specified uid is null or empty, or if the app has not
   *     been initialized with service account credentials.
   * @throws FirebaseAuthException If an error occurs while generating the custom token.
   */
  public String createCustomToken(@NonNull String uid) throws FirebaseAuthException {
    return createCustomToken(uid, null);
  }

  /**
   * Creates a Firebase custom token for the given UID, containing the specified additional claims.
   * This token can then be sent back to a client application to be used with the <a
   * href="/docs/auth/admin/create-custom-tokens#sign_in_using_custom_tokens_on_clients">signInWithCustomToken</a>
   * authentication API.
   *
   * <p>This method attempts to generate a token using:
   *
   * <ol>
   *   <li>the private key of {@link FirebaseApp}'s service account credentials, if provided at
   *       initialization.
   *   <li>the <a
   *       href="https://cloud.google.com/iam/reference/rest/v1/projects.serviceAccounts/signBlob">IAM
   *       service</a> if a service account email was specified via {@link
   *       com.google.firebase.FirebaseOptions.Builder#setServiceAccountId(String)}.
   *   <li>the <a href="https://cloud.google.com/appengine/docs/standard/java/appidentity/">App
   *       Identity service</a> if the code is deployed in the Google App Engine standard
   *       environment.
   *   <li>the <a href="https://cloud.google.com/compute/docs/storing-retrieving-metadata">local
   *       Metadata server</a> if the code is deployed in a different GCP-managed environment like
   *       Google Compute Engine.
   * </ol>
   *
   * <p>This method throws an exception when all the above fail.
   *
   * @param uid The UID to store in the token. This identifies the user to other Firebase services
   *     (Realtime Database, Firebase Auth, etc.). Should be less than 128 characters.
   * @param developerClaims Additional claims to be stored in the token (and made available to
   *     security rules in Database, Storage, etc.). These must be able to be serialized to JSON
   *     (e.g. contain only Maps, Arrays, Strings, Booleans, Numbers, etc.)
   * @return A Firebase custom token string.
   * @throws IllegalArgumentException If the specified uid is null or empty.
   * @throws IllegalStateException If the SDK fails to discover a viable approach for signing
   *     tokens.
   * @throws FirebaseAuthException If an error occurs while generating the custom token.
   */
  public String createCustomToken(
      @NonNull String uid, @Nullable Map<String, Object> developerClaims)
      throws FirebaseAuthException {
    return createCustomTokenOp(uid, developerClaims).call();
  }

  /**
   * Similar to {@link #createCustomToken(String)} but performs the operation asynchronously.
   *
   * @param uid The UID to store in the token. This identifies the user to other Firebase services
   *     (Realtime Database, Firebase Auth, etc.). Should be less than 128 characters.
   * @return An {@code ApiFuture} which will complete successfully with the created Firebase custom
   *     token, or unsuccessfully with the failure Exception.
   * @throws IllegalArgumentException If the specified uid is null or empty, or if the app has not
   *     been initialized with service account credentials.
   */
  public ApiFuture<String> createCustomTokenAsync(@NonNull String uid) {
    return createCustomTokenAsync(uid, null);
  }

  /**
   * Similar to {@link #createCustomToken(String, Map)} but performs the operation asynchronously.
   *
   * @param uid The UID to store in the token. This identifies the user to other Firebase services
   *     (Realtime Database, Storage, etc.). Should be less than 128 characters.
   * @param developerClaims Additional claims to be stored in the token (and made available to
   *     security rules in Database, Storage, etc.). These must be able to be serialized to JSON
   *     (e.g. contain only Maps, Arrays, Strings, Booleans, Numbers, etc.)
   * @return An {@code ApiFuture} which will complete successfully with the created Firebase custom
   *     token, or unsuccessfully with the failure Exception.
   * @throws IllegalArgumentException If the specified uid is null or empty, or if the app has not
   *     been initialized with service account credentials.
   */
  public ApiFuture<String> createCustomTokenAsync(
      @NonNull String uid, @Nullable Map<String, Object> developerClaims) {
    return createCustomTokenOp(uid, developerClaims).callAsync(firebaseApp);
  }

  private CallableOperation<String, FirebaseAuthException> createCustomTokenOp(
      final String uid, final Map<String, Object> developerClaims) {
    checkArgument(!Strings.isNullOrEmpty(uid), "uid must not be null or empty");
    final FirebaseTokenFactory tokenFactory = this.tokenFactory.get();
    return new CallableOperation<String, FirebaseAuthException>() {
      @Override
      public String execute() throws FirebaseAuthException {
        return tokenFactory.createSignedCustomAuthTokenForUser(uid, developerClaims);
      }
    };
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
    return createSessionCookieOp(idToken, options).callAsync(firebaseApp);
  }

  private CallableOperation<String, FirebaseAuthException> createSessionCookieOp(
      final String idToken, final SessionCookieOptions options) {
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
   * Parses and verifies a Firebase ID Token.
   *
   * <p>A Firebase application can identify itself to a trusted backend server by sending its
   * Firebase ID Token (accessible via the {@code getToken} API in the Firebase Authentication
   * client) with its requests. The backend server can then use the {@code verifyIdToken()} method
   * to verify that the token is valid. This method ensures that the token is correctly signed, has
   * not expired, and it was issued to the Firebase project associated with this {@link
   * FirebaseAuth} instance.
   *
   * <p>This method does not check whether a token has been revoked. Use {@link
   * #verifyIdToken(String, boolean)} to perform an additional revocation check.
   *
   * @param idToken A Firebase ID token string to parse and verify.
   * @return A {@link FirebaseToken} representing the verified and decoded token.
   * @throws IllegalArgumentException If the token is null, empty, or if the {@link FirebaseApp}
   *     instance does not have a project ID associated with it.
   * @throws FirebaseAuthException If an error occurs while parsing or validating the token.
   */
  public FirebaseToken verifyIdToken(@NonNull String idToken) throws FirebaseAuthException {
    return verifyIdToken(idToken, false);
  }

  /**
   * Parses and verifies a Firebase ID Token.
   *
   * <p>A Firebase application can identify itself to a trusted backend server by sending its
   * Firebase ID Token (accessible via the {@code getToken} API in the Firebase Authentication
   * client) with its requests. The backend server can then use the {@code verifyIdToken()} method
   * to verify that the token is valid. This method ensures that the token is correctly signed, has
   * not expired, and it was issued to the Firebase project associated with this {@link
   * FirebaseAuth} instance.
   *
   * <p>If {@code checkRevoked} is set to true, this method performs an additional check to see if
   * the ID token has been revoked since it was issues. This requires making an additional remote
   * API call.
   *
   * @param idToken A Firebase ID token string to parse and verify.
   * @param checkRevoked A boolean denoting whether to check if the tokens were revoked.
   * @return A {@link FirebaseToken} representing the verified and decoded token.
   * @throws IllegalArgumentException If the token is null, empty, or if the {@link FirebaseApp}
   *     instance does not have a project ID associated with it.
   * @throws FirebaseAuthException If an error occurs while parsing or validating the token.
   */
  public FirebaseToken verifyIdToken(@NonNull String idToken, boolean checkRevoked)
      throws FirebaseAuthException {
    return verifyIdTokenOp(idToken, checkRevoked).call();
  }

  /**
   * Similar to {@link #verifyIdToken(String)} but performs the operation asynchronously.
   *
   * @param idToken A Firebase ID Token to verify and parse.
   * @return An {@code ApiFuture} which will complete successfully with the parsed token, or
   *     unsuccessfully with a {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the token is null, empty, or if the {@link FirebaseApp}
   *     instance does not have a project ID associated with it.
   */
  public ApiFuture<FirebaseToken> verifyIdTokenAsync(@NonNull String idToken) {
    return verifyIdTokenAsync(idToken, false);
  }

  /**
   * Similar to {@link #verifyIdToken(String, boolean)} but performs the operation asynchronously.
   *
   * @param idToken A Firebase ID Token to verify and parse.
   * @param checkRevoked A boolean denoting whether to check if the tokens were revoked.
   * @return An {@code ApiFuture} which will complete successfully with the parsed token, or
   *     unsuccessfully with a {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the token is null, empty, or if the {@link FirebaseApp}
   *     instance does not have a project ID associated with it.
   */
  public ApiFuture<FirebaseToken>
      verifyIdTokenAsync(@NonNull String idToken, boolean checkRevoked) {
    return verifyIdTokenOp(idToken, checkRevoked).callAsync(firebaseApp);
  }

  private CallableOperation<FirebaseToken, FirebaseAuthException> verifyIdTokenOp(
      final String idToken, final boolean checkRevoked) {
    checkArgument(!Strings.isNullOrEmpty(idToken), "ID token must not be null or empty");
    final FirebaseTokenVerifier verifier = getIdTokenVerifier(checkRevoked);
    return new CallableOperation<FirebaseToken, FirebaseAuthException>() {
      @Override
      protected FirebaseToken execute() throws FirebaseAuthException {
        return verifier.verifyToken(idToken);
      }
    };
  }

  @VisibleForTesting
  FirebaseTokenVerifier getIdTokenVerifier(boolean checkRevoked) {
    FirebaseTokenVerifier verifier = idTokenVerifier.get();
    if (checkRevoked) {
      FirebaseUserManager userManager = getUserManager();
      verifier = RevocationCheckDecorator.decorateIdTokenVerifier(verifier, userManager);
    }
    return verifier;
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
    return verifySessionCookieOp(cookie, checkRevoked).callAsync(firebaseApp);
  }

  private CallableOperation<FirebaseToken, FirebaseAuthException> verifySessionCookieOp(
      final String cookie, final boolean checkRevoked) {
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
    FirebaseTokenVerifier verifier = cookieVerifier.get();
    if (checkRevoked) {
      FirebaseUserManager userManager = getUserManager();
      verifier = RevocationCheckDecorator.decorateSessionCookieVerifier(verifier, userManager);
    }
    return verifier;
  }

  /**
   * Revokes all refresh tokens for the specified user.
   *
   * <p>Updates the user's tokensValidAfterTimestamp to the current UTC time expressed in
   * milliseconds since the epoch and truncated to 1 second accuracy. It is important that the
   * server on which this is called has its clock set correctly and synchronized.
   *
   * <p>While this will revoke all sessions for a specified user and disable any new ID tokens for
   * existing sessions from getting minted, existing ID tokens may remain active until their natural
   * expiration (one hour). To verify that ID tokens are revoked, use {@link
   * #verifyIdTokenAsync(String, boolean)}.
   *
   * @param uid The user id for which tokens are revoked.
   * @throws IllegalArgumentException If the user ID is null or empty.
   * @throws FirebaseAuthException If an error occurs while revoking tokens.
   */
  public void revokeRefreshTokens(@NonNull String uid) throws FirebaseAuthException {
    revokeRefreshTokensOp(uid).call();
  }

  /**
   * Similar to {@link #revokeRefreshTokens(String)} but performs the operation asynchronously.
   *
   * @param uid The user id for which tokens are revoked.
   * @return An {@code ApiFuture} which will complete successfully or fail with a {@link
   *     FirebaseAuthException} in the event of an error.
   * @throws IllegalArgumentException If the user ID is null or empty.
   */
  public ApiFuture<Void> revokeRefreshTokensAsync(@NonNull String uid) {
    return revokeRefreshTokensOp(uid).callAsync(firebaseApp);
  }

  private CallableOperation<Void, FirebaseAuthException> revokeRefreshTokensOp(final String uid) {
    checkArgument(!Strings.isNullOrEmpty(uid), "uid must not be null or empty");
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<Void, FirebaseAuthException>() {
      @Override
      protected Void execute() throws FirebaseAuthException {
        int currentTimeSeconds = (int) (System.currentTimeMillis() / 1000);
        UserRecord.UpdateRequest request =
            new UserRecord.UpdateRequest(uid).setValidSince(currentTimeSeconds);
        userManager.updateUser(request, jsonFactory);
        return null;
      }
    };
  }

  /**
   * Gets the user data corresponding to the specified user ID.
   *
   * @param uid A user ID string.
   * @return A {@link UserRecord} instance.
   * @throws IllegalArgumentException If the user ID string is null or empty.
   * @throws FirebaseAuthException If an error occurs while retrieving user data.
   */
  public UserRecord getUser(@NonNull String uid) throws FirebaseAuthException {
    return getUserOp(uid).call();
  }

  /**
   * Similar to {@link #getUser(String)} but performs the operation asynchronously.
   *
   * @param uid A user ID string.
   * @return An {@code ApiFuture} which will complete successfully with a {@link UserRecord}
   *     instance. If an error occurs while retrieving user data or if the specified user ID does
   *     not exist, the future throws a {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the user ID string is null or empty.
   */
  public ApiFuture<UserRecord> getUserAsync(@NonNull String uid) {
    return getUserOp(uid).callAsync(firebaseApp);
  }

  private CallableOperation<UserRecord, FirebaseAuthException> getUserOp(final String uid) {
    checkArgument(!Strings.isNullOrEmpty(uid), "uid must not be null or empty");
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<UserRecord, FirebaseAuthException>() {
      @Override
      protected UserRecord execute() throws FirebaseAuthException {
        return userManager.getUserById(uid);
      }
    };
  }

  /**
   * Gets the user data corresponding to the specified user email.
   *
   * @param email A user email address string.
   * @return A {@link UserRecord} instance.
   * @throws IllegalArgumentException If the email is null or empty.
   * @throws FirebaseAuthException If an error occurs while retrieving user data.
   */
  public UserRecord getUserByEmail(@NonNull String email) throws FirebaseAuthException {
    return getUserByEmailOp(email).call();
  }

  /**
   * Similar to {@link #getUserByEmail(String)} but performs the operation asynchronously.
   *
   * @param email A user email address string.
   * @return An {@code ApiFuture} which will complete successfully with a {@link UserRecord}
   *     instance. If an error occurs while retrieving user data or if the email address does not
   *     correspond to a user, the future throws a {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the email is null or empty.
   */
  public ApiFuture<UserRecord> getUserByEmailAsync(@NonNull String email) {
    return getUserByEmailOp(email).callAsync(firebaseApp);
  }

  private CallableOperation<UserRecord, FirebaseAuthException> getUserByEmailOp(
      final String email) {
    checkArgument(!Strings.isNullOrEmpty(email), "email must not be null or empty");
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<UserRecord, FirebaseAuthException>() {
      @Override
      protected UserRecord execute() throws FirebaseAuthException {
        return userManager.getUserByEmail(email);
      }
    };
  }

  /**
   * Gets the user data corresponding to the specified user phone number.
   *
   * @param phoneNumber A user phone number string.
   * @return A a {@link UserRecord} instance.
   * @throws IllegalArgumentException If the phone number is null or empty.
   * @throws FirebaseAuthException If an error occurs while retrieving user data.
   */
  public UserRecord getUserByPhoneNumber(@NonNull String phoneNumber) throws FirebaseAuthException {
    return getUserByPhoneNumberOp(phoneNumber).call();
  }

  /**
   * Gets the user data corresponding to the specified user phone number.
   *
   * @param phoneNumber A user phone number string.
   * @return An {@code ApiFuture} which will complete successfully with a {@link UserRecord}
   *     instance. If an error occurs while retrieving user data or if the phone number does not
   *     correspond to a user, the future throws a {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the phone number is null or empty.
   */
  public ApiFuture<UserRecord> getUserByPhoneNumberAsync(@NonNull String phoneNumber) {
    return getUserByPhoneNumberOp(phoneNumber).callAsync(firebaseApp);
  }

  private CallableOperation<UserRecord, FirebaseAuthException> getUserByPhoneNumberOp(
      final String phoneNumber) {
    checkArgument(!Strings.isNullOrEmpty(phoneNumber), "phone number must not be null or empty");
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<UserRecord, FirebaseAuthException>() {
      @Override
      protected UserRecord execute() throws FirebaseAuthException {
        return userManager.getUserByPhoneNumber(phoneNumber);
      }
    };
  }

  /**
   * Gets a page of users starting from the specified {@code pageToken}. Page size is limited to
   * 1000 users.
   *
   * @param pageToken A non-empty page token string, or null to retrieve the first page of users.
   * @return A {@link ListUsersPage} instance.
   * @throws IllegalArgumentException If the specified page token is empty.
   * @throws FirebaseAuthException If an error occurs while retrieving user data.
   */
  public ListUsersPage listUsers(@Nullable String pageToken) throws FirebaseAuthException {
    return listUsers(pageToken, FirebaseUserManager.MAX_LIST_USERS_RESULTS);
  }

  /**
   * Gets a page of users starting from the specified {@code pageToken}.
   *
   * @param pageToken A non-empty page token string, or null to retrieve the first page of users.
   * @param maxResults Maximum number of users to include in the returned page. This may not exceed
   *     1000.
   * @return A {@link ListUsersPage} instance.
   * @throws IllegalArgumentException If the specified page token is empty, or max results value is
   *     invalid.
   * @throws FirebaseAuthException If an error occurs while retrieving user data.
   */
  public ListUsersPage listUsers(@Nullable String pageToken, int maxResults)
      throws FirebaseAuthException {
    return listUsersOp(pageToken, maxResults).call();
  }

  /**
   * Similar to {@link #listUsers(String)} but performs the operation asynchronously.
   *
   * @param pageToken A non-empty page token string, or null to retrieve the first page of users.
   * @return An {@code ApiFuture} which will complete successfully with a {@link ListUsersPage}
   *     instance. If an error occurs while retrieving user data, the future throws an exception.
   * @throws IllegalArgumentException If the specified page token is empty.
   */
  public ApiFuture<ListUsersPage> listUsersAsync(@Nullable String pageToken) {
    return listUsersAsync(pageToken, FirebaseUserManager.MAX_LIST_USERS_RESULTS);
  }

  /**
   * Similar to {@link #listUsers(String, int)} but performs the operation asynchronously.
   *
   * @param pageToken A non-empty page token string, or null to retrieve the first page of users.
   * @param maxResults Maximum number of users to include in the returned page. This may not exceed
   *     1000.
   * @return An {@code ApiFuture} which will complete successfully with a {@link ListUsersPage}
   *     instance. If an error occurs while retrieving user data, the future throws an exception.
   * @throws IllegalArgumentException If the specified page token is empty, or max results value is
   *     invalid.
   */
  public ApiFuture<ListUsersPage> listUsersAsync(@Nullable String pageToken, int maxResults) {
    return listUsersOp(pageToken, maxResults).callAsync(firebaseApp);
  }

  private CallableOperation<ListUsersPage, FirebaseAuthException> listUsersOp(
      @Nullable final String pageToken, final int maxResults) {
    final FirebaseUserManager userManager = getUserManager();
    final DefaultUserSource source = new DefaultUserSource(userManager, jsonFactory);
    final ListUsersPage.Factory factory = new ListUsersPage.Factory(source, maxResults, pageToken);
    return new CallableOperation<ListUsersPage, FirebaseAuthException>() {
      @Override
      protected ListUsersPage execute() throws FirebaseAuthException {
        return factory.create();
      }
    };
  }

  /**
   * Creates a new user account with the attributes contained in the specified {@link
   * UserRecord.CreateRequest}.
   *
   * @param request A non-null {@link UserRecord.CreateRequest} instance.
   * @return A {@link UserRecord} instance corresponding to the newly created account.
   * @throws NullPointerException if the provided request is null.
   * @throws FirebaseAuthException if an error occurs while creating the user account.
   */
  public UserRecord createUser(@NonNull UserRecord.CreateRequest request)
      throws FirebaseAuthException {
    return createUserOp(request).call();
  }

  /**
   * Similar to {@link #createUser} but performs the operation asynchronously.
   *
   * @param request A non-null {@link UserRecord.CreateRequest} instance.
   * @return An {@code ApiFuture} which will complete successfully with a {@link UserRecord}
   *     instance corresponding to the newly created account. If an error occurs while creating the
   *     user account, the future throws a {@link FirebaseAuthException}.
   * @throws NullPointerException if the provided request is null.
   */
  public ApiFuture<UserRecord> createUserAsync(@NonNull UserRecord.CreateRequest request) {
    return createUserOp(request).callAsync(firebaseApp);
  }

  private CallableOperation<UserRecord, FirebaseAuthException> createUserOp(
      final UserRecord.CreateRequest request) {
    checkNotNull(request, "create request must not be null");
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<UserRecord, FirebaseAuthException>() {
      @Override
      protected UserRecord execute() throws FirebaseAuthException {
        String uid = userManager.createUser(request);
        return userManager.getUserById(uid);
      }
    };
  }

  /**
   * Updates an existing user account with the attributes contained in the specified {@link
   * UserRecord.UpdateRequest}.
   *
   * @param request A non-null {@link UserRecord.UpdateRequest} instance.
   * @return A {@link UserRecord} instance corresponding to the updated user account.
   * @throws NullPointerException if the provided update request is null.
   * @throws FirebaseAuthException if an error occurs while updating the user account.
   */
  public UserRecord updateUser(@NonNull UserRecord.UpdateRequest request)
      throws FirebaseAuthException {
    return updateUserOp(request).call();
  }

  /**
   * Similar to {@link #updateUser} but performs the operation asynchronously.
   *
   * @param request A non-null {@link UserRecord.UpdateRequest} instance.
   * @return An {@code ApiFuture} which will complete successfully with a {@link UserRecord}
   *     instance corresponding to the updated user account. If an error occurs while updating the
   *     user account, the future throws a {@link FirebaseAuthException}.
   */
  public ApiFuture<UserRecord> updateUserAsync(@NonNull UserRecord.UpdateRequest request) {
    return updateUserOp(request).callAsync(firebaseApp);
  }

  private CallableOperation<UserRecord, FirebaseAuthException> updateUserOp(
      final UserRecord.UpdateRequest request) {
    checkNotNull(request, "update request must not be null");
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<UserRecord, FirebaseAuthException>() {
      @Override
      protected UserRecord execute() throws FirebaseAuthException {
        userManager.updateUser(request, jsonFactory);
        return userManager.getUserById(request.getUid());
      }
    };
  }

  /**
   * Sets the specified custom claims on an existing user account. A null claims value removes any
   * claims currently set on the user account. The claims should serialize into a valid JSON string.
   * The serialized claims must not be larger than 1000 characters.
   *
   * @param uid A user ID string.
   * @param claims A map of custom claims or null.
   * @throws FirebaseAuthException If an error occurs while updating custom claims.
   * @throws IllegalArgumentException If the user ID string is null or empty, or the claims payload
   *     is invalid or too large.
   */
  public void setCustomUserClaims(@NonNull String uid, @Nullable Map<String, Object> claims)
      throws FirebaseAuthException {
    setCustomUserClaimsOp(uid, claims).call();
  }

  /**
   * @deprecated Use {@link #setCustomUserClaims(String, Map)} instead.
   */
  public void setCustomClaims(@NonNull String uid, @Nullable Map<String, Object> claims)
      throws FirebaseAuthException {
    setCustomUserClaims(uid, claims);
  }

  /**
   * Similar to {@link #setCustomUserClaims(String, Map)} but performs the operation asynchronously.
   *
   * @param uid A user ID string.
   * @param claims A map of custom claims or null.
   * @return An {@code ApiFuture} which will complete successfully when the user account has been
   *     updated. If an error occurs while deleting the user account, the future throws a {@link
   *     FirebaseAuthException}.
   * @throws IllegalArgumentException If the user ID string is null or empty.
   */
  public ApiFuture<Void> setCustomUserClaimsAsync(
      @NonNull String uid, @Nullable Map<String, Object> claims) {
    return setCustomUserClaimsOp(uid, claims).callAsync(firebaseApp);
  }

  private CallableOperation<Void, FirebaseAuthException> setCustomUserClaimsOp(
      final String uid, final Map<String, Object> claims) {
    checkArgument(!Strings.isNullOrEmpty(uid), "uid must not be null or empty");
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<Void, FirebaseAuthException>() {
      @Override
      protected Void execute() throws FirebaseAuthException {
        final UserRecord.UpdateRequest request =
            new UserRecord.UpdateRequest(uid).setCustomClaims(claims);
        userManager.updateUser(request, jsonFactory);
        return null;
      }
    };
  }

  /**
   * Deletes the user identified by the specified user ID.
   *
   * @param uid A user ID string.
   * @throws IllegalArgumentException If the user ID string is null or empty.
   * @throws FirebaseAuthException If an error occurs while deleting the user.
   */
  public void deleteUser(@NonNull String uid) throws FirebaseAuthException {
    deleteUserOp(uid).call();
  }

  /**
   * Similar to {@link #deleteUser(String)} but performs the operation asynchronously.
   *
   * @param uid A user ID string.
   * @return An {@code ApiFuture} which will complete successfully when the specified user account
   *     has been deleted. If an error occurs while deleting the user account, the future throws a
   *     {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the user ID string is null or empty.
   */
  public ApiFuture<Void> deleteUserAsync(String uid) {
    return deleteUserOp(uid).callAsync(firebaseApp);
  }

  private CallableOperation<Void, FirebaseAuthException> deleteUserOp(final String uid) {
    checkArgument(!Strings.isNullOrEmpty(uid), "uid must not be null or empty");
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<Void, FirebaseAuthException>() {
      @Override
      protected Void execute() throws FirebaseAuthException {
        userManager.deleteUser(uid);
        return null;
      }
    };
  }

  /**
   * Imports the provided list of users into Firebase Auth. At most 1000 users can be imported at a
   * time. This operation is optimized for bulk imports and will ignore checks on identifier
   * uniqueness which could result in duplications.
   *
   * <p>{@link UserImportOptions} is required to import users with passwords. See {@link
   * #importUsers(List, UserImportOptions)}.
   *
   * @param users A non-empty list of users to be imported. Length must not exceed 1000.
   * @return A {@link UserImportResult} instance.
   * @throws IllegalArgumentException If the users list is null, empty or has more than 1000
   *     elements. Or if at least one user specifies a password.
   * @throws FirebaseAuthException If an error occurs while importing users.
   */
  public UserImportResult importUsers(List<ImportUserRecord> users) throws FirebaseAuthException {
    return importUsers(users, null);
  }

  /**
   * Imports the provided list of users into Firebase Auth. At most 1000 users can be imported at a
   * time. This operation is optimized for bulk imports and will ignore checks on identifier
   * uniqueness which could result in duplications.
   *
   * @param users A non-empty list of users to be imported. Length must not exceed 1000.
   * @param options a {@link UserImportOptions} instance or null. Required when importing users with
   *     passwords.
   * @return A {@link UserImportResult} instance.
   * @throws IllegalArgumentException If the users list is null, empty or has more than 1000
   *     elements. Or if at least one user specifies a password, and options is null.
   * @throws FirebaseAuthException If an error occurs while importing users.
   */
  public UserImportResult importUsers(
      List<ImportUserRecord> users, @Nullable UserImportOptions options)
      throws FirebaseAuthException {
    return importUsersOp(users, options).call();
  }

  /**
   * Similar to {@link #importUsers(List)} but performs the operation asynchronously.
   *
   * @param users A non-empty list of users to be imported. Length must not exceed 1000.
   * @return An {@code ApiFuture} which will complete successfully when the user accounts are
   *     imported. If an error occurs while importing the users, the future throws a {@link
   *     FirebaseAuthException}.
   * @throws IllegalArgumentException If the users list is null, empty or has more than 1000
   *     elements. Or if at least one user specifies a password.
   */
  public ApiFuture<UserImportResult> importUsersAsync(List<ImportUserRecord> users) {
    return importUsersAsync(users, null);
  }

  /**
   * Similar to {@link #importUsers(List, UserImportOptions)} but performs the operation
   * asynchronously.
   *
   * @param users A non-empty list of users to be imported. Length must not exceed 1000.
   * @param options a {@link UserImportOptions} instance or null. Required when importing users with
   *     passwords.
   * @return An {@code ApiFuture} which will complete successfully when the user accounts are
   *     imported. If an error occurs while importing the users, the future throws a {@link
   *     FirebaseAuthException}.
   * @throws IllegalArgumentException If the users list is null, empty or has more than 1000
   *     elements. Or if at least one user specifies a password, and options is null.
   */
  public ApiFuture<UserImportResult> importUsersAsync(
      List<ImportUserRecord> users, @Nullable UserImportOptions options) {
    return importUsersOp(users, options).callAsync(firebaseApp);
  }

  private CallableOperation<UserImportResult, FirebaseAuthException> importUsersOp(
      final List<ImportUserRecord> users, final UserImportOptions options) {
    final UserImportRequest request = new UserImportRequest(users, options, jsonFactory);
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<UserImportResult, FirebaseAuthException>() {
      @Override
      protected UserImportResult execute() throws FirebaseAuthException {
        return userManager.importUsers(request);
      }
    };
  }

  /**
   * Gets the user data corresponding to the specified identifiers.
   *
   * <p>There are no ordering guarantees; in particular, the nth entry in the users result list is
   * not guaranteed to correspond to the nth entry in the input parameters list.
   *
   * <p>A maximum of 100 identifiers may be specified. If more than 100 identifiers are
   * supplied, this method throws an {@code IllegalArgumentException}.
   *
   * @param identifiers The identifiers used to indicate which user records should be returned. Must
   *     have 100 or fewer entries.
   * @return The corresponding user records.
   * @throws IllegalArgumentException If any of the identifiers are invalid or if more than 100
   *     identifiers are specified.
   * @throws NullPointerException If the identifiers parameter is null.
   * @throws FirebaseAuthException If an error occurs while retrieving user data.
   */
  public GetUsersResult getUsers(@NonNull Collection<UserIdentifier> identifiers)
      throws FirebaseAuthException {
    return getUsersOp(identifiers).call();
  }

  /**
   * Gets the user data corresponding to the specified identifiers.
   *
   * <p>There are no ordering guarantees; in particular, the nth entry in the users result list is
   * not guaranteed to correspond to the nth entry in the input parameters list.
   *
   * <p>A maximum of 100 identifiers may be specified. If more than 100 identifiers are
   * supplied, this method throws an {@code IllegalArgumentException}.
   *
   * @param identifiers The identifiers used to indicate which user records should be returned.
   *     Must have 100 or fewer entries.
   * @return An {@code ApiFuture} that resolves to the corresponding user records.
   * @throws IllegalArgumentException If any of the identifiers are invalid or if more than 100
   *     identifiers are specified.
   * @throws NullPointerException If the identifiers parameter is null.
   */
  public ApiFuture<GetUsersResult> getUsersAsync(@NonNull Collection<UserIdentifier> identifiers) {
    return getUsersOp(identifiers).callAsync(firebaseApp);
  }

  private CallableOperation<GetUsersResult, FirebaseAuthException> getUsersOp(
      @NonNull final Collection<UserIdentifier> identifiers) {
    checkNotNull(identifiers, "identifiers must not be null");
    checkArgument(identifiers.size() <= FirebaseUserManager.MAX_GET_ACCOUNTS_BATCH_SIZE,
        "identifiers parameter must have <= " + FirebaseUserManager.MAX_GET_ACCOUNTS_BATCH_SIZE
            + " entries.");

    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<GetUsersResult, FirebaseAuthException>() {
      @Override
      protected GetUsersResult execute() throws FirebaseAuthException {
        Set<UserRecord> users = userManager.getAccountInfo(identifiers);
        Set<UserIdentifier> notFound = new HashSet<>();
        for (UserIdentifier id : identifiers) {
          if (!isUserFound(id, users)) {
            notFound.add(id);
          }
        }
        return new GetUsersResult(users, notFound);
      }
    };
  }

  private boolean isUserFound(UserIdentifier id, Collection<UserRecord> userRecords) {
    for (UserRecord userRecord : userRecords) {
      if (id.matches(userRecord)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Deletes the users specified by the given identifiers.
   *
   * <p>Deleting a non-existing user does not generate an error (the method is idempotent).
   * Non-existing users are considered to be successfully deleted and are therefore included in the
   * DeleteUsersResult.getSuccessCount() value.
   *
   * <p>A maximum of 1000 identifiers may be supplied. If more than 1000 identifiers are
   * supplied, this method throws an {@code IllegalArgumentException}.
   *
   * <p>This API has a rate limit of 1 QPS. Exceeding the limit may result in a quota exceeded
   * error. If you want to delete more than 1000 users, we suggest adding a delay to ensure you
   * don't exceed this limit.
   *
   * @param uids The uids of the users to be deleted. Must have <= 1000 entries.
   * @return The total number of successful/failed deletions, as well as the array of errors that
   *     correspond to the failed deletions.
   * @throws IllegalArgumentException If any of the identifiers are invalid or if more than 1000
   *     identifiers are specified.
   * @throws FirebaseAuthException If an error occurs while deleting users.
   */
  public DeleteUsersResult deleteUsers(List<String> uids) throws FirebaseAuthException {
    return deleteUsersOp(uids).call();
  }

  /**
   * Similar to {@link #deleteUsers(List)} but performs the operation asynchronously.
   *
   * @param uids The uids of the users to be deleted. Must have <= 1000 entries.
   * @return An {@code ApiFuture} that resolves to the total number of successful/failed
   *     deletions, as well as the array of errors that correspond to the failed deletions. If an
   *     error occurs while deleting the user account, the future throws a
   *     {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If any of the identifiers are invalid or if more than 1000
   *     identifiers are specified.
   */
  public ApiFuture<DeleteUsersResult> deleteUsersAsync(List<String> uids) {
    return deleteUsersOp(uids).callAsync(firebaseApp);
  }

  private CallableOperation<DeleteUsersResult, FirebaseAuthException> deleteUsersOp(
      final List<String> uids) {
    checkNotNull(uids, "uids must not be null");
    for (String uid : uids) {
      UserRecord.checkUid(uid);
    }
    checkArgument(uids.size() <= FirebaseUserManager.MAX_DELETE_ACCOUNTS_BATCH_SIZE,
        "uids parameter must have <= " + FirebaseUserManager.MAX_DELETE_ACCOUNTS_BATCH_SIZE
            + " entries.");
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<DeleteUsersResult, FirebaseAuthException>() {
      @Override
      protected DeleteUsersResult execute() throws FirebaseAuthException {
        return userManager.deleteUsers(uids);
      }
    };
  }

  /**
   * Generates the out-of-band email action link for password reset flows for the specified email
   * address.
   *
   * @param email The email of the user whose password is to be reset.
   * @return A password reset link.
   * @throws IllegalArgumentException If the email address is null or empty.
   * @throws FirebaseAuthException If an error occurs while generating the link.
   */
  public String generatePasswordResetLink(@NonNull String email) throws FirebaseAuthException {
    return generatePasswordResetLink(email, null);
  }

  /**
   * Generates the out-of-band email action link for password reset flows for the specified email
   * address.
   *
   * @param email The email of the user whose password is to be reset.
   * @param settings The action code settings object which defines whether the link is to be handled
   *     by a mobile app and the additional state information to be passed in the deep link.
   * @return A password reset link.
   * @throws IllegalArgumentException If the email address is null or empty.
   * @throws FirebaseAuthException If an error occurs while generating the link.
   */
  public String generatePasswordResetLink(
      @NonNull String email, @Nullable ActionCodeSettings settings) throws FirebaseAuthException {
    return generateEmailActionLinkOp(EmailLinkType.PASSWORD_RESET, email, settings).call();
  }

  /**
   * Similar to {@link #generatePasswordResetLink(String)} but performs the operation
   * asynchronously.
   *
   * @param email The email of the user whose password is to be reset.
   * @return An {@code ApiFuture} which will complete successfully with the generated email action
   *     link. If an error occurs while generating the link, the future throws a {@link
   *     FirebaseAuthException}.
   * @throws IllegalArgumentException If the email address is null or empty.
   */
  public ApiFuture<String> generatePasswordResetLinkAsync(@NonNull String email) {
    return generatePasswordResetLinkAsync(email, null);
  }

  /**
   * Similar to {@link #generatePasswordResetLink(String, ActionCodeSettings)} but performs the
   * operation asynchronously.
   *
   * @param email The email of the user whose password is to be reset.
   * @param settings The action code settings object which defines whether the link is to be handled
   *     by a mobile app and the additional state information to be passed in the deep link.
   * @return An {@code ApiFuture} which will complete successfully with the generated email action
   *     link. If an error occurs while generating the link, the future throws a {@link
   *     FirebaseAuthException}.
   * @throws IllegalArgumentException If the email address is null or empty.
   */
  public ApiFuture<String> generatePasswordResetLinkAsync(
      @NonNull String email, @Nullable ActionCodeSettings settings) {
    return generateEmailActionLinkOp(EmailLinkType.PASSWORD_RESET, email, settings)
        .callAsync(firebaseApp);
  }

  /**
   * Generates the out-of-band email action link for email verification flows for the specified
   * email address.
   *
   * @param email The email of the user to be verified.
   * @return An email verification link.
   * @throws IllegalArgumentException If the email address is null or empty.
   * @throws FirebaseAuthException If an error occurs while generating the link.
   */
  public String generateEmailVerificationLink(@NonNull String email) throws FirebaseAuthException {
    return generateEmailVerificationLink(email, null);
  }

  /**
   * Generates the out-of-band email action link for email verification flows for the specified
   * email address, using the action code settings provided.
   *
   * @param email The email of the user to be verified.
   * @return An email verification link.
   * @throws IllegalArgumentException If the email address is null or empty.
   * @throws FirebaseAuthException If an error occurs while generating the link.
   */
  public String generateEmailVerificationLink(
      @NonNull String email, @Nullable ActionCodeSettings settings) throws FirebaseAuthException {
    return generateEmailActionLinkOp(EmailLinkType.VERIFY_EMAIL, email, settings).call();
  }

  /**
   * Similar to {@link #generateEmailVerificationLink(String)} but performs the operation
   * asynchronously.
   *
   * @param email The email of the user to be verified.
   * @return An {@code ApiFuture} which will complete successfully with the generated email action
   *     link. If an error occurs while generating the link, the future throws a {@link
   *     FirebaseAuthException}.
   * @throws IllegalArgumentException If the email address is null or empty.
   */
  public ApiFuture<String> generateEmailVerificationLinkAsync(@NonNull String email) {
    return generateEmailVerificationLinkAsync(email, null);
  }

  /**
   * Similar to {@link #generateEmailVerificationLink(String, ActionCodeSettings)} but performs the
   * operation asynchronously.
   *
   * @param email The email of the user to be verified.
   * @param settings The action code settings object which defines whether the link is to be handled
   *     by a mobile app and the additional state information to be passed in the deep link.
   * @return An {@code ApiFuture} which will complete successfully with the generated email action
   *     link. If an error occurs while generating the link, the future throws a {@link
   *     FirebaseAuthException}.
   * @throws IllegalArgumentException If the email address is null or empty.
   */
  public ApiFuture<String> generateEmailVerificationLinkAsync(
      @NonNull String email, @Nullable ActionCodeSettings settings) {
    return generateEmailActionLinkOp(EmailLinkType.VERIFY_EMAIL, email, settings)
        .callAsync(firebaseApp);
  }

  /**
   * Generates the out-of-band email action link for email link sign-in flows, using the action code
   * settings provided.
   *
   * @param email The email of the user signing in.
   * @param settings The action code settings object which defines whether the link is to be handled
   *     by a mobile app and the additional state information to be passed in the deep link.
   * @return An email verification link.
   * @throws IllegalArgumentException If the email address is null or empty.
   * @throws FirebaseAuthException If an error occurs while generating the link.
   */
  public String generateSignInWithEmailLink(
      @NonNull String email, @NonNull ActionCodeSettings settings) throws FirebaseAuthException {
    return generateEmailActionLinkOp(EmailLinkType.EMAIL_SIGNIN, email, settings).call();
  }

  /**
   * Similar to {@link #generateSignInWithEmailLink(String, ActionCodeSettings)} but performs the
   * operation asynchronously.
   *
   * @param email The email of the user signing in.
   * @param settings The action code settings object which defines whether the link is to be handled
   *     by a mobile app and the additional state information to be passed in the deep link.
   * @return An {@code ApiFuture} which will complete successfully with the generated email action
   *     link. If an error occurs while generating the link, the future throws a {@link
   *     FirebaseAuthException}.
   * @throws IllegalArgumentException If the email address is null or empty.
   * @throws NullPointerException If the settings is null.
   */
  public ApiFuture<String> generateSignInWithEmailLinkAsync(
      String email, @NonNull ActionCodeSettings settings) {
    return generateEmailActionLinkOp(EmailLinkType.EMAIL_SIGNIN, email, settings)
        .callAsync(firebaseApp);
  }

  private CallableOperation<String, FirebaseAuthException> generateEmailActionLinkOp(
      final EmailLinkType type, final String email, final ActionCodeSettings settings) {
    checkArgument(!Strings.isNullOrEmpty(email), "email must not be null or empty");
    if (type == EmailLinkType.EMAIL_SIGNIN) {
      checkNotNull(settings, "ActionCodeSettings must not be null when generating sign-in links");
    }
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<String, FirebaseAuthException>() {
      @Override
      protected String execute() throws FirebaseAuthException {
        return userManager.getEmailActionLink(type, email, settings);
      }
    };
  }

  /**
   * Creates a new OpenID Connect auth provider config with the attributes contained in the
   * specified {@link OidcProviderConfig.CreateRequest}.
   *
   * @param request A non-null {@link OidcProviderConfig.CreateRequest} instance.
   * @return An {@link OidcProviderConfig} instance corresponding to the newly created provider
   *     config.
   * @throws NullPointerException if the provided request is null.
   * @throws IllegalArgumentException If the provider ID string is null or empty, or is not
   *     prefixed with 'oidc.'.
   * @throws FirebaseAuthException if an error occurs while creating the provider config.
   */
  public OidcProviderConfig createOidcProviderConfig(
      @NonNull OidcProviderConfig.CreateRequest request) throws FirebaseAuthException {
    return createOidcProviderConfigOp(request).call();
  }

  /**
   * Similar to {@link #createOidcProviderConfig} but performs the operation asynchronously.
   *
   * @param request A non-null {@link OidcProviderConfig.CreateRequest} instance.
   * @return An {@code ApiFuture} which will complete successfully with a {@link OidcProviderConfig}
   *     instance corresponding to the newly created provider config. If an error occurs while
   *     creating the provider config, the future throws a {@link FirebaseAuthException}.
   * @throws NullPointerException if the provided request is null.
   * @throws IllegalArgumentException If the provider ID string is null or empty, or is not
   *     prefixed with 'oidc.'.
   */
  public ApiFuture<OidcProviderConfig> createOidcProviderConfigAsync(
      @NonNull OidcProviderConfig.CreateRequest request) {
    return createOidcProviderConfigOp(request).callAsync(firebaseApp);
  }

  private CallableOperation<OidcProviderConfig, FirebaseAuthException>
      createOidcProviderConfigOp(final OidcProviderConfig.CreateRequest request) {
    checkNotNull(request, "Create request must not be null.");
    OidcProviderConfig.checkOidcProviderId(request.getProviderId());
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<OidcProviderConfig, FirebaseAuthException>() {
      @Override
      protected OidcProviderConfig execute() throws FirebaseAuthException {
        return userManager.createOidcProviderConfig(request);
      }
    };
  }

  /**
   * Updates an existing OpenID Connect auth provider config with the attributes contained in the
   * specified {@link OidcProviderConfig.UpdateRequest}.
   *
   * @param request A non-null {@link OidcProviderConfig.UpdateRequest} instance.
   * @return A {@link OidcProviderConfig} instance corresponding to the updated provider config.
   * @throws NullPointerException if the provided update request is null.
   * @throws IllegalArgumentException If the provided update request is invalid.
   * @throws FirebaseAuthException if an error occurs while updating the provider config.
   */
  public OidcProviderConfig updateOidcProviderConfig(
      @NonNull OidcProviderConfig.UpdateRequest request) throws FirebaseAuthException {
    return updateOidcProviderConfigOp(request).call();
  }

  /**
   * Similar to {@link #updateOidcProviderConfig} but performs the operation asynchronously.
   *
   * @param request A non-null {@link OidcProviderConfig.UpdateRequest} instance.
   * @return An {@code ApiFuture} which will complete successfully with a {@link OidcProviderConfig}
   *     instance corresponding to the updated provider config. If an error occurs while updating
   *     the provider config, the future throws a {@link FirebaseAuthException}.
   * @throws NullPointerException if the provided update request is null.
   * @throws IllegalArgumentException If the provided update request is invalid.
   */
  public ApiFuture<OidcProviderConfig> updateOidcProviderConfigAsync(
      @NonNull OidcProviderConfig.UpdateRequest request) {
    return updateOidcProviderConfigOp(request).callAsync(firebaseApp);
  }

  private CallableOperation<OidcProviderConfig, FirebaseAuthException> updateOidcProviderConfigOp(
      final OidcProviderConfig.UpdateRequest request) {
    checkNotNull(request, "Update request must not be null.");
    checkArgument(!request.getProperties().isEmpty(),
        "Update request must have at least one property set.");
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<OidcProviderConfig, FirebaseAuthException>() {
      @Override
      protected OidcProviderConfig execute() throws FirebaseAuthException {
        return userManager.updateOidcProviderConfig(request);
      }
    };
  }

  /**
   * Gets the OpenID Connect auth provider corresponding to the specified provider ID.
   *
   * @param providerId A provider ID string.
   * @return An {@link OidcProviderConfig} instance.
   * @throws IllegalArgumentException If the provider ID string is null or empty, or is not prefixed
   *     with 'oidc'.
   * @throws FirebaseAuthException If an error occurs while retrieving the provider config.
   */
  public OidcProviderConfig getOidcProviderConfig(@NonNull String providerId)
      throws FirebaseAuthException {
    return getOidcProviderConfigOp(providerId).call();
  }

  /**
   * Similar to {@link #getOidcProviderConfig(String)} but performs the operation asynchronously.
   * Page size is limited to 100 provider configs.
   *
   * @param providerId A provider ID string.
   * @return An {@code ApiFuture} which will complete successfully with an
   *     {@link OidcProviderConfig} instance. If an error occurs while retrieving the provider
   *     config or if the specified provider ID does not exist, the future throws a
   *     {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the provider ID string is null or empty, or is not
   *     prefixed with 'oidc.'.
   */
  public ApiFuture<OidcProviderConfig> getOidcProviderConfigAsync(@NonNull String providerId) {
    return getOidcProviderConfigOp(providerId).callAsync(firebaseApp);
  }

  private CallableOperation<OidcProviderConfig, FirebaseAuthException>
      getOidcProviderConfigOp(final String providerId) {
    OidcProviderConfig.checkOidcProviderId(providerId);
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<OidcProviderConfig, FirebaseAuthException>() {
      @Override
      protected OidcProviderConfig execute() throws FirebaseAuthException {
        return userManager.getOidcProviderConfig(providerId);
      }
    };
  }

  /**
   * Gets a page of OpenID Connect auth provider configs starting from the specified
   * {@code pageToken}. Page size is limited to 100 provider configs.
   *
   * @param pageToken A non-empty page token string, or null to retrieve the first page of provider
   *     configs.
   * @return A {@link ListProviderConfigsPage} instance.
   * @throws IllegalArgumentException If the specified page token is empty
   * @throws FirebaseAuthException If an error occurs while retrieving provider config data.
   */
  public ListProviderConfigsPage<OidcProviderConfig> listOidcProviderConfigs(
        @Nullable String pageToken) throws FirebaseAuthException {
    int maxResults = FirebaseUserManager.MAX_LIST_PROVIDER_CONFIGS_RESULTS;
    return listOidcProviderConfigsOp(pageToken, maxResults).call();
  }

  /**
   * Gets a page of OpenID Connect auth provider configs starting from the specified
   * {@code pageToken}.
   *
   * @param pageToken A non-empty page token string, or null to retrieve the first page of provider
   *     configs.
   * @param maxResults Maximum number of provider configs to include in the returned page. This may
   *     not exceed 100.
   * @return A {@link ListProviderConfigsPage} instance.
   * @throws IllegalArgumentException If the specified page token is empty, or max results value is
   *     invalid.
   * @throws FirebaseAuthException If an error occurs while retrieving provider config data.
   */
  public ListProviderConfigsPage<OidcProviderConfig> listOidcProviderConfigs(
        @Nullable String pageToken, int maxResults) throws FirebaseAuthException {
    return listOidcProviderConfigsOp(pageToken, maxResults).call();
  }

  /**
   * Similar to {@link #listOidcProviderConfigs(String)} but performs the operation asynchronously.
   * Page size is limited to 100 provider configs.
   *
   * @param pageToken A non-empty page token string, or null to retrieve the first page of provider
   *     configs.
   * @return An {@code ApiFuture} which will complete successfully with a
   *     {@link ListProviderConfigsPage} instance. If an error occurs while retrieving provider
   *     config data, the future throws an exception.
   * @throws IllegalArgumentException If the specified page token is empty.
   */
  public ApiFuture<ListProviderConfigsPage<OidcProviderConfig>> listOidcProviderConfigsAsync(
      @Nullable String pageToken) {
    int maxResults = FirebaseUserManager.MAX_LIST_PROVIDER_CONFIGS_RESULTS;
    return listOidcProviderConfigsAsync(pageToken, maxResults);
  }

  /**
   * Similar to {@link #listOidcProviderConfigs(String, int)} but performs the operation
   * asynchronously.
   *
   * @param pageToken A non-empty page token string, or null to retrieve the first page of provider
   *     configs.
   * @param maxResults Maximum number of provider configs to include in the returned page. This may
   *     not exceed 100.
   * @return An {@code ApiFuture} which will complete successfully with a
   *     {@link ListProviderConfigsPage} instance. If an error occurs while retrieving provider
   *     config data, the future throws an exception.
   * @throws IllegalArgumentException If the specified page token is empty, or max results value is
   *     invalid.
   */
  public ApiFuture<ListProviderConfigsPage<OidcProviderConfig>> listOidcProviderConfigsAsync(
      @Nullable String pageToken,
      int maxResults) {
    return listOidcProviderConfigsOp(pageToken, maxResults).callAsync(firebaseApp);
  }

  private CallableOperation<ListProviderConfigsPage<OidcProviderConfig>, FirebaseAuthException>
      listOidcProviderConfigsOp(@Nullable final String pageToken, final int maxResults) {
    final FirebaseUserManager userManager = getUserManager();
    final DefaultOidcProviderConfigSource source = new DefaultOidcProviderConfigSource(userManager);
    final ListProviderConfigsPage.Factory<OidcProviderConfig> factory =
        new ListProviderConfigsPage.Factory<OidcProviderConfig>(source, maxResults, pageToken);
    return
      new CallableOperation<ListProviderConfigsPage<OidcProviderConfig>, FirebaseAuthException>() {
        @Override
        protected ListProviderConfigsPage<OidcProviderConfig> execute()
            throws FirebaseAuthException {
          return factory.create();
        }
    };
  }

  /**
   * Deletes the OpenID Connect auth provider config identified by the specified provider ID.
   *
   * @param providerId A provider ID string.
   * @throws IllegalArgumentException If the provider ID string is null or empty, or is not prefixed
   *     with 'oidc'.
   * @throws FirebaseAuthException If an error occurs while deleting the provider config.
   */
  public void deleteOidcProviderConfig(@NonNull String providerId) throws FirebaseAuthException {
    deleteOidcProviderConfigOp(providerId).call();
  }

  /**
   * Similar to {@link #deleteOidcProviderConfig} but performs the operation asynchronously.
   *
   * @param providerId A provider ID string.
   * @return An {@code ApiFuture} which will complete successfully when the specified provider
   *     config has been deleted. If an error occurs while deleting the provider config, the future
   *     throws a {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the provider ID string is null or empty, or is not prefixed
   *     with "oidc.".
   */
  public ApiFuture<Void> deleteOidcProviderConfigAsync(String providerId) {
    return deleteOidcProviderConfigOp(providerId).callAsync(firebaseApp);
  }

  private CallableOperation<Void, FirebaseAuthException> deleteOidcProviderConfigOp(
      final String providerId) {
    OidcProviderConfig.checkOidcProviderId(providerId);
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<Void, FirebaseAuthException>() {
      @Override
      protected Void execute() throws FirebaseAuthException {
        userManager.deleteOidcProviderConfig(providerId);
        return null;
      }
    };
  }

  /**
   * Creates a new SAML Auth provider config with the attributes contained in the specified
   * {@link SamlProviderConfig.CreateRequest}.
   *
   * @param request A non-null {@link SamlProviderConfig.CreateRequest} instance.
   * @return An {@link SamlProviderConfig} instance corresponding to the newly created provider
   *     config.
   * @throws NullPointerException if the provided request is null.
   * @throws IllegalArgumentException If the provider ID string is null or empty, or is not prefixed
   *     with 'saml'.
   * @throws FirebaseAuthException if an error occurs while creating the provider config.
   */
  public SamlProviderConfig createSamlProviderConfig(
      @NonNull SamlProviderConfig.CreateRequest request) throws FirebaseAuthException {
    return createSamlProviderConfigOp(request).call();
  }

  /**
   * Similar to {@link #createSamlProviderConfig} but performs the operation asynchronously.
   *
   * @param request A non-null {@link SamlProviderConfig.CreateRequest} instance.
   * @return An {@code ApiFuture} which will complete successfully with a {@link SamlProviderConfig}
   *     instance corresponding to the newly created provider config. If an error occurs while
   *     creating the provider config, the future throws a {@link FirebaseAuthException}.
   * @throws NullPointerException if the provided request is null.
   * @throws IllegalArgumentException If the provider ID string is null or empty, or is not prefixed
   *     with 'saml'.
   */
  public ApiFuture<SamlProviderConfig> createSamlProviderConfigAsync(
      @NonNull SamlProviderConfig.CreateRequest request) {
    return createSamlProviderConfigOp(request).callAsync(firebaseApp);
  }

  private CallableOperation<SamlProviderConfig, FirebaseAuthException>
      createSamlProviderConfigOp(final SamlProviderConfig.CreateRequest request) {
    checkNotNull(request, "Create request must not be null.");
    SamlProviderConfig.checkSamlProviderId(request.getProviderId());
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<SamlProviderConfig, FirebaseAuthException>() {
      @Override
      protected SamlProviderConfig execute() throws FirebaseAuthException {
        return userManager.createSamlProviderConfig(request);
      }
    };
  }

  /**
   * Updates an existing SAML Auth provider config with the attributes contained in the specified
   * {@link SamlProviderConfig.UpdateRequest}.
   *
   * @param request A non-null {@link SamlProviderConfig.UpdateRequest} instance.
   * @return A {@link SamlProviderConfig} instance corresponding to the updated provider config.
   * @throws NullPointerException if the provided update request is null.
   * @throws IllegalArgumentException If the provided update request is invalid.
   * @throws FirebaseAuthException if an error occurs while updating the provider config.
   */
  public SamlProviderConfig updateSamlProviderConfig(
      @NonNull SamlProviderConfig.UpdateRequest request) throws FirebaseAuthException {
    return updateSamlProviderConfigOp(request).call();
  }

  /**
   * Similar to {@link #updateSamlProviderConfig} but performs the operation asynchronously.
   *
   * @param request A non-null {@link SamlProviderConfig.UpdateRequest} instance.
   * @return An {@code ApiFuture} which will complete successfully with a {@link SamlProviderConfig}
   *     instance corresponding to the updated provider config. If an error occurs while updating
   *     the provider config, the future throws a {@link FirebaseAuthException}.
   * @throws NullPointerException if the provided update request is null.
   * @throws IllegalArgumentException If the provided update request is invalid.
   */
  public ApiFuture<SamlProviderConfig> updateSamlProviderConfigAsync(
      @NonNull SamlProviderConfig.UpdateRequest request) {
    return updateSamlProviderConfigOp(request).callAsync(firebaseApp);
  }

  private CallableOperation<SamlProviderConfig, FirebaseAuthException> updateSamlProviderConfigOp(
      final SamlProviderConfig.UpdateRequest request) {
    checkNotNull(request, "Update request must not be null.");
    checkArgument(!request.getProperties().isEmpty(),
        "Update request must have at least one property set.");
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<SamlProviderConfig, FirebaseAuthException>() {
      @Override
      protected SamlProviderConfig execute() throws FirebaseAuthException {
        return userManager.updateSamlProviderConfig(request);
      }
    };
  }

  /**
   * Gets the SAML Auth provider config corresponding to the specified provider ID.
   *
   * @param providerId A provider ID string.
   * @return An {@link SamlProviderConfig} instance.
   * @throws IllegalArgumentException If the provider ID string is null or empty, or is not prefixed
   *     with 'saml'.
   * @throws FirebaseAuthException If an error occurs while retrieving the provider config.
   */
  public SamlProviderConfig getSamlProviderConfig(@NonNull String providerId)
      throws FirebaseAuthException {
    return getSamlProviderConfigOp(providerId).call();
  }

  /**
   * Similar to {@link #getSamlProviderConfig(String)} but performs the operation asynchronously.
   * Page size is limited to 100 provider configs.
   *
   * @param providerId A provider ID string.
   * @return An {@code ApiFuture} which will complete successfully with an
   *     {@link SamlProviderConfig} instance. If an error occurs while retrieving the provider
   *     config or if the specified provider ID does not exist, the future throws a
   *     {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the provider ID string is null or empty, or is not prefixed
   *     with 'saml'.
   */
  public ApiFuture<SamlProviderConfig> getSamlProviderConfigAsync(@NonNull String providerId) {
    return getSamlProviderConfigOp(providerId).callAsync(firebaseApp);
  }

  private CallableOperation<SamlProviderConfig, FirebaseAuthException>
      getSamlProviderConfigOp(final String providerId) {
    SamlProviderConfig.checkSamlProviderId(providerId);
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<SamlProviderConfig, FirebaseAuthException>() {
      @Override
      protected SamlProviderConfig execute() throws FirebaseAuthException {
        return userManager.getSamlProviderConfig(providerId);
      }
    };
  }

  /**
   * Gets a page of SAML Auth provider configs starting from the specified {@code pageToken}. Page
   * size is limited to 100 provider configs.
   *
   * @param pageToken A non-empty page token string, or null to retrieve the first page of provider
   *     configs.
   * @return A {@link ListProviderConfigsPage} instance.
   * @throws IllegalArgumentException If the specified page token is empty.
   * @throws FirebaseAuthException If an error occurs while retrieving provider config data.
   */
  public ListProviderConfigsPage<SamlProviderConfig> listSamlProviderConfigs(
        @Nullable String pageToken) throws FirebaseAuthException {
    return listSamlProviderConfigs(
        pageToken,
        FirebaseUserManager.MAX_LIST_PROVIDER_CONFIGS_RESULTS);
  }

  /**
   * Gets a page of SAML Auth provider configs starting from the specified {@code pageToken}.
   *
   * @param pageToken A non-empty page token string, or null to retrieve the first page of provider
   *     configs.
   * @param maxResults Maximum number of provider configs to include in the returned page. This may
   *     not exceed 100.
   * @return A {@link ListProviderConfigsPage} instance.
   * @throws IllegalArgumentException If the specified page token is empty, or max results value is
   *     invalid.
   * @throws FirebaseAuthException If an error occurs while retrieving provider config data.
   */
  public ListProviderConfigsPage<SamlProviderConfig> listSamlProviderConfigs(
        @Nullable String pageToken, int maxResults) throws FirebaseAuthException {
    return listSamlProviderConfigsOp(pageToken, maxResults).call();
  }

  /**
   * Similar to {@link #listSamlProviderConfigs(String)} but performs the operation asynchronously.
   * Page size is limited to 100 provider configs.
   *
   * @param pageToken A non-empty page token string, or null to retrieve the first page of provider
   *     configs.
   * @return An {@code ApiFuture} which will complete successfully with a
   *     {@link ListProviderConfigsPage} instance. If an error occurs while retrieving provider
   *     config data, the future throws an exception.
   * @throws IllegalArgumentException If the specified page token is empty.
   */
  public ApiFuture<ListProviderConfigsPage<SamlProviderConfig>> listSamlProviderConfigsAsync(
      @Nullable String pageToken) {
    int maxResults = FirebaseUserManager.MAX_LIST_PROVIDER_CONFIGS_RESULTS;
    return listSamlProviderConfigsAsync(pageToken, maxResults);
  }

  /**
   * Similar to {@link #listSamlProviderConfigs(String, int)} but performs the operation
   * asynchronously.
   *
   * @param pageToken A non-empty page token string, or null to retrieve the first page of provider
   *     configs.
   * @param maxResults Maximum number of provider configs to include in the returned page. This may
   *     not exceed 100.
   * @return An {@code ApiFuture} which will complete successfully with a
   *     {@link ListProviderConfigsPage} instance. If an error occurs while retrieving provider
   *     config data, the future throws an exception.
   * @throws IllegalArgumentException If the specified page token is empty, or max results value is
   *     invalid.
   */
  public ApiFuture<ListProviderConfigsPage<SamlProviderConfig>> listSamlProviderConfigsAsync(
      @Nullable String pageToken,
      int maxResults) {
    return listSamlProviderConfigsOp(pageToken, maxResults).callAsync(firebaseApp);
  }

  private CallableOperation<ListProviderConfigsPage<SamlProviderConfig>, FirebaseAuthException>
      listSamlProviderConfigsOp(@Nullable final String pageToken, final int maxResults) {
    final FirebaseUserManager userManager = getUserManager();
    final DefaultSamlProviderConfigSource source = new DefaultSamlProviderConfigSource(userManager);
    final ListProviderConfigsPage.Factory<SamlProviderConfig> factory =
        new ListProviderConfigsPage.Factory<SamlProviderConfig>(source, maxResults, pageToken);
    return
      new CallableOperation<ListProviderConfigsPage<SamlProviderConfig>, FirebaseAuthException>() {
        @Override
        protected ListProviderConfigsPage<SamlProviderConfig> execute()
            throws FirebaseAuthException {
          return factory.create();
        }
    };
  }

  /**
   * Deletes the SAML Auth provider config identified by the specified provider ID.
   *
   * @param providerId A provider ID string.
   * @throws IllegalArgumentException If the provider ID string is null or empty, or is not prefixed
   *     with "saml.".
   * @throws FirebaseAuthException If an error occurs while deleting the provider config.
   */
  public void deleteSamlProviderConfig(@NonNull String providerId) throws FirebaseAuthException {
    deleteSamlProviderConfigOp(providerId).call();
  }

  /**
   * Similar to {@link #deleteSamlProviderConfig} but performs the operation asynchronously.
   *
   * @param providerId A provider ID string.
   * @return An {@code ApiFuture} which will complete successfully when the specified provider
   *     config has been deleted. If an error occurs while deleting the provider config, the future
   *     throws a {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the provider ID string is null or empty, or is not prefixed
   *     with "saml.".
   */
  public ApiFuture<Void> deleteSamlProviderConfigAsync(String providerId) {
    return deleteSamlProviderConfigOp(providerId).callAsync(firebaseApp);
  }

  private CallableOperation<Void, FirebaseAuthException> deleteSamlProviderConfigOp(
      final String providerId) {
    SamlProviderConfig.checkSamlProviderId(providerId);
    final FirebaseUserManager userManager = getUserManager();
    return new CallableOperation<Void, FirebaseAuthException>() {
      @Override
      protected Void execute() throws FirebaseAuthException {
        userManager.deleteSamlProviderConfig(providerId);
        return null;
      }
    };
  }

  FirebaseUserManager getUserManager() {
    return this.userManager.get();
  }

  <T> Supplier<T> threadSafeMemoize(final Supplier<T> supplier) {
    return Suppliers.memoize(
        new Supplier<T>() {
          @Override
          public T get() {
            checkNotNull(supplier);
            synchronized (lock) {
              return supplier.get();
            }
          }
        });
  }

  protected abstract static class Builder<T extends  Builder<T>> {

    private FirebaseApp firebaseApp;
    private Supplier<FirebaseTokenFactory> tokenFactory;
    private Supplier<? extends FirebaseTokenVerifier> idTokenVerifier;
    private Supplier<? extends FirebaseTokenVerifier> cookieVerifier;
    private Supplier<? extends FirebaseUserManager> userManager;

    protected abstract T getThis();

    public FirebaseApp getFirebaseApp() {
      return firebaseApp;
    }

    public T setFirebaseApp(FirebaseApp firebaseApp) {
      this.firebaseApp = firebaseApp;
      return getThis();
    }

    public T setIdTokenVerifier(Supplier<? extends FirebaseTokenVerifier> idTokenVerifier) {
      this.idTokenVerifier = idTokenVerifier;
      return getThis();
    }

    public T setCookieVerifier(Supplier<? extends FirebaseTokenVerifier> cookieVerifier) {
      this.cookieVerifier = cookieVerifier;
      return getThis();
    }

    T setUserManager(Supplier<FirebaseUserManager> userManager) {
      this.userManager = userManager;
      return getThis();
    }

    T setTokenFactory(Supplier<FirebaseTokenFactory> tokenFactory) {
      this.tokenFactory = tokenFactory;
      return getThis();
    }
  }

  protected static <T extends Builder<T>> T populateBuilderFromApp(
      Builder<T> builder, final FirebaseApp app, @Nullable final String tenantId) {
    return builder.setFirebaseApp(app)
        .setTokenFactory(
            new Supplier<FirebaseTokenFactory>() {
              @Override
              public FirebaseTokenFactory get() {
                return FirebaseTokenUtils.createTokenFactory(app, Clock.SYSTEM, tenantId);
              }
            })
        .setIdTokenVerifier(
            new Supplier<FirebaseTokenVerifier>() {
              @Override
              public FirebaseTokenVerifier get() {
                return FirebaseTokenUtils.createIdTokenVerifier(app, Clock.SYSTEM, tenantId);
              }
            })
        .setCookieVerifier(
            new Supplier<FirebaseTokenVerifier>() {
              @Override
              public FirebaseTokenVerifier get() {
                return FirebaseTokenUtils.createSessionCookieVerifier(app, Clock.SYSTEM, tenantId);
              }
            })
        .setUserManager(
            new Supplier<FirebaseUserManager>() {
              @Override
              public FirebaseUserManager get() {
                return FirebaseUserManager.createUserManager(app,  tenantId);
              }
            });
  }
}
