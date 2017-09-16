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
import com.google.firebase.auth.FirebaseCredential;
import com.google.firebase.auth.GoogleOAuthAccessToken;
import com.google.firebase.tasks.Tasks;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutionException;

/**
 * An adapter for converting custom {@link FirebaseCredential} implementations into
 * GoogleCredentials.
 */
public final class FirebaseCredentialsAdapter extends GoogleCredentials {

  private final FirebaseCredential credential;

  public FirebaseCredentialsAdapter(FirebaseCredential credential) {
    this.credential = checkNotNull(credential);
  }

  @Override
  public AccessToken refreshAccessToken() throws IOException {
    try {
      GoogleOAuthAccessToken token = Tasks.await(credential.getAccessToken());
      return new AccessToken(token.getAccessToken(), new Date(token.getExpiryTime()));
    } catch (ExecutionException | InterruptedException e) {
      throw new IOException("Error while obtaining OAuth2 token", e);
    }
  }
}
