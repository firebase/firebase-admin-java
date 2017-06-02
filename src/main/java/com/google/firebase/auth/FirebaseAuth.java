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

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Clock;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.auth.UserRecord.Update;
import com.google.firebase.auth.internal.FirebaseTokenFactory;
import com.google.firebase.auth.internal.FirebaseTokenVerifier;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.GetTokenResult;
import com.google.firebase.internal.NonNull;
import com.google.firebase.tasks.Continuation;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.Tasks;

import java.util.Map;

/**
 * This class is the entry point for all server-side Firebase Authentication actions.
 *
 * <p>You can get an instance of FirebaseAuth via {@link FirebaseAuth#getInstance(FirebaseApp)} and
 * then use it to perform a variety of authentication-related operations, including generating
 * custom tokens for use by client-side code, verifying Firebase ID Tokens received from clients, or
 * creating new FirebaseApp instances that are scoped to a particular authentication UID.
 */
public class FirebaseAuth {

  /** A global, thread-safe Json Factory built using Gson. */
  private static final JsonFactory jsonFactory = new GsonFactory();

  private final FirebaseApp firebaseApp;
  private final GooglePublicKeysManager googlePublicKeysManager;
  private final Clock clock;
  private final FirebaseUserManager userManager;

  private FirebaseAuth(FirebaseApp firebaseApp) {
    this(firebaseApp, FirebaseTokenVerifier.DEFAULT_KEY_MANAGER, Clock.SYSTEM);
  }

  /**
   * Constructor for injecting a GooglePublicKeysManager, which is used to verify tokens are
   * correctly signed. This should only be used for testing to override the default key manager.
   */
  @VisibleForTesting
  FirebaseAuth(
      FirebaseApp firebaseApp, GooglePublicKeysManager googlePublicKeysManager, Clock clock) {
    this.firebaseApp = firebaseApp;
    this.googlePublicKeysManager = googlePublicKeysManager;
    this.clock = clock;
    this.userManager = new FirebaseUserManager(jsonFactory, Utils.getDefaultTransport());
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

  /**
   * Creates a Firebase Custom Token associated with the given UID. This token can then be provided
   * back to a client application for use with the signInWithCustomToken authentication API.
   *
   * @param uid The UID to store in the token. This identifies the user to other Firebase services
   *     (Firebase Database, Firebase Auth, etc.)
   * @return A {@link Task} which will complete successfully with the created Firebase Custom Token,
   *     or unsuccessfully with the failure Exception.
   */
  public Task<String> createCustomToken(String uid) {
    return createCustomToken(uid, null);
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
   * @return A {@link Task} which will complete successfully with the created Firebase Custom Token,
   *     or unsuccessfully with the failure Exception.
   */
  public Task<String> createCustomToken(
      final String uid, final Map<String, Object> developerClaims) {
    FirebaseCredential credential = ImplFirebaseTrampolines.getCredential(firebaseApp);
    if (!(credential instanceof FirebaseCredentials.CertCredential)) {
      return Tasks.forException(
          new FirebaseException(
              "Must initialize FirebaseApp with a certificate credential to call "
                  + "createCustomToken()"));
    }

    return ((FirebaseCredentials.CertCredential) credential)
        .getCertificate()
        .continueWith(
            new Continuation<GoogleCredential, String>() {
              @Override
              public String then(@NonNull Task<GoogleCredential> task) throws Exception {
                GoogleCredential baseCredential = task.getResult();
                FirebaseTokenFactory tokenFactory = FirebaseTokenFactory.getInstance();
                return tokenFactory.createSignedCustomAuthTokenForUser(
                    uid,
                    developerClaims,
                    baseCredential.getServiceAccountId(),
                    baseCredential.getServiceAccountPrivateKey());
              }
            });
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
   * <p>If the token is valid, the returned {@link Task} will complete successfully and provide a
   * parsed version of the token from which the UID and other claims in the token can be inspected.
   * If the token is invalid, the Task will fail with an exception indicating the failure.
   *
   * @param token A Firebase ID Token to verify and parse.
   * @return A {@link Task} which will complete successfully with the parsed token, or
   *     unsuccessfully with the failure Exception.
   */
  public Task<FirebaseToken> verifyIdToken(final String token) {
    FirebaseCredential credential = ImplFirebaseTrampolines.getCredential(firebaseApp);
    if (!(credential instanceof FirebaseCredentials.CertCredential)) {
      return Tasks.forException(
          new FirebaseException(
              "Must initialize FirebaseApp with a certificate credential to call "
                  + "verifyIdToken()"));
    }
    return ((FirebaseCredentials.CertCredential) credential)
        .getProjectId()
        .continueWith(
            new Continuation<String, FirebaseToken>() {
              @Override
              public FirebaseToken then(@NonNull Task<String> task) throws Exception {
                FirebaseTokenVerifier firebaseTokenVerifier =
                    new FirebaseTokenVerifier.Builder()
                        .setProjectId(task.getResult())
                        .setPublicKeysManager(googlePublicKeysManager)
                        .setClock(clock)
                        .build();
                FirebaseToken firebaseToken = FirebaseToken.parse(jsonFactory, token);

                // This will throw a FirebaseAuthException with details on how the token is invalid.
                firebaseTokenVerifier.verifyTokenAndSignature(firebaseToken.getToken());

                return firebaseToken;
              }
            });
  }

  /**
   * Gets the user data corresponding to the specified user ID.
   *
   * @param uid A user ID string.
   * @return A {@link Task} which will complete successfully with a {@link UserRecord} instance.
   *     If an error occurs while retrieving user data or if the specified user ID does not exist,
   *     the task fails with a FirebaseAuthException.
   * @throws IllegalArgumentException If the user ID string is null or empty.
   */
  public Task<UserRecord> getUser(final String uid) {
    checkArgument(!Strings.isNullOrEmpty(uid), "uid must not be null or empty");
    return ImplFirebaseTrampolines.getToken(firebaseApp, false).continueWith(
        new Continuation<GetTokenResult, UserRecord>() {
          @Override
          public UserRecord then(Task<GetTokenResult> task) throws Exception {
            return userManager.getUserById(uid, task.getResult().getToken());
          }
        });
  }

  /**
   * Gets the user data corresponding to the specified user email.
   *
   * @param email A user email address string.
   * @return A {@link Task} which will complete successfully with a {@link UserRecord} instance.
   *     If an error occurs while retrieving user data or if the email address does not correspond
   *     to a user, the task fails with a FirebaseAuthException.
   * @throws IllegalArgumentException If the email is null or empty.
   */
  public Task<UserRecord> getUserByEmail(final String email) {
    checkArgument(!Strings.isNullOrEmpty(email), "email must not be null or empty");
    return ImplFirebaseTrampolines.getToken(firebaseApp, false).continueWith(
        new Continuation<GetTokenResult, UserRecord>() {
          @Override
          public UserRecord then(Task<GetTokenResult> task) throws Exception {
            return userManager.getUserByEmail(email, task.getResult().getToken());
          }
        });
  }

  /**
   * Creates a new user account with the attributes contained in the specified
   * {@link UserRecord.NewUser}.
   *
   * @param user A non-null {@link UserRecord.NewUser} instance.
   * @return A {@link Task} which will complete successfully with a {@link UserRecord} instance
   *     corresponding to the newly created account. If an error occurs while creating the user
   *     account, the task fails with a FirebaseAuthException.
   * @throws NullPointerException if the provided user is null.
   */
  public Task<UserRecord> createUser(final UserRecord.NewUser user) {
    checkNotNull(user, "user must not be null");
    return ImplFirebaseTrampolines.getToken(firebaseApp, false).continueWith(
        new Continuation<GetTokenResult, UserRecord>() {
          @Override
          public UserRecord then(Task<GetTokenResult> task) throws Exception {
            String uid = userManager.createUser(user, task.getResult().getToken());
            return userManager.getUserById(uid, task.getResult().getToken());
          }
        });
  }

  /**
   * Updates an existing user account with the attributes contained in the specified
   * {@link Update}.
   *
   * @param update A non-null {@link Update} instance.
   * @return A {@link Task} which will complete successfully with a {@link UserRecord} instance
   *     corresponding to the updated user account. If an error occurs while updating the user
   *     account, the task fails with a FirebaseAuthException.
   * @throws NullPointerException if the provided update is null.
   */
  public Task<UserRecord> updateUser(final Update update) {
    checkNotNull(update, "update must not be null");
    return ImplFirebaseTrampolines.getToken(firebaseApp, false).continueWith(
        new Continuation<GetTokenResult, UserRecord>() {
          @Override
          public UserRecord then(Task<GetTokenResult> task) throws Exception {
            userManager.updateUser(update, task.getResult().getToken());
            return userManager.getUserById(update.getUid(), task.getResult().getToken());
          }
        });
  }

  /**
   * Deletes the user identified by the specified user ID.
   *
   * @param uid A user ID string.
   * @return A {@link Task} which will complete successfully when the specified user account has
   *     been deleted. If an error occurs while deleting the user account, the task fails with a
   *     FirebaseAuthException.
   * @throws IllegalArgumentException If the user ID string is null or empty.
   */
  public Task<Void> deleteUser(final String uid) {
    checkArgument(!Strings.isNullOrEmpty(uid), "uid must not be null or empty");
    return ImplFirebaseTrampolines.getToken(firebaseApp, false).continueWith(
        new Continuation<GetTokenResult, Void>() {
          @Override
          public Void then(Task<GetTokenResult> task) throws Exception {
            userManager.deleteUser(uid, task.getResult().getToken());
            return null;
          }
        });
  }

  private static final String SERVICE_ID = FirebaseAuth.class.getName();

  private static class FirebaseAuthService extends FirebaseService<FirebaseAuth> {

    FirebaseAuthService(FirebaseApp app) {
      super(SERVICE_ID, new FirebaseAuth(app));
    }

    @Override
    public void destroy() {
      // NOTE: We don't explicitly tear down anything here, but public methods of FirebaseAuth
      // will now fail because calls to getCredential() and getToken() will hit FirebaseApp,
      // which will throw once the app is deleted.
    }
  }
}
