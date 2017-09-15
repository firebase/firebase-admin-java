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

import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.Clock;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.auth.UserRecord.CreateRequest;
import com.google.firebase.auth.UserRecord.UpdateRequest;
import com.google.firebase.auth.internal.FirebaseTokenFactory;
import com.google.firebase.auth.internal.FirebaseTokenVerifier;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.NonNull;
import com.google.firebase.tasks.Continuation;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.Tasks;

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

  private final GoogleCredentials credentials;
  private final String projectId;
  private final GooglePublicKeysManager googlePublicKeysManager;
  private final Clock clock;
  private final JsonFactory jsonFactory;
  private final FirebaseUserManager userManager;
  private final AtomicBoolean destroyed;
  private final Object lock;

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
    this.credentials = ImplFirebaseTrampolines.getCredentials(firebaseApp);
    this.projectId = ImplFirebaseTrampolines.getProjectId(firebaseApp);
    this.googlePublicKeysManager = googlePublicKeysManager;
    this.clock = clock;
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
    checkNotDestroyed();
    checkState(credentials instanceof ServiceAccountCredentials,
        "Must initialize FirebaseApp with a service account credential to call "
            + "createCustomToken()");

    final ServiceAccountCredentials serviceAccount = (ServiceAccountCredentials) credentials;
    return Tasks.call(new Callable<String>() {
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
    checkNotDestroyed();
    checkState(!Strings.isNullOrEmpty(projectId),
        "Must initialize FirebaseApp with a project ID to call verifyIdToken()");
    return Tasks.forResult(projectId)
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
    checkNotDestroyed();
    checkArgument(!Strings.isNullOrEmpty(uid), "uid must not be null or empty");
    return Tasks.call(new Callable<UserRecord>() {
      @Override
      public UserRecord call() throws Exception {
        return userManager.getUserById(uid);
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
    checkNotDestroyed();
    checkArgument(!Strings.isNullOrEmpty(email), "email must not be null or empty");
    return Tasks.call(new Callable<UserRecord>() {
      @Override
      public UserRecord call() throws Exception {
        return userManager.getUserByEmail(email);
      }
    });
  }

  /**
   * Gets the user data corresponding to the specified user phone number.
   *
   * @param phoneNumber A user phone number string.
   * @return A {@link Task} which will complete successfully with a {@link UserRecord} instance.
   *     If an error occurs while retrieving user data or if the phone number does not
   *     correspond to a user, the task fails with a FirebaseAuthException.
   * @throws IllegalArgumentException If the phone number is null or empty.
   */
  public Task<UserRecord> getUserByPhoneNumber(final String phoneNumber) {
    checkNotDestroyed();
    checkArgument(!Strings.isNullOrEmpty(phoneNumber), "phone number must not be null or empty");
    return Tasks.call(new Callable<UserRecord>() {
      @Override
      public UserRecord call() throws Exception {
        return userManager.getUserByPhoneNumber(phoneNumber);
      }
    });
  }

  /**
   * Creates a new user account with the attributes contained in the specified
   * {@link CreateRequest}.
   *
   * @param request A non-null {@link CreateRequest} instance.
   * @return A {@link Task} which will complete successfully with a {@link UserRecord} instance
   *     corresponding to the newly created account. If an error occurs while creating the user
   *     account, the task fails with a FirebaseAuthException.
   * @throws NullPointerException if the provided request is null.
   */
  public Task<UserRecord> createUser(final CreateRequest request) {
    checkNotDestroyed();
    checkNotNull(request, "create request must not be null");
    return Tasks.call(new Callable<UserRecord>() {
      @Override
      public UserRecord call() throws Exception {
        String uid = userManager.createUser(request);
        return userManager.getUserById(uid);
      }
    });
  }

  /**
   * Updates an existing user account with the attributes contained in the specified
   * {@link UpdateRequest}.
   *
   * @param request A non-null {@link UpdateRequest} instance.
   * @return A {@link Task} which will complete successfully with a {@link UserRecord} instance
   *     corresponding to the updated user account. If an error occurs while updating the user
   *     account, the task fails with a FirebaseAuthException.
   * @throws NullPointerException if the provided update request is null.
   */
  public Task<UserRecord> updateUser(final UpdateRequest request) {
    checkNotDestroyed();
    checkNotNull(request, "update request must not be null");
    return Tasks.call(new Callable<UserRecord>() {
      @Override
      public UserRecord call() throws Exception {
        userManager.updateUser(request);
        return userManager.getUserById(request.getUid());
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
    checkNotDestroyed();
    checkArgument(!Strings.isNullOrEmpty(uid), "uid must not be null or empty");
    return Tasks.call(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        userManager.deleteUser(uid);
        return null;
      }
    });
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
