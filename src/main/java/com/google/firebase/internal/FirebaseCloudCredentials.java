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

package com.google.firebase.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auth.Credentials;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.tasks.Continuation;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.Tasks;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * A Google cloud credential implementation that uses OAuth2 access tokens obtained from a
 * <code>FirebaseApp</code> to authenticate cloud API calls. This essentially acts as a bridge
 * between Firebase Admin SDK APIs and the Google cloud <code>Credentials</code> API.
 */
public final class FirebaseCloudCredentials extends Credentials {

  private final FirebaseApp app;

  public FirebaseCloudCredentials(FirebaseApp app) {
    this.app = checkNotNull(app);
  }

  @Override
  public String getAuthenticationType() {
    return "OAuth2";
  }

  @Override
  public Map<String, List<String>> getRequestMetadata(URI uri) throws IOException {
    Task<String> task = ImplFirebaseTrampolines.getToken(app, false).continueWith(
        new Continuation<GetTokenResult, String>() {
          @Override
          public String then(Task<GetTokenResult> task) throws Exception {
            return task.getResult().getToken();
          }
        });
    try {
      String authHeader = "Bearer " + Tasks.await(task);
      return ImmutableMap.<String, List<String>>of(
          "Authorization", ImmutableList.of(authHeader));
    } catch (ExecutionException | InterruptedException e) {
      throw new IOException("Failed to acquire an OAuth token", e);
    }
  }

  @Override
  public boolean hasRequestMetadata() {
    return true;
  }

  @Override
  public boolean hasRequestMetadataOnly() {
    return true;
  }

  @Override
  public void refresh() throws IOException {
    ImplFirebaseTrampolines.getToken(app, true);
  }

}
