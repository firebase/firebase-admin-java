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
import static com.google.common.base.Preconditions.checkState;

import com.google.api.client.json.JsonFactory;
import com.google.api.core.ApiFuture;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseUserManager.EmailLinkType;
import com.google.firebase.auth.FirebaseUserManager.UserImportRequest;
import com.google.firebase.auth.ListProviderConfigsPage;
import com.google.firebase.auth.ListProviderConfigsPage.DefaultOidcProviderConfigSource;
import com.google.firebase.auth.ListProviderConfigsPage.DefaultSamlProviderConfigSource;
import com.google.firebase.auth.ListUsersPage;
import com.google.firebase.auth.ListUsersPage.DefaultUserSource;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.internal.FirebaseTokenFactory;
import com.google.firebase.internal.CallableOperation;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This is the abstract class for server-side Firebase Authentication actions.
 */
public abstract class AbstractFirebaseAuth {

  private static final String ERROR_CUSTOM_TOKEN = "ERROR_CUSTOM_TOKEN";

  private final Object lock = new Object();
  private final AtomicBoolean destroyed = new AtomicBoolean(false);

  private final FirebaseApp firebaseApp;
  private final Supplier<FirebaseTokenFactory> tokenFactory;
  private final Supplier<? extends FirebaseTokenVerifier> idTokenVerifier;
  private final Supplier<? extends FirebaseTokenVerifier> cookieVerifier;
  private final Supplier<? extends FirebaseUserManager> userManager;
  private final JsonFactory jsonFactory;

  AbstractFirebaseAuth(Builder builder) {
    this.firebaseApp = checkNotNull(builder.firebaseApp);
    this.tokenFactory = threadSafeMemoize(builder.tokenFactory);
    this.idTokenVerifier = threadSafeMemoize(builder.idTokenVerifier);
    this.cookieVerifier = threadSafeMemoize(builder.cookieVerifier);
    this.userManager = threadSafeMemoize(builder.userManager);
    this.jsonFactory = builder.firebaseApp.getOptions().getJsonFactory();
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
    checkNotDestroyed();
    checkArgument(!Strings.isNullOrEmpty(uid), "uid must not be null or empty");
    final FirebaseTokenFactory tokenFactory = this.tokenFactory.get();
    return new CallableOperation<String, FirebaseAuthException>() {
      @Override
      public String execute() throws FirebaseAuthException {
        try {
          return tokenFactory.createSignedCustomAuthTokenForUser(uid, developerClaims);
        } catch (IOException e) {
          throw new FirebaseAuthException(
              ERROR_CUSTOM_TOKEN, "Failed to generate a custom token", e);
        }
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
    checkNotDestroyed();
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
    checkNotDestroyed();
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
    checkNotDestroyed();
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
    checkNotDestroyed();
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
    checkNotDestroyed();
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
   * Gets a page of users starting from the specified {@code pageToken}. Page size will be limited
   * to 1000 users.
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
    checkNotDestroyed();
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
    checkNotDestroyed();
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
    checkNotDestroyed();
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
    checkNotDestroyed();
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
    checkNotDestroyed();
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
    checkNotDestroyed();
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
    checkNotDestroyed();
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
   * Creates a new OIDC Auth provider config with the attributes contained in the specified
   * {@link OidcProviderConfig.CreateRequest}.
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
    checkNotDestroyed();
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
   * Updates an existing OIDC Auth provider config with the attributes contained in the specified
   * {@link OidcProviderConfig.UpdateRequest}.
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
    checkNotDestroyed();
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
   * Gets the provider OIDC Auth config corresponding to the specified provider ID.
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
   * Page size will be limited to 100 provider configs.
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
    checkNotDestroyed();
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
   * Gets a page of OIDC Auth provider configs starting from the specified {@code pageToken}. Page
   * size will be limited to 100 provider configs.
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
   * Gets a page of OIDC Auth provider configs starting from the specified {@code pageToken}.
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
   * Page size will be limited to 100 provider configs.
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
    checkNotDestroyed();
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
   * Deletes the OIDC Auth provider config identified by the specified provider ID.
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
    checkNotDestroyed();
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
    checkNotDestroyed();
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
    checkNotDestroyed();
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
   * Gets the SAML provider Auth config corresponding to the specified provider ID.
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
   * Page size will be limited to 100 provider configs.
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
    checkNotDestroyed();
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
   * size will be limited to 100 provider configs.
   *
   * @param pageToken A non-empty page token string, or null to retrieve the first page of provider
   *     configs.
   * @return A {@link ListProviderConfigsPage} instance.
   * @throws IllegalArgumentException If the specified page token is empty
   * @throws FirebaseAuthException If an error occurs while retrieving provider config data.
   */
  public ListProviderConfigsPage<SamlProviderConfig> listSamlProviderConfigs(
        @Nullable String pageToken) throws FirebaseAuthException {
    int maxResults = FirebaseUserManager.MAX_LIST_PROVIDER_CONFIGS_RESULTS;
    return listSamlProviderConfigsOp(pageToken, maxResults).call();
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
   * Page size will be limited to 100 provider configs.
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
    checkNotDestroyed();
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
    checkNotDestroyed();
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

  FirebaseApp getFirebaseApp() {
    return this.firebaseApp;
  }

  FirebaseTokenVerifier getCookieVerifier() {
    return this.cookieVerifier.get();
  }

  FirebaseUserManager getUserManager() {
    return this.userManager.get();
  }

  protected <T> Supplier<T> threadSafeMemoize(final Supplier<T> supplier) {
    return Suppliers.memoize(
        new Supplier<T>() {
          @Override
          public T get() {
            checkNotNull(supplier);
            synchronized (lock) {
              checkNotDestroyed();
              return supplier.get();
            }
          }
        });
  }

  void checkNotDestroyed() {
    synchronized (lock) {
      checkState(
          !destroyed.get(),
          "FirebaseAuth instance is no longer alive. This happens when "
              + "the parent FirebaseApp instance has been deleted.");
    }
  }

  final void destroy() {
    synchronized (lock) {
      doDestroy();
      destroyed.set(true);
    }
  }

  /** Performs any additional required clean up. */
  protected abstract void doDestroy();

  static Builder builder() {
    return new Builder();
  }

  static class Builder {
    protected FirebaseApp firebaseApp;
    private Supplier<FirebaseTokenFactory> tokenFactory;
    private Supplier<? extends FirebaseTokenVerifier> idTokenVerifier;
    private Supplier<? extends FirebaseTokenVerifier> cookieVerifier;
    private Supplier<FirebaseUserManager> userManager;

    private Builder() {}

    Builder setFirebaseApp(FirebaseApp firebaseApp) {
      this.firebaseApp = firebaseApp;
      return this;
    }

    Builder setTokenFactory(Supplier<FirebaseTokenFactory> tokenFactory) {
      this.tokenFactory = tokenFactory;
      return this;
    }

    Builder setIdTokenVerifier(Supplier<? extends FirebaseTokenVerifier> idTokenVerifier) {
      this.idTokenVerifier = idTokenVerifier;
      return this;
    }

    Builder setCookieVerifier(Supplier<? extends FirebaseTokenVerifier> cookieVerifier) {
      this.cookieVerifier = cookieVerifier;
      return this;
    }

    Builder setUserManager(Supplier<FirebaseUserManager> userManager) {
      this.userManager = userManager;
      return this;
    }
  }
}
