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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.auth.internal.GetAccountInfoResponse;

import java.io.IOException;
import java.util.Map;

class FirebaseUserManager {

  private static final String ID_TOOLKIT_URL =
      "https://www.googleapis.com/identitytoolkit/v3/relyingparty/";

  private final JsonFactory jsonFactory;
  private final HttpRequestFactory requestFactory;

  FirebaseUserManager(JsonFactory jsonFactory, HttpTransport transport) {
    this.jsonFactory = checkNotNull(jsonFactory, "jsonFactory must not be null");
    this.requestFactory = transport.createRequestFactory();
  }

  User getUserById(String uid, String token) throws IOException, FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "localId", ImmutableList.of(uid));
    GetAccountInfoResponse response = post("getAccountInfo", token, payload,
        GetAccountInfoResponse.class);
    if (response.getUsers() == null || response.getUsers().isEmpty()) {
      throw new FirebaseAuthException(FirebaseAuth.ERROR_USER_NOT_FOUND,
          "No user record found for the provided user ID: " + uid);
    }
    return new User(response.getUsers().get(0));
  }

  User getUserByEmail(String email, String token) throws IOException, FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "email", ImmutableList.of(email));
    GetAccountInfoResponse response = post("getAccountInfo", token, payload,
        GetAccountInfoResponse.class);
    if (response.getUsers() == null || response.getUsers().isEmpty()) {
      throw new FirebaseAuthException(FirebaseAuth.ERROR_USER_NOT_FOUND,
          "No user record found for the provided email: " + email);
    }
    return new User(response.getUsers().get(0));
  }

  String createUser(User.Builder builder, String token) throws IOException, FirebaseAuthException {
    GenericJson response = post("signupNewUser", token, builder.build(), GenericJson.class);
    if (response != null) {
      String uid = (String) response.get("localId");
      if (!Strings.isNullOrEmpty(uid)) {
        return uid;
      }
    }
    throw new FirebaseAuthException(FirebaseAuth.ERROR_USER_SIGNUP_FAILED,
        "Failed to create new user");
  }

  void updateUser(User.Updater updater, String token) throws IOException, FirebaseAuthException {
    GenericJson response = post("setAccountInfo", token, updater.update(), GenericJson.class);
    if (response == null || !updater.getUid().equals(response.get("localId"))) {
      throw new FirebaseAuthException(FirebaseAuth.ERROR_USER_UPDATE_FAILED,
          "Failed to update user: " + updater.getUid());
    }
  }

  void deleteUser(String uid, String token) throws IOException, FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of("localId", uid);
    GenericJson response = post("deleteAccount", token, payload, GenericJson.class);
    if (response == null || !response.containsKey("kind")) {
      throw new FirebaseAuthException(FirebaseAuth.ERROR_USER_DELETE_FAILED,
          "Failed to delete user: " + uid);
    }
  }

  private <T> T post(String path, String token, Object content, Class<T> clazz) throws IOException {
    checkArgument(!Strings.isNullOrEmpty(path), "path must not be null or empty");
    checkArgument(!Strings.isNullOrEmpty(token), "OAuth token must not be null or empty");
    checkNotNull(content, "content must not be null");
    checkNotNull(clazz, "response class must not be null");

    GenericUrl url = new GenericUrl(ID_TOOLKIT_URL + path);
    HttpRequest request = requestFactory.buildPostRequest(url,
        new JsonHttpContent(jsonFactory, content));
    request.setParser(new JsonObjectParser(jsonFactory));
    request.getHeaders().setAuthorization("Bearer " + token);
    HttpResponse response = request.execute();
    try {
      return response.parseAs(clazz);
    } finally {
      response.disconnect();
    }
  }
}
