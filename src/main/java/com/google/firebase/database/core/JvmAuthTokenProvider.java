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
import java.util.concurrent.ScheduledExecutorService;

public class JvmAuthTokenProvider implements AuthTokenProvider {

  private final ScheduledExecutorService executorService;
  private final FirebaseApp firebaseApp;

  JvmAuthTokenProvider(FirebaseApp firebaseApp, ScheduledExecutorService executorService) {
    this.executorService = executorService;
    this.firebaseApp = firebaseApp;
  }

  private static String wrapOAuthToken(FirebaseApp firebaseApp, AccessToken result) {
    if (result == null) {
      // This shouldn't happen in the actual production SDK, but can happen in tests.
      return null;
    }

    Map<String, Object> authVariable = firebaseApp.getOptions().getDatabaseAuthVariableOverride();
    GAuthToken googleAuthToken = new GAuthToken(result.getTokenValue(), authVariable);
    return googleAuthToken.serializeToString();
  }

  @Override
  public void getToken(boolean forceRefresh, final GetTokenCompletionListener listener) {
    GoogleCredentials credentials = ImplFirebaseTrampolines.getCredentials(firebaseApp);
    try {
      if (forceRefresh) {
        credentials.refresh();
      } else {
        credentials.getRequestMetadata();
      }
      listener.onSuccess(wrapOAuthToken(firebaseApp, credentials.getAccessToken()));
    } catch (IOException e) {
      listener.onError(e.toString());
    }
  }

  @Override
  public void addTokenChangeListener(TokenChangeListener listener) {
    ImplFirebaseTrampolines.addCredentialsChangedListener(firebaseApp, wrap(listener));
  }

  private CredentialsChangedListener wrap(TokenChangeListener listener) {
    return new TokenChangeListenerWrapper(listener, firebaseApp, executorService);
  }

  /**
   * Wraps a TokenChangeListener instance inside a FirebaseApp.AuthStateListener. Equality
   * comparisons are delegated to the TokenChangeListener so that listener addition and removal will
   * work as expected in FirebaseApp.
   */
  private static class TokenChangeListenerWrapper implements CredentialsChangedListener {

    private final TokenChangeListener listener;
    private final FirebaseApp firebaseApp;
    private final ScheduledExecutorService executorService;

    TokenChangeListenerWrapper(
        TokenChangeListener listener,
        FirebaseApp firebaseApp,
        ScheduledExecutorService executorService) {
      this.listener = checkNotNull(listener, "Listener must not be null");
      this.firebaseApp = checkNotNull(firebaseApp, "FirebaseApp must not be null");
      this.executorService = checkNotNull(executorService, "ExecutorService must not be null");
    }

    @Override
    public void onChanged(OAuth2Credentials credentials) throws IOException {
      // Notify the TokenChangeListener on database's thread pool to make sure that
      // all database work happens on database worker threads.
      final AccessToken accessToken = credentials.getAccessToken();
      executorService.execute(
          new Runnable() {
            @Override
            public void run() {
              listener.onTokenChange(wrapOAuthToken(firebaseApp, accessToken));
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
