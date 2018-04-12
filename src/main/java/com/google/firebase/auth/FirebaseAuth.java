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

package com.google.firebase.auth;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.Clock;
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.auth.ListUsersPage.DefaultUserSource;
import com.google.firebase.auth.ListUsersPage.PageFactory;
import com.google.firebase.auth.UserRecord.CreateRequest;
import com.google.firebase.auth.UserRecord.UpdateRequest;
import com.google.firebase.auth.internal.FirebaseTokenFactory;
import com.google.firebase.auth.internal.FirebaseTokenVerifier;
import com.google.firebase.auth.internal.KeyManagers;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import com.google.firebase.internal.TaskToApiFuture;
import com.google.firebase.tasks.Task;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class is the entry point for all server-side Firebase Authentication actions.
 *
 * <p>You can get an instance of FirebaseAuth via {@link FirebaseAuth#getInstance(FirebaseApp)} and
 * then use it to perform a variety of authentication-related operations, including generating
 * custom tokens for use by client-side code, verifying Firebase ID Tokens received from clients, or
 * creating new FirebaseApp instances that are scoped to a particular authentication UID.
 */
public class FirebaseAuth {

  private final Clock clock;

  private final FirebaseApp firebaseApp;
  private final KeyManagers keyManagers;
  private final GoogleCredentials credentials;
  private final String projectId;
  private final JsonFactory jsonFactory;
  private final FirebaseUserManager userManager;
  private final AtomicBoolean destroyed;
  private final Object lock;

  private FirebaseAuth(FirebaseApp firebaseApp) {
    this(firebaseApp, KeyManagers.getDefault(firebaseApp, Clock.SYSTEM), Clock.SYSTEM);
  }

  /**
   * Constructor for injecting a GooglePublicKeysManager, which is used to verify tokens are
   * correctly signed. This should only be used for testing to override the default key manager.
   */
  @VisibleForTesting
  FirebaseAuth(FirebaseApp firebaseApp, KeyManagers keyManagers, Clock clock) {
    this.firebaseApp = checkNotNull(firebaseApp);
    this.keyManagers = checkNotNull(keyManagers);
    this.clock = checkNotNull(clock);
    this.credentials = ImplFirebaseTrampolines.getCredentials(firebaseApp);
    this.projectId = ImplFirebaseTrampolines.getProjectId(firebaseApp);
    this.jsonFactory = firebaseApp.getOptions().getJsonFactory();
    this.userManager = new FirebaseUserManager(jsonFactory,
        firebaseApp.getOptions().getHttpTransport(), this.credentials);
    this.destroyed = new AtomicBoolean(false);
    this.lock = new Object();
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
    FirebaseAuthService service = ImplFirebaseTrampolines.getService(app, SERVICE_ID,
        FirebaseAuthService.class);
    if (service == null) {
      service = ImplFirebaseTrampolines.addService(app, new FirebaseAuthService(app));
    }
    return service.getInstance();
  }

  private Task<String> createSessionCookie(
      final String idToken, final SessionCookieOptions options) {
    checkNotDestroyed();
    checkArgument(!Strings.isNullOrEmpty(idToken), "idToken must not be null or empty");
    checkNotNull(options, "options must not be null");
    return call(new Callable<String>() {
      @Override
      public String call() throws Exception {
        return userManager.createSessionCookie(idToken, options);
      }
    });
  }

  /**
   * Creates a new Firebase session cookie from the given ID token and options. The returned JWT
   * can be set as a server-side session cookie with a custom cookie policy.
   *
   * @param idToken The Firebase ID token to exchange for a session cookie.
   * @param options Additional options required to create the cookie.
   * @return An {@code ApiFuture} which will complete successfully with a session cookie string.
   *     If an error occurs while generating the cookie or if the specified ID token is invalid,
   *     the future throws a {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the ID token is null or empty, or if options is null.
   */
  public ApiFuture<String> createSessionCookieAsync(
      @NonNull String idToken, @NonNull SessionCookieOptions options) {
    return new TaskToApiFuture<>(createSessionCookie(idToken, options));
  }

  /**
   * Parses and verifies a Firebase session cookie.
   *
   * <p>If the cookie is valid, the returned Future will complete successfully and provide a
   * parsed version of the cookie from which the UID and other claims can be read.
   * If the cookie is invalid, the future throws an exception indicating the failure.
   *
   * <p>This does not check whether the cookie has been revoked. See
   * {@link #verifySessionCookie(String, boolean)}.
   *
   * @param cookie A Firebase session cookie string to verify and parse.
   * @return An {@code ApiFuture} which will complete successfully with the parsed cookie, or
   *     unsuccessfully with the failure Exception.
   */
  public ApiFuture<FirebaseToken> verifySessionCookieAsync(String cookie) {
    return new TaskToApiFuture<>(verifySessionCookie(cookie, false));
  }

  /**
   * Parses and verifies a Firebase session cookie.
   *
   * <p>If the cookie is valid, the returned Future will complete successfully and provide a
   * parsed version of the cookie from which the UID and other claims can be inspected.
   * If the cookie is invalid, the future throws an exception indicating the failure.
   *
   * <p>If {@code checkRevoked} is true, additionally checks if the cookie has been revoked.
   *
   * <p>If the cookie is valid, and not revoked, the returned Future will complete successfully and
   * provide a parsed version of the cookie from which the UID and other claims can be read.
   * If the cookie is invalid or has been revoked, the future throws an exception indicating the
   * failure.
   *
   * @param cookie A Firebase session cookie string to verify and parse.
   * @param checkRevoked A boolean indicating whether to check if the cookie was revoked.
   * @return An {@code ApiFuture} which will complete successfully with the parsed cookie, or
   *     unsuccessfully with the failure Exception.
   */
  public ApiFuture<FirebaseToken> verifySessionCookieAsync(String cookie, boolean checkRevoked) {
    return new TaskToApiFuture<>(verifySessionCookie(cookie, checkRevoked));
  }

  private Task<FirebaseToken> verifySessionCookie(final String cookie, final boolean checkRevoked) {
    checkNotDestroyed();
    checkState(!Strings.isNullOrEmpty(projectId),
        "Must initialize FirebaseApp with a project ID to call verifySessionCookie()");
    return call(new Callable<FirebaseToken>() {
      @Override
      public FirebaseToken call() throws Exception {
        FirebaseTokenVerifier firebaseTokenVerifier =
            FirebaseTokenVerifier.createSessionCookieVerifier(projectId, keyManagers, clock);
        FirebaseToken firebaseToken = FirebaseToken.parse(jsonFactory, cookie);
        // This will throw a FirebaseAuthException with details on how the token is invalid.
        firebaseTokenVerifier.verifyTokenAndSignature(firebaseToken.getToken());

        if (checkRevoked) {
          checkRevoked(firebaseToken, "session cookie",
              FirebaseUserManager.SESSION_COOKIE_REVOKED_ERROR);
        }
        return firebaseToken;
      }
    });
  }

  private void checkRevoked(
      FirebaseToken firebaseToken, String label, String errorCode) throws FirebaseAuthException {
    String uid = firebaseToken.getUid();
    UserRecord user = userManager.getUserById(uid);
    long issuedAt = (long) firebaseToken.getClaims().get("iat");
    if (user.getTokensValidAfterTimestamp() > issuedAt * 1000) {
      throw new FirebaseAuthException(errorCode, "Firebase " + label + " revoked");
    }
  }

  /**
   * Similar to {@link #createCustomTokenAsync(String)}, but returns a {@link Task}.
   *
   * @param uid The UID to store in the token. This identifies the user to other Firebase services
   *     (Firebase Database, Firebase Auth, etc.)
   * @return A {@link Task} which will complete successfully with the created Firebase Custom Token,
   *     or unsuccessfully with the failure Exception.
   * @deprecated Use {@link #createCustomTokenAsync(String)}
   */
  public Task<String> createCustomToken(String uid) {
    return createCustomToken(uid, null);
  }

  /**
   * Similar to {@link #createCustomTokenAsync(String, Map)}, but returns a {@link Task}.
   *
   * @param uid The UID to store in the token. This identifies the user to other Firebase services
   *     (Realtime Database, Storage, etc.). Should be less than 128 characters.
   * @param developerClaims Additional claims to be stored in the token (and made available to
   *     security rules in Database, Storage, etc.). These must be able to be serialized to JSON
   *     (e.g. contain only Maps, Arrays, Strings, Booleans, Numbers, etc.)
   * @return A {@link Task} which will complete successfully with the created Firebase Custom Token,
   *     or unsuccessfully with the failure Exception.
   * @deprecated Use {@link #createCustomTokenAsync(String, Map)}
   */
  public Task<String> createCustomToken(
      final String uid, final Map<String, Object> developerClaims) {
    checkNotDestroyed();
    checkState(credentials instanceof ServiceAccountCredentials,
        "Must initialize FirebaseApp with a service account credential to call "
            + "createCustomToken()");

    final ServiceAccountCredentials serviceAccount = (ServiceAccountCredentials) credentials;
    return call(new Callable<String>() {
      @Override
      public String call() throws Exception {
        FirebaseTokenFactory tokenFactory = FirebaseTokenFactory.getInstance();
        return tokenFactory.createSignedCustomAuthTokenForUser(
            uid,
            developerClaims,
            serviceAccount.getClientEmail(),
            serviceAccount.getPrivateKey());
      }
    });
  }

  /**
   * Creates a Firebase Custom Token associated with the given UID. This token can then be provided
   * back to a client application for use with the
   * <a href="/docs/auth/admin/create-custom-tokens#sign_in_using_custom_tokens_on_clients">signInWithCustomToken</a>
   * authentication API.
   *
   * @param uid The UID to store in the token. This identifies the user to other Firebase services
   *     (Firebase Realtime Database, Firebase Auth, etc.)
   * @return An {@code ApiFuture} which will complete successfully with the created Firebase Custom
   *     Token, or unsuccessfully with the failure Exception.
   */
  public ApiFuture<String> createCustomTokenAsync(String uid) {
    return new TaskToApiFuture<>(createCustomToken(uid));
  }

  /**
   * Creates a Firebase Custom Token associated with the given UID and additionally containing the
   * specified developerClaims. This token can then be provided back to a client application for use
   * with the signInWithCustomToken authentication API.
   *
   * @param uid The UID to store in the token. This identifies the user to other Firebase services
   *     (Realtime Database, Storage, etc.). Should be less than 128 characters.
   * @param developerClaims Additional claims to be stored in the token (and made available to
   *     security rules in Database, Storage, etc.). These must be able to be serialized to JSON
   *     (e.g. contain only Maps, Arrays, Strings, Booleans, Numbers, etc.)
   * @return An {@code ApiFuture} which will complete successfully with the created Firebase Custom
   *     Token, or unsuccessfully with the failure Exception.
   */
  public ApiFuture<String> createCustomTokenAsync(
      final String uid, final Map<String, Object> developerClaims) {
    return new TaskToApiFuture<>(createCustomToken(uid, developerClaims));
  }

  /**
   * Similar to {@link #verifyIdTokenAsync(String)}, but returns a {@link Task}.
   *
   * @param token A Firebase ID Token to verify and parse.
   * @return A {@link Task} which will complete successfully with the parsed token, or
   *     unsuccessfully with the failure Exception.
   * @deprecated Use {@link #verifyIdTokenAsync(String)}
   */
  public Task<FirebaseToken> verifyIdToken(final String token) {
    return verifyIdToken(token, false);
  }
 
  private Task<FirebaseToken> verifyIdToken(final String token, final boolean checkRevoked) {
    checkNotDestroyed();
    checkState(!Strings.isNullOrEmpty(projectId),
        "Must initialize FirebaseApp with a project ID to call verifyIdToken()");
    return call(new Callable<FirebaseToken>() {
      @Override
      public FirebaseToken call() throws Exception {
        FirebaseTokenVerifier firebaseTokenVerifier =
            FirebaseTokenVerifier.createIdTokenVerifier(projectId, keyManagers, clock);
        FirebaseToken firebaseToken = FirebaseToken.parse(jsonFactory, token);
        // This will throw a FirebaseAuthException with details on how the token is invalid.
        firebaseTokenVerifier.verifyTokenAndSignature(firebaseToken.getToken());

        if (checkRevoked) {
          checkRevoked(firebaseToken, "auth token", FirebaseUserManager.ID_TOKEN_REVOKED_ERROR);
        }       
        return firebaseToken;
      }
    });
  }

  private Task<Void> revokeRefreshTokens(String uid) {
    checkNotDestroyed();
    int currentTimeSeconds = (int) (System.currentTimeMillis() / 1000);
    final UpdateRequest request = new UpdateRequest(uid).setValidSince(currentTimeSeconds);
    return call(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        userManager.updateUser(request, jsonFactory);
        return null;
      }
    });
  }

  /**
   * Revokes all refresh tokens for the specified user.
   * 
   * <p>Updates the user's tokensValidAfterTimestamp to the current UTC time expressed in
   * milliseconds since the epoch and truncated to 1 second accuracy. It is important that the
   * server on which this is called has its clock set correctly and synchronized.
   * 
   * <p>While this will revoke all sessions for a specified user and disable any new ID tokens for
   * existing sessions from getting minted, existing ID tokens may remain active until their
   * natural expiration (one hour). 
   * To verify that ID tokens are revoked, use {@link #verifyIdTokenAsync(String, boolean)}.
   * 
   * @param uid The user id for which tokens are revoked.
   * @return An {@code ApiFuture} which will complete successfully or if updating the user fails,
   *     unsuccessfully with the failure Exception.
   */
  public ApiFuture<Void> revokeRefreshTokensAsync(String uid) {
    return new TaskToApiFuture<>(revokeRefreshTokens(uid));
  }

  /**
   * Parses and verifies a Firebase ID Token.
   *
   * <p>A Firebase application can identify itself to a trusted backend server by sending its
   * Firebase ID Token (accessible via the getToken API in the Firebase Authentication client) with
   * its request.
   *
   * <p>The backend server can then use the verifyIdToken() method to verify the token is valid,
   * meaning: the token is properly signed, has not expired, and it was issued for the project
   * associated with this FirebaseAuth instance (which by default is extracted from your service
   * account)
   *
   * <p>If the token is valid, the returned Future will complete successfully and provide a
   * parsed version of the token from which the UID and other claims in the token can be inspected.
   * If the token is invalid, the future throws an exception indicating the failure.
   * 
   * <p>This does not check whether a token has been revoked. 
   * See {@link #verifyIdTokenAsync(String, boolean)} below.
   *
   * @param token A Firebase ID Token to verify and parse.
   * @return An {@code ApiFuture} which will complete successfully with the parsed token, or
   *     unsuccessfully with the failure Exception.
   */
  public ApiFuture<FirebaseToken> verifyIdTokenAsync(final String token) {
    return verifyIdTokenAsync(token, false);
  }

  /**
   * Parses and verifies a Firebase ID Token and if requested, checks whether it was revoked.
   *
   * <p>A Firebase application can identify itself to a trusted backend server by sending its
   * Firebase ID Token (accessible via the getToken API in the Firebase Authentication client) with
   * its request.
   *
   * <p>The backend server can then use the verifyIdToken() method to verify the token is valid,
   * meaning: the token is properly signed, has not expired, and it was issued for the project
   * associated with this FirebaseAuth instance (which by default is extracted from your service
   * account)
   * 
   * <p>If {@code checkRevoked} is true, additionally checks if the token has been revoked.
   * 
   * <p>If the token is valid, and not revoked, the returned Future will complete successfully and
   * provide a parsed version of the token from which the UID and other claims in the token can be
   * inspected.
   * If the token is invalid or has been revoked, the future throws an exception indicating the
   * failure.
   *
   * @param token A Firebase ID Token to verify and parse.
   * @param checkRevoked A boolean indicating whether to check if the tokens were revoked.
   * @return An {@code ApiFuture} which will complete successfully with the parsed token, or
   *     unsuccessfully with the failure Exception.
   */
  public ApiFuture<FirebaseToken> verifyIdTokenAsync(final String token,
                                                     final boolean checkRevoked) {
    return new TaskToApiFuture<>(verifyIdToken(token, checkRevoked));
  }

  /**
   * Similar to {@link #getUserAsync(String)}, but returns a {@link Task}.
   *
   * @param uid A user ID string.
   * @return A {@link Task} which will complete successfully with a {@link UserRecord} instance.
   *     If an error occurs while retrieving user data or if the specified user ID does not exist,
   *     the task fails with a {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the user ID string is null or empty.
   * @deprecated Use {@link #getUserAsync(String)}
   */
  public Task<UserRecord> getUser(final String uid) {
    checkNotDestroyed();
    checkArgument(!Strings.isNullOrEmpty(uid), "uid must not be null or empty");
    return call(new Callable<UserRecord>() {
      @Override
      public UserRecord call() throws Exception {
        return userManager.getUserById(uid);
      }
    });
  }

  /**
   * Gets the user data corresponding to the specified user ID.
   *
   * @param uid A user ID string.
   * @return An {@code ApiFuture} which will complete successfully with a {@link UserRecord}
   *     instance. If an error occurs while retrieving user data or if the specified user ID does
   *     not exist, the future throws a {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the user ID string is null or empty.
   */
  public ApiFuture<UserRecord> getUserAsync(final String uid) {
    return new TaskToApiFuture<>(getUser(uid));
  }

  /**
   * Similar to {@link #getUserByEmailAsync(String)}, but returns a {@link Task}.
   *
   * @param email A user email address string.
   * @return A {@link Task} which will complete successfully with a {@link UserRecord} instance.
   *     If an error occurs while retrieving user data or if the email address does not correspond
   *     to a user, the task fails with a {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the email is null or empty.
   * @deprecated Use {@link #getUserByEmailAsync(String)}
   */
  public Task<UserRecord> getUserByEmail(final String email) {
    checkNotDestroyed();
    checkArgument(!Strings.isNullOrEmpty(email), "email must not be null or empty");
    return call(new Callable<UserRecord>() {
      @Override
      public UserRecord call() throws Exception {
        return userManager.getUserByEmail(email);
      }
    });
  }

  /**
   * Gets the user data corresponding to the specified user email.
   *
   * @param email A user email address string.
   * @return An {@code ApiFuture} which will complete successfully with a {@link UserRecord}
   *     instance. If an error occurs while retrieving user data or if the email address does not
   *     correspond to a user, the future throws a {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the email is null or empty.
   */
  public ApiFuture<UserRecord> getUserByEmailAsync(final String email) {
    return new TaskToApiFuture<>(getUserByEmail(email));
  }

  /**
   * Similar to {@link #getUserByPhoneNumberAsync(String)}, but returns a {@link Task}.
   *
   * @param phoneNumber A user phone number string.
   * @return A {@link Task} which will complete successfully with a {@link UserRecord} instance.
   *     If an error occurs while retrieving user data or if the phone number does not
   *     correspond to a user, the task fails with a {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the phone number is null or empty.
   * @deprecated Use {@link #getUserByPhoneNumberAsync(String)}
   */
  public Task<UserRecord> getUserByPhoneNumber(final String phoneNumber) {
    checkNotDestroyed();
    checkArgument(!Strings.isNullOrEmpty(phoneNumber), "phone number must not be null or empty");
    return call(new Callable<UserRecord>() {
      @Override
      public UserRecord call() throws Exception {
        return userManager.getUserByPhoneNumber(phoneNumber);
      }
    });
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
  public ApiFuture<UserRecord> getUserByPhoneNumberAsync(final String phoneNumber) {
    return new TaskToApiFuture<>(getUserByPhoneNumber(phoneNumber));
  }

  private Task<ListUsersPage> listUsers(@Nullable String pageToken, int maxResults) {
    checkNotDestroyed();
    final PageFactory factory = new PageFactory(
        new DefaultUserSource(userManager, jsonFactory), maxResults, pageToken);
    return call(new Callable<ListUsersPage>() {
      @Override
      public ListUsersPage call() throws Exception {
        return factory.create();
      }
    });
  }

  /**
   * Gets a page of users starting from the specified {@code pageToken}. Page size will be
   * limited to 1000 users.
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
   * Gets a page of users starting from the specified {@code pageToken}.
   *
   * @param pageToken A non-empty page token string, or null to retrieve the first page of users.
   * @param maxResults Maximum number of users to include in the returned page. This may not
   *     exceed 1000.
   * @return An {@code ApiFuture} which will complete successfully with a {@link ListUsersPage}
   *     instance. If an error occurs while retrieving user data, the future throws an exception.
   * @throws IllegalArgumentException If the specified page token is empty, or max results value
   *     is invalid.
   */
  public ApiFuture<ListUsersPage> listUsersAsync(@Nullable String pageToken, int maxResults) {
    return new TaskToApiFuture<>(listUsers(pageToken, maxResults));
  }

  /**
   * Similar to {@link #createUserAsync(CreateRequest)}, but returns a {@link Task}.
   *
   * @param request A non-null {@link CreateRequest} instance.
   * @return A {@link Task} which will complete successfully with a {@link UserRecord} instance
   *     corresponding to the newly created account. If an error occurs while creating the user
   *     account, the task fails with a {@link FirebaseAuthException}.
   * @throws NullPointerException if the provided request is null.
   * @deprecated Use {@link #createUserAsync(CreateRequest)}
   */
  public Task<UserRecord> createUser(final CreateRequest request) {
    checkNotDestroyed();
    checkNotNull(request, "create request must not be null");
    return call(new Callable<UserRecord>() {
      @Override
      public UserRecord call() throws Exception {
        String uid = userManager.createUser(request);
        return userManager.getUserById(uid);
      }
    });
  }

  /**
   * Creates a new user account with the attributes contained in the specified
   * {@link CreateRequest}.
   *
   * @param request A non-null {@link CreateRequest} instance.
   * @return An {@code ApiFuture} which will complete successfully with a {@link UserRecord}
   *     instance corresponding to the newly created account. If an error occurs while creating the
   *     user account, the future throws a {@link FirebaseAuthException}.
   * @throws NullPointerException if the provided request is null.
   */
  public ApiFuture<UserRecord> createUserAsync(final CreateRequest request) {
    return new TaskToApiFuture<>(createUser(request));
  }

  /**
   * Similar to {@link #updateUserAsync(UpdateRequest)}, but returns a {@link Task}.
   *
   * @param request A non-null {@link UpdateRequest} instance.
   * @return A {@link Task} which will complete successfully with a {@link UserRecord} instance
   *     corresponding to the updated user account. If an error occurs while updating the user
   *     account, the task fails with a {@link FirebaseAuthException}.
   * @throws NullPointerException if the provided update request is null.
   * @deprecated Use {@link #updateUserAsync(UpdateRequest)}
   */
  public Task<UserRecord> updateUser(final UpdateRequest request) {
    checkNotDestroyed();
    checkNotNull(request, "update request must not be null");
    return call(new Callable<UserRecord>() {
      @Override
      public UserRecord call() throws Exception {
        userManager.updateUser(request, jsonFactory);
        return userManager.getUserById(request.getUid());
      }
    });
  }

  /**
   * Updates an existing user account with the attributes contained in the specified
   * {@link UpdateRequest}.
   *
   * @param request A non-null {@link UpdateRequest} instance.
   * @return An {@code ApiFuture} which will complete successfully with a {@link UserRecord}
   *     instance corresponding to the updated user account. If an error occurs while updating the
   *     user account, the future throws a {@link FirebaseAuthException}.
   * @throws NullPointerException if the provided update request is null.
   */
  public ApiFuture<UserRecord> updateUserAsync(final UpdateRequest request) {
    return new TaskToApiFuture<>(updateUser(request));
  }

  private Task<Void> setCustomClaims(String uid, Map<String, Object> claims) {
    checkNotDestroyed();
    final UpdateRequest request = new UpdateRequest(uid).setCustomClaims(claims);
    return call(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        userManager.updateUser(request, jsonFactory);
        return null;
      }
    });
  }

  /**
   * Sets the specified custom claims on an existing user account. A null claims value removes
   * any claims currently set on the user account. The claims should serialize into a valid JSON
   * string. The serialized claims must not be larger than 1000 characters.
   *
   * @param uid A user ID string.
   * @param claims A map of custom claims or null.
   * @return An {@code ApiFuture} which will complete successfully when the user account has been
   *     updated. If an error occurs while deleting the user account, the future throws a
   *     {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the user ID string is null or empty, or the claims
   *     payload is invalid or too large.
   */
  public ApiFuture<Void> setCustomUserClaimsAsync(String uid, Map<String, Object> claims) {
    return new TaskToApiFuture<>(setCustomClaims(uid, claims));
  }

  /**
   * Similar to {@link #deleteUserAsync(String)}, but returns a {@link Task}.
   *
   * @param uid A user ID string.
   * @return A {@link Task} which will complete successfully when the specified user account has
   *     been deleted. If an error occurs while deleting the user account, the task fails with a
   *     {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the user ID string is null or empty.
   * @deprecated Use {@link #deleteUserAsync(String)}
   */
  public Task<Void> deleteUser(final String uid) {
    checkNotDestroyed();
    checkArgument(!Strings.isNullOrEmpty(uid), "uid must not be null or empty");
    return call(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        userManager.deleteUser(uid);
        return null;
      }
    });
  }

  /**
   * Deletes the user identified by the specified user ID.
   *
   * @param uid A user ID string.
   * @return An {@code ApiFuture} which will complete successfully when the specified user account
   *     has been deleted. If an error occurs while deleting the user account, the future throws a
   *     {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the user ID string is null or empty.
   */
  public ApiFuture<Void> deleteUserAsync(final String uid) {
    return new TaskToApiFuture<>(deleteUser(uid));
  }

  private <T> Task<T> call(Callable<T> command) {
    return ImplFirebaseTrampolines.submitCallable(firebaseApp, command);
  }

  @VisibleForTesting
  FirebaseUserManager getUserManager() {
    return this.userManager;
  }

  private void checkNotDestroyed() {
    synchronized (lock) {
      checkState(!destroyed.get(), "FirebaseAuth instance is no longer alive. This happens when "
          + "the parent FirebaseApp instance has been deleted.");
    }
  }

  private void destroy() {
    synchronized (lock) {
      destroyed.set(true);
    }
  }

  private static final String SERVICE_ID = FirebaseAuth.class.getName();

  private static class FirebaseAuthService extends FirebaseService<FirebaseAuth> {

    FirebaseAuthService(FirebaseApp app) {
      super(SERVICE_ID, new FirebaseAuth(app));
    }

    @Override
    public void destroy() {
      instance.destroy();
    }
  }
}
