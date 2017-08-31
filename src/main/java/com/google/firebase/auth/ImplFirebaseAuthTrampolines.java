package com.google.firebase.auth;

import com.google.firebase.auth.FirebaseCredentials.CertCredential;
import com.google.firebase.tasks.Tasks;
import java.util.concurrent.ExecutionException;

public class ImplFirebaseAuthTrampolines {

  /**
   * Extracts the project ID from the given FirebaseCredential. This is a temporary workaround
   * until we properly migrate to the GoogleCredentials API provided by cloud. That work is now
   * underway at https://github.com/firebase/firebase-admin-java/tree/hkj-credential-refactor.
   */
  public static String getProjectId(FirebaseCredential credential) {
    if (credential instanceof CertCredential) {
      try {
        return Tasks.await(((CertCredential) credential).getProjectId());
      } catch (ExecutionException | InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    return null;
  }

}
