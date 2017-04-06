package com.google.firebase.internal;

/**
 * An event listener for receiving authentication state change events (i.e. token renewals).
 */
public interface AuthStateListener {

  /**
   * Gets called when FirebaseApp fetches a new access token. The GetTokenResult encapsulates the
   * newly fetched token.
   */
  void onAuthStateChanged(GetTokenResult tokenResult);
}
