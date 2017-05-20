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

import static com.google.api.client.repackaged.com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.firebase.auth.User.NewAccount;
import com.google.firebase.auth.internal.GetAccountInfoResponse;
import com.google.firebase.auth.internal.GetUserByEmail;
import com.google.firebase.auth.internal.GetUserByUid;
import com.google.firebase.auth.internal.SignupNewUserResponse;
import com.google.firebase.internal.GetTokenResult;
import com.google.firebase.internal.JsonHttpClient;
import com.google.firebase.tasks.Continuation;
import com.google.firebase.tasks.Task;

public class FirebaseUserManager {

  private static final String ID_TOOLKIT_HOST = "www.googleapis.com";
  private static final String ID_TOOLKIT_PATH = "/identitytoolkit/v3/relyingparty/";

  private static final String ERROR_USER_NOT_FOUND = "user-not-found";
  private static final String ERROR_USER_SIGNUP_FAILED = "user-signup-failed";

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

  public Task<User> getUser(final String uid) {
    final GetUserByUid payload = new GetUserByUid(uid);
    return tokenSource.getToken().continueWith(new Continuation<GetTokenResult, User>() {
      @Override
      public User then(Task<GetTokenResult> task) throws Exception {
        GetAccountInfoResponse response = idToolKitClient.post("getAccountInfo",
            task.getResult().getToken(), payload, GetAccountInfoResponse.class);
        if (response.getUsers() == null || response.getUsers().isEmpty()) {
          throw new FirebaseAuthException(ERROR_USER_NOT_FOUND,
              "No user record found for the provider user ID: " + uid);
        }
        return response.getUsers().get(0);
      }
    });
  }

  public Task<User> getUserByEmail(final String email) {
    final GetUserByEmail payload = new GetUserByEmail(email);
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

  public Task<String> createUser(NewAccount account) {
    checkNotNull(account, "NewAccount argument must not be null");
    final User user = account.build();
    return tokenSource.getToken().continueWith(new Continuation<GetTokenResult, String>() {
      @Override
      public String then(Task<GetTokenResult> task) throws Exception {
        SignupNewUserResponse response = idToolKitClient.post("signupNewUser",
            task.getResult().getToken(), user, SignupNewUserResponse.class);
        String uid = response.getUid();
        if (uid == null || uid.isEmpty()) {
          throw new FirebaseAuthException(ERROR_USER_SIGNUP_FAILED, "Failed to create new user");
        }
        return uid;
      }
    });
  }

  interface TokenSource {
    Task<GetTokenResult> getToken();
  }

}
