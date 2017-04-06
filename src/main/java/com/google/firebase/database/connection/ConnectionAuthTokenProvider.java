package com.google.firebase.database.connection;

public interface ConnectionAuthTokenProvider {

  /**
   * Gets the token that should currently be used for authenticated requests.
   *
   * @param forceRefresh Pass true to get a new, up-to-date token rather than a (potentially
   * expired) cached token.
   * @param callback Callback to be notified after operation completes.
   */
  void getToken(boolean forceRefresh, GetTokenCallback callback);

  interface GetTokenCallback {

    /**
     * Called if the getToken operation completed successfully.  Token may be null
     * if there is no auth state currently.
     */
    void onSuccess(String token);

    /**
     * Called if the getToken operation fails.
     *
     * TODO: Figure out sane way to plumb errors through.
     */
    void onError(String error);
  }
}
