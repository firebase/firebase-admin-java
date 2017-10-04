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
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.auth.UserRecord.CreateRequest;
import com.google.firebase.auth.UserRecord.UpdateRequest;
import com.google.firebase.auth.internal.DownloadAccountResponse;
import com.google.firebase.auth.internal.GetAccountInfoResponse;

import com.google.firebase.internal.SdkUtils;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * FirebaseUserManager provides methods for interacting with the Google Identity Toolkit via its
 * REST API. This class does not hold any mutable state, and is thread safe.
 *
 * @see <a href="https://developers.google.com/identity/toolkit/web/reference/relyingparty">
 *   Google Identity Toolkit</a>
 */
class FirebaseUserManager {

  static final String USER_NOT_FOUND_ERROR = "USER_NOT_FOUND_ERROR";
  static final String USER_CREATE_ERROR = "USER_CREATE_ERROR";
  static final String USER_UPDATE_ERROR = "USER_UPDATE_ERROR";
  static final String USER_DELETE_ERROR = "USER_DELETE_ERROR";
  static final String LIST_USERS_ERROR = "LIST_USERS_ERROR";
  static final String INTERNAL_ERROR = "INTERNAL_ERROR";

  static final List<String> RESERVED_CLAIMS = ImmutableList.of(
      "amr", "at_hash", "aud", "auth_time", "azp", "cnf", "c_hash", "exp", "iat",
      "iss", "jti", "nbf", "nonce", "sub", "firebase");

  private static final String ID_TOOLKIT_URL =
      "https://www.googleapis.com/identitytoolkit/v3/relyingparty/";
  private static final String CLIENT_VERSION_HEADER = "X-Client-Version";

  private final JsonFactory jsonFactory;
  private final HttpRequestFactory requestFactory;
  private final String clientVersion = "Java/Admin/" + SdkUtils.getVersion();

  private HttpResponseInterceptor interceptor;

  /**
   * Creates a new FirebaseUserManager instance.
   *
   * @param jsonFactory JsonFactory instance used to transform Java objects into JSON and back.
   * @param transport HttpTransport used to make REST API calls.
   */
  FirebaseUserManager(JsonFactory jsonFactory, HttpTransport transport,
      GoogleCredentials credentials) {
    this.jsonFactory = checkNotNull(jsonFactory, "jsonFactory must not be null");
    this.requestFactory = transport.createRequestFactory(new HttpCredentialsAdapter(credentials));
  }

  @VisibleForTesting
  void setInterceptor(HttpResponseInterceptor interceptor) {
    this.interceptor = interceptor;
  }

  UserRecord getUserById(String uid) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "localId", ImmutableList.of(uid));
    GetAccountInfoResponse response;
    try {
      response = post("getAccountInfo", payload, GetAccountInfoResponse.class);
    } catch (IOException e) {
      throw new FirebaseAuthException(INTERNAL_ERROR,
          "IO error while retrieving user with ID: " + uid, e);
    }

    if (response == null || response.getUsers() == null || response.getUsers().isEmpty()) {
      throw new FirebaseAuthException(USER_NOT_FOUND_ERROR,
          "No user record found for the provided user ID: " + uid);
    }
    return new UserRecord(response.getUsers().get(0));
  }

  UserRecord getUserByEmail(String email) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "email", ImmutableList.of(email));
    GetAccountInfoResponse response;
    try {
      response = post("getAccountInfo", payload, GetAccountInfoResponse.class);
    } catch (IOException e) {
      throw new FirebaseAuthException(INTERNAL_ERROR,
          "IO error while retrieving user with email: " + email, e);
    }

    if (response == null || response.getUsers() == null || response.getUsers().isEmpty()) {
      throw new FirebaseAuthException(USER_NOT_FOUND_ERROR,
          "No user record found for the provided email: " + email);
    }
    return new UserRecord(response.getUsers().get(0));
  }

  UserRecord getUserByPhoneNumber(String phoneNumber) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "phoneNumber", ImmutableList.of(phoneNumber));
    GetAccountInfoResponse response;
    try {
      response = post("getAccountInfo", payload, GetAccountInfoResponse.class);
    } catch (IOException e) {
      throw new FirebaseAuthException(INTERNAL_ERROR,
          "IO error while retrieving user with phone number: " + phoneNumber, e);
    }

    if (response == null || response.getUsers() == null || response.getUsers().isEmpty()) {
      throw new FirebaseAuthException(USER_NOT_FOUND_ERROR,
          "No user record found for the provided phone number: " + phoneNumber);
    }
    return new UserRecord(response.getUsers().get(0));
  }

  String createUser(CreateRequest request) throws FirebaseAuthException {
    GenericJson response;
    try {
      response = post("signupNewUser", request.getProperties(), GenericJson.class);
    } catch (IOException e) {
      throw new FirebaseAuthException(USER_CREATE_ERROR,
          "IO error while creating user account", e);
    }

    if (response != null) {
      String uid = (String) response.get("localId");
      if (!Strings.isNullOrEmpty(uid)) {
        return uid;
      }
    }
    throw new FirebaseAuthException(USER_CREATE_ERROR, "Failed to create new user");
  }

  void updateUser(UpdateRequest request) throws FirebaseAuthException {
    GenericJson response;
    try {
      response = post("setAccountInfo", request.getProperties(), GenericJson.class);
    } catch (IOException e) {
      throw new FirebaseAuthException(USER_UPDATE_ERROR,
          "IO error while updating user: " + request.getUid(), e);
    }

    if (response == null || !request.getUid().equals(response.get("localId"))) {
      throw new FirebaseAuthException(USER_UPDATE_ERROR,
          "Failed to update user: " + request.getUid());
    }
  }

  void deleteUser(String uid) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of("localId", uid);
    GenericJson response;
    try {
      response = post("deleteAccount", payload, GenericJson.class);
    } catch (IOException e) {
      throw new FirebaseAuthException(USER_DELETE_ERROR,
          "IO error while deleting user: " + uid, e);
    }
    if (response == null || !response.containsKey("kind")) {
      throw new FirebaseAuthException(USER_DELETE_ERROR,
          "Failed to delete user: " + uid);
    }
  }

  UserAccountDownloader newDownloader() {
    return new RestUserAccountDownloader(this);
  }

  private <T> T post(String path, Object content, Class<T> clazz) throws IOException {
    checkArgument(!Strings.isNullOrEmpty(path), "path must not be null or empty");
    checkNotNull(content, "content must not be null");
    checkNotNull(clazz, "response class must not be null");

    GenericUrl url = new GenericUrl(ID_TOOLKIT_URL + path);
    HttpRequest request = requestFactory.buildPostRequest(url,
        new JsonHttpContent(jsonFactory, content));
    request.setParser(new JsonObjectParser(jsonFactory));
    request.getHeaders().set(CLIENT_VERSION_HEADER, clientVersion);
    request.setResponseInterceptor(interceptor);
    HttpResponse response = request.execute();
    try {
      return response.parseAs(clazz);
    } finally {
      response.disconnect();
    }
  }

  static class PageToken {
    private final String tokenString;

    PageToken(String tokenString) {
      if (tokenString != null) {
        checkArgument(!"".equals(tokenString), "Page token must not be empty");
      }
      this.tokenString = tokenString;
    }

    boolean isEndOfList() {
      return this.tokenString == null;
    }
  }

  interface UserAccountDownloader {
    DownloadAccountResponse download(int maxResults, PageToken pageToken) throws Exception;
  }

  static class RestUserAccountDownloader implements UserAccountDownloader {
    private final FirebaseUserManager userManager;

    private RestUserAccountDownloader(FirebaseUserManager userManager) {
      this.userManager = userManager;
    }

    public DownloadAccountResponse download(
        int maxResults, PageToken pageToken) throws FirebaseAuthException {
      ImmutableMap.Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
          .put("maxResults", maxResults);
      if (pageToken != null) {
        builder.put("nextPageToken", pageToken.tokenString);
      }

      final Map<String, Object> payload = builder.build();
      DownloadAccountResponse response;
      try {
        response = userManager.post("downloadAccount", payload, DownloadAccountResponse.class);
      } catch (IOException e) {
        throw new FirebaseAuthException(LIST_USERS_ERROR,
            "IO error while downloading user accounts.", e);
      }
      if (response == null) {
        throw new FirebaseAuthException(LIST_USERS_ERROR,
            "Unexpected response from download user account API.");
      }
      return response;
    }
  }
}
