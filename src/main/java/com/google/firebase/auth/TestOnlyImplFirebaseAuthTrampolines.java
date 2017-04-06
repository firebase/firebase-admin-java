package com.google.firebase.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.Clock;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.Tasks;

import java.io.IOException;

/**
 * Provides trampolines into package-private Auth APIs used by components of Firebase
 *
 * <p>This class will not be compiled into the shipping library and can only be used in tests.
 *
 * @hide
 */
public final class TestOnlyImplFirebaseAuthTrampolines {

  private TestOnlyImplFirebaseAuthTrampolines() {}

  /* FirebaseApp */
  public static FirebaseToken.FirebaseTokenImpl getToken(FirebaseToken tokenHolder) {
    return tokenHolder.getToken();
  }

  /* FirebaseToken */
  public static FirebaseToken parseToken(JsonFactory jsonFactory, String tokenString)
      throws IOException {
    return FirebaseToken.parse(jsonFactory, tokenString);
  }

  /* FirebaseCredentials */
  public static Task<GoogleCredential> getCertificate(FirebaseCredential credential) {
    if (credential instanceof FirebaseCredentials.CertCredential) {
      return ((FirebaseCredentials.CertCredential) credential).getCertificate(false);
    } else {
      return Tasks.forException(new FirebaseException("Cannot convert to CertCredential"));
    }
  }

  /* FirebaseCredentials */
  public static Task<String> getProjectId(FirebaseCredential credential) {
    return ((FirebaseCredentials.CertCredential) credential).getProjectId(false);
  }

  /* FirebaseAuth */
  public static FirebaseAuth getFirebaseAuthInstance(
      FirebaseApp firebaseApp, GooglePublicKeysManager googlePublicKeysManager, Clock clock) {
    return new FirebaseAuth(firebaseApp, googlePublicKeysManager, clock);
  }
}
