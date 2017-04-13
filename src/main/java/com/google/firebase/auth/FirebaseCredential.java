package com.google.firebase.auth;

import com.google.firebase.tasks.Task;

/**
 * Provides Google OAuth2 access tokens used to authenticate with Firebase services. In most cases,
 * you will not need to implement this yourself and can instead use the default implementations
 * provided by {@link FirebaseCredentials}.
 */
public interface FirebaseCredential {

  /**
   * Returns a Google OAuth2 access token used to authenticate with Firebase services.
   *
   * @param forceRefresh Whether to fetch a new token or use a cached one if available.
   * @return A {@link Task} providing an access token.
   */
  Task<String> getAccessToken(boolean forceRefresh);
}
