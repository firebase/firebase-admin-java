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

package com.google.firebase.auth.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.FirebaseCredential;
import com.google.firebase.auth.GoogleOAuthAccessToken;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.Tasks;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Internal base class for built-in FirebaseCredential implementations.
 */
public abstract class BaseCredential implements FirebaseCredential {

  public static final List<String> FIREBASE_SCOPES =
      ImmutableList.of(
          // Enables access to Firebase Realtime Database.
          "https://www.googleapis.com/auth/firebase.database",

          // Enables access to the email address associated with a project.
          "https://www.googleapis.com/auth/userinfo.email",

          // Enables access to Google Identity Toolkit (for user management APIs).
          "https://www.googleapis.com/auth/identitytoolkit",

          // Enables access to Google Cloud Storage.
          "https://www.googleapis.com/auth/devstorage.full_control");

  private final GoogleCredentials googleCredentials;

  public BaseCredential(GoogleCredentials googleCredentials) {
    this.googleCredentials = checkNotNull(googleCredentials).createScoped(FIREBASE_SCOPES);
  }

  public final GoogleCredentials getGoogleCredentials() {
    return googleCredentials;
  }

  @Override
  public Task<GoogleOAuthAccessToken> getAccessToken() {
    return Tasks.call(new Callable<GoogleOAuthAccessToken>() {
      @Override
      public GoogleOAuthAccessToken call() throws Exception {
        AccessToken accessToken = googleCredentials.refreshAccessToken();
        return new GoogleOAuthAccessToken(accessToken.getTokenValue(),
            accessToken.getExpirationTime().getTime());
      }
    });
  }

}
