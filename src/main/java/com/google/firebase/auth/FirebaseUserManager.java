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

package com.google.firebase.auth;

import static com.google.api.client.repackaged.com.google.common.base.Preconditions.checkArgument;
import static com.google.api.client.repackaged.com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.auth.internal.GetAccountInfoResponse;
import com.google.firebase.auth.internal.SignupNewUserResponse;
import com.google.firebase.internal.GetTokenResult;
import com.google.firebase.internal.JsonHttpClient;
import com.google.firebase.tasks.Continuation;
import com.google.firebase.tasks.Task;

import java.io.IOException;
import java.util.Map;

public class FirebaseUserManager {

  private static final String ID_TOOLKIT_HOST = "www.googleapis.com";
  private static final String ID_TOOLKIT_PATH = "/identitytoolkit/v3/relyingparty/";

  private static final String ERROR_USER_NOT_FOUND = "user-not-found";
  private static final String ERROR_USER_SIGNUP_FAILED = "user-signup-failed";
  private static final String ERROR_USER_UPDATE_FAILED = "user-update-failed";

  private final JsonHttpClient idToolKitClient;
  private final TokenSource tokenSource;

  FirebaseUserManager(TokenSource tokenSource, JsonFactory jsonFactory, HttpTransport transport) {
    this.idToolKitClient = new JsonHttpClient.Builder()
        .setHost(ID_TOOLKIT_HOST)
        .setPort(443)
        .setPath(ID_TOOLKIT_PATH)
        .setSecure(true)
        .setJsonFactory(jsonFactory)
        .setTransport(transport)
        .build();
    this.tokenSource = tokenSource;
  }

  private User getUserById(String uid, String token) throws IOException, FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "localId", ImmutableList.of(uid));
    GetAccountInfoResponse response = idToolKitClient.post("getAccountInfo",
        token, payload, GetAccountInfoResponse.class);
    if (response.getUsers() == null || response.getUsers().isEmpty()) {
      throw new FirebaseAuthException(ERROR_USER_NOT_FOUND,
          "No user record found for the provider user ID: " + uid);
    }
    return response.getUsers().get(0);
  }

  public Task<User> getUser(final String uid) {
    checkArgument(!Strings.isNullOrEmpty(uid), "uid must not be null or empty");
    return tokenSource.getToken().continueWith(new Continuation<GetTokenResult, User>() {
      @Override
      public User then(Task<GetTokenResult> task) throws Exception {
        return getUserById(uid, task.getResult().getToken());
      }
    });
  }

  public Task<User> getUserByEmail(final String email) {
    checkArgument(!Strings.isNullOrEmpty(email), "email must not be null or empty");
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "email", ImmutableList.of(email));
    return tokenSource.getToken().continueWith(new Continuation<GetTokenResult, User>() {
      @Override
      public User then(Task<GetTokenResult> task) throws Exception {
        GetAccountInfoResponse response = idToolKitClient.post("getAccountInfo",
            task.getResult().getToken(), payload, GetAccountInfoResponse.class);
        if (response.getUsers() == null || response.getUsers().isEmpty()) {
          throw new FirebaseAuthException(ERROR_USER_NOT_FOUND,
              "No user record found for the provider email: " + email);
        }
        return response.getUsers().get(0);
      }
    });
  }

  public Task<String> createUser(final User.Builder builder) {
    checkNotNull(builder, "User.Builder argument must not be null");
    return tokenSource.getToken().continueWith(new Continuation<GetTokenResult, String>() {
      @Override
      public String then(Task<GetTokenResult> task) throws Exception {
        SignupNewUserResponse response = idToolKitClient.post("signupNewUser",
            task.getResult().getToken(), builder.build(), SignupNewUserResponse.class);
        String uid = response.getUid();
        if (uid == null || uid.isEmpty()) {
          throw new FirebaseAuthException(ERROR_USER_SIGNUP_FAILED, "Failed to create new user");
        }
        return uid;
      }
    });
  }

  public Task<User> updateUser(final User.Updater updater) {
    checkNotNull(updater, "User.Updater argument must not be null");
    return tokenSource.getToken().continueWith(new Continuation<GetTokenResult, User>() {
      @Override
      public User then(Task<GetTokenResult> task) throws Exception {
        GenericJson response = idToolKitClient.post("setAccountInfo",
            task.getResult().getToken(), updater.update(), GenericJson.class);
        if (response == null || !updater.getUid().equals(response.get("localId"))) {
          throw new FirebaseAuthException(ERROR_USER_UPDATE_FAILED, "Failed to update user: "
              + updater.getUid());
        }
        return getUserById(updater.getUid(), task.getResult().getToken());
      }
    });
  }

  interface TokenSource {
    Task<GetTokenResult> getToken();
  }

}
