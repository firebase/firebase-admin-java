package com.google.firebase.database.core;

public interface AuthTokenProvider {

  /**
   * Gets the token that should currently be used for authenticated requests.
   *
   * @param forceRefresh Pass true to get a new, up-to-date token rather than a (potentially
   *     expired) cached token.
   * @param listener Listener to be notified after operation completes.
   */
  void getToken(boolean forceRefresh, GetTokenCompletionListener listener);

  /**
   * Adds a TokenChangeListener to be notified of token changes.
   *
   * @param listener Listener to be added.
   */
  void addTokenChangeListener(TokenChangeListener listener);

  /**
   * Removes a previously-registered TokenChangeListener.
   *
   * @param listener Listener to be removed.
   */
  void removeTokenChangeListener(TokenChangeListener listener);

  interface GetTokenCompletionListener {

    /**
     * Called if the getToken operation completed successfully. Token may be null if there is no
     * auth state currently.
     */
    void onSuccess(String token);

    /**
     * Called if the getToken operation fails.
     *
     * <p>TODO: Figure out sane way to plumb errors through.
     */
    void onError(String error);
  }

  interface TokenChangeListener {

    /**
     * Called whenever an event happens that will affect the current auth token (e.g. user
     * logging in or out). Use {@link #getToken(boolean, GetTokenCompletionListener)} method to
     * get the updated token.
     */
    void onTokenChange(String token);

    // TODO(mikelehen): Remove this once AndroidAuthTokenProvider is updated to call
    // the other method.

    void onTokenChange();
  }
}
