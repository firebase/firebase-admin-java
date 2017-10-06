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

package com.google.firebase.database.core;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.auth.oauth2.OAuth2Credentials.CredentialsChangedListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.database.util.GAuthToken;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executor;

public class JvmAuthTokenProvider implements AuthTokenProvider {

  private final GoogleCredentials credentials;
  private final Map<String, Object> authVariable;
  private final Executor executor;

  JvmAuthTokenProvider(FirebaseApp firebaseApp, Executor executor) {
    this(firebaseApp, executor, true);
  }

  JvmAuthTokenProvider(FirebaseApp firebaseApp, Executor executor, boolean autoRefresh) {
    this.credentials = ImplFirebaseTrampolines.getCredentials(firebaseApp);
    this.authVariable = firebaseApp.getOptions().getDatabaseAuthVariableOverride();
    this.executor = executor;
    if (autoRefresh) {
      ImplFirebaseTrampolines.startTokenRefresher(firebaseApp);
    }
  }

  @Override
  public void getToken(boolean forceRefresh, final GetTokenCompletionListener listener) {
    try {
      if (forceRefresh) {
        credentials.refresh();
      }

      // The typical way to use a GoogleCredentials instance is to call its getRequestMetadata(),
      // and include the metadata in your request. Since we are accessing the token directly via
      // getAccessToken(), we must first call getRequestMetadata() to ensure the token is available
      // (refreshed if necessary).
      credentials.getRequestMetadata();

      AccessToken accessToken = credentials.getAccessToken();
      listener.onSuccess(wrapOAuthToken(accessToken, authVariable));
    } catch (Exception e) {
      listener.onError(e.toString());
    }
  }

  @Override
  public void addTokenChangeListener(TokenChangeListener listener) {
    CredentialsChangedListener listenerWrapper = new TokenChangeListenerWrapper(
        listener, executor, authVariable);
    credentials.addChangeListener(listenerWrapper);
  }

  private static String wrapOAuthToken(AccessToken result, Map<String, Object> authVariable) {
    if (result == null) {
      // This shouldn't happen in the actual production SDK, but can happen in tests.
      return null;
    }

    GAuthToken googleAuthToken = new GAuthToken(result.getTokenValue(), authVariable);
    return googleAuthToken.serializeToString();
  }

  /**
   * Wraps a TokenChangeListener instance inside a CredentialsChangedListener. Equality
   * comparisons are delegated to the TokenChangeListener so that listener addition and removal will
   * work as expected.
   */
  private static class TokenChangeListenerWrapper implements CredentialsChangedListener {

    private final TokenChangeListener listener;
    private final Executor executor;
    private final Map<String, Object> authVariable;

    TokenChangeListenerWrapper(
        TokenChangeListener listener,
        Executor executor,
        Map<String, Object> authVariable) {
      this.listener = checkNotNull(listener, "Listener must not be null");
      this.executor = checkNotNull(executor, "Executor must not be null");
      this.authVariable = authVariable;
    }

    @Override
    public void onChanged(OAuth2Credentials credentials) throws IOException {
      // When this event fires, it is guaranteed that credentials.getAccessToken() will return a
      // valid OAuth2 token.
      final AccessToken accessToken = credentials.getAccessToken();

      // Notify the TokenChangeListener on database's thread pool to make sure that
      // all database work happens on database worker threads.
      executor.execute(
          new Runnable() {
            @Override
            public void run() {
              listener.onTokenChange(wrapOAuthToken(accessToken, authVariable));
            }
          });
    }

    @Override
    public int hashCode() {
      return listener.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj != null
          && obj instanceof TokenChangeListenerWrapper
          && ((TokenChangeListenerWrapper) obj).listener.equals(listener);
    }
  }
}
