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

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.auth.internal.GetAccountInfoResponse;
import com.google.firebase.internal.JsonHttpClient;

import java.io.IOException;
import java.util.Map;

class FirebaseUserManager {

  private static final String ID_TOOLKIT_HOST = "www.googleapis.com";
  private static final String ID_TOOLKIT_PATH = "/identitytoolkit/v3/relyingparty/";

  private static final String ERROR_USER_NOT_FOUND = "user-not-found";
  private static final String ERROR_USER_SIGNUP_FAILED = "user-signup-failed";
  private static final String ERROR_USER_UPDATE_FAILED = "user-update-failed";

  private final JsonHttpClient idToolKitClient;

  FirebaseUserManager(JsonFactory jsonFactory, HttpTransport transport) {
    this.idToolKitClient = new JsonHttpClient.Builder()
        .setHost(ID_TOOLKIT_HOST)
        .setPort(443)
        .setPath(ID_TOOLKIT_PATH)
        .setSecure(true)
        .setJsonFactory(jsonFactory)
        .setTransport(transport)
        .build();
  }

  User getUserById(String uid, String token) throws IOException, FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "localId", ImmutableList.of(uid));
    GetAccountInfoResponse response = idToolKitClient.post("getAccountInfo",
        token, payload, GetAccountInfoResponse.class);
    if (response.getUsers() == null || response.getUsers().isEmpty()) {
      throw new FirebaseAuthException(ERROR_USER_NOT_FOUND,
          "No user record found for the provided user ID: " + uid);
    }
    return response.getUsers().get(0);
  }

  User getUserByEmail(String email, String token) throws IOException, FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "email", ImmutableList.of(email));
    GetAccountInfoResponse response = idToolKitClient.post("getAccountInfo",
        token, payload, GetAccountInfoResponse.class);
    if (response.getUsers() == null || response.getUsers().isEmpty()) {
      throw new FirebaseAuthException(ERROR_USER_NOT_FOUND,
          "No user record found for the provided email: " + email);
    }
    return response.getUsers().get(0);
  }

  String createUser(User.Builder builder, String token) throws IOException, FirebaseAuthException {
    GenericJson response = idToolKitClient.post("signupNewUser",
        token, builder.build(), GenericJson.class);
    if (response != null) {
      String uid = (String) response.get("localId");
      if (!Strings.isNullOrEmpty(uid)) {
        return uid;
      }
    }
    throw new FirebaseAuthException(ERROR_USER_SIGNUP_FAILED, "Failed to create new user");
  }

  void updateUser(User.Updater updater, String token) throws IOException, FirebaseAuthException {
    GenericJson response = idToolKitClient.post("setAccountInfo",
        token, updater.update(), GenericJson.class);
    if (response == null || !updater.getUid().equals(response.get("localId"))) {
      throw new FirebaseAuthException(ERROR_USER_UPDATE_FAILED, "Failed to update user: "
          + updater.getUid());
    }
  }
}
