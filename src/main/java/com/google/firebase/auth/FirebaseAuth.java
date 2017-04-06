package com.google.firebase.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Clock;
import com.google.common.annotations.VisibleForTesting;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.auth.internal.FirebaseTokenFactory;
import com.google.firebase.auth.internal.FirebaseTokenVerifier;
import com.google.firebase.internal.NonNull;
import com.google.firebase.tasks.Continuation;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.Tasks;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>This class is the entry point for all server-side Firebase Authentication actions.</p>
 *
 * <p>You can get an instance of FirebaseAuth via {@link FirebaseAuth#getInstance(FirebaseApp)}
 * and then use it to perform a variety of authentication-related operations, including generating
 * custom tokens for use by client-side code, verifying Firebase ID Tokens received from clients,
 * or creating new FirebaseApp instances that are scoped to a particular authentication UID.</p>
 */
public class FirebaseAuth {

  /**
   * A global, thread-safe Json Factory built using Gson.
   */
  private static final JsonFactory jsonFactory = new GsonFactory();
  /**
   * A static map of FirebaseApp name to FirebaseAuth instance. To ensure thread-
   * safety, it should only be accessed in getInstance(), which is a synchronized method.
   */
  private static Map<String, FirebaseAuth> authInstances = new HashMap<>();
  private final FirebaseApp firebaseApp;
  private final GooglePublicKeysManager googlePublicKeysManager;
  private final Clock clock;

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
    if (!authInstances.containsKey(app.getName())) {
      authInstances.put(app.getName(), new FirebaseAuth(app));
    }

    return authInstances.get(app.getName());
  }

  /**
   * Creates a Firebase Custom Token associated with the given UID. This token can then be provided
   * back to a client application for use with the signInWithCustomToken authentication API.
   *
   * @param uid The UID to store in the token. This identifies the user to other Firebase services
   * (Firebase Database, Firebase Auth, etc.)
   * @return A {@link Task} which will complete successfully with the created Firebase Custom Token,
   * or unsuccessfully with the failure Exception.
   */
  public Task<String> createCustomToken(String uid) {
    return createCustomToken(uid, null);
  }

  /**
   * Creates a Firebase Custom Token associated with the given UID and additionally containing the
   * specified developerClaims. This token can then be provided back to a client application for
   * use with the signInWithCustomToken authentication API.
   *
   * @param uid The UID to store in the token. This identifies the user to other Firebase services
   * (Realtime Database, Storage, etc.). Should be less than 128 characters.
   * @param developerClaims Additional claims to be stored in the token (and made available to
   * security rules in Database, Storage, etc.). These must be able to be serialized to JSON (e.g.
   * contain only Maps, Arrays, Strings, Booleans, Numbers, etc.)
   * @return A {@link Task} which will complete successfully with the created Firebase Custom Token,
   * or unsuccessfully with the failure Exception.
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
        .getCertificate(false)
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
   * unsuccessfully with the failure Exception.
   */
  public Task<FirebaseToken> verifyIdToken(final String token) {
    FirebaseCredential credential = ImplFirebaseTrampolines.getCredential(firebaseApp);
    if (!(credential instanceof FirebaseCredentials.CertCredential)) {
      return Tasks.forException(
          new FirebaseException(
              "Must initialize FirebaseApp with a certificate credential to call verifyIdToken()"));
    }
    return ((FirebaseCredentials.CertCredential) credential)
        .getProjectId(false)
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
}
