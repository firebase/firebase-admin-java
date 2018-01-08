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
import com.google.api.client.http.HttpResponseException;
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

import com.google.firebase.auth.internal.HttpErrorResponse;
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

  static final String USER_NOT_FOUND_ERROR = "user-not-found";
  static final String INTERNAL_ERROR = "internal-error";

  // Map of server-side error codes to SDK error codes.
  // SDK error codes defined at: https://firebase.google.com/docs/auth/admin/errors
  private static final Map<String, String> ERROR_CODES = ImmutableMap.<String, String>builder()
      .put("CLAIMS_TOO_LARGE", "claims-too-large")
      .put("CONFIGURATION_NOT_FOUND", "project-not-found")
      .put("INSUFFICIENT_PERMISSION", "insufficient-permission")
      .put("DUPLICATE_EMAIL", "email-already-exists")
      .put("DUPLICATE_LOCAL_ID", "uid-already-exists")
      .put("EMAIL_EXISTS", "email-already-exists")
      .put("INVALID_CLAIMS", "invalid-claims")
      .put("INVALID_EMAIL", "invalid-email")
      .put("INVALID_PAGE_SELECTION", "invalid-page-token")
      .put("INVALID_PHONE_NUMBER", "invalid-phone-number")
      .put("PHONE_NUMBER_EXISTS", "phone-number-already-exists")
      .put("PROJECT_NOT_FOUND", "project-not-found")
      .put("USER_NOT_FOUND", USER_NOT_FOUND_ERROR)
      .put("WEAK_PASSWORD", "invalid-password")
      .build();

  static final int MAX_LIST_USERS_RESULTS = 1000;

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
    GetAccountInfoResponse response = post(
        "getAccountInfo", payload, GetAccountInfoResponse.class);
    if (response == null || response.getUsers() == null || response.getUsers().isEmpty()) {
      throw new FirebaseAuthException(USER_NOT_FOUND_ERROR,
          "No user record found for the provided user ID: " + uid);
    }
    return new UserRecord(response.getUsers().get(0), jsonFactory);
  }

  UserRecord getUserByEmail(String email) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "email", ImmutableList.of(email));
    GetAccountInfoResponse response = post(
        "getAccountInfo", payload, GetAccountInfoResponse.class);
    if (response == null || response.getUsers() == null || response.getUsers().isEmpty()) {
      throw new FirebaseAuthException(USER_NOT_FOUND_ERROR,
          "No user record found for the provided email: " + email);
    }
    return new UserRecord(response.getUsers().get(0), jsonFactory);
  }

  UserRecord getUserByPhoneNumber(String phoneNumber) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "phoneNumber", ImmutableList.of(phoneNumber));
    GetAccountInfoResponse response = post(
        "getAccountInfo", payload, GetAccountInfoResponse.class);
    if (response == null || response.getUsers() == null || response.getUsers().isEmpty()) {
      throw new FirebaseAuthException(USER_NOT_FOUND_ERROR,
          "No user record found for the provided phone number: " + phoneNumber);
    }
    return new UserRecord(response.getUsers().get(0), jsonFactory);
  }

  String createUser(CreateRequest request) throws FirebaseAuthException {
    GenericJson response = post(
        "signupNewUser", request.getProperties(), GenericJson.class);
    if (response != null) {
      String uid = (String) response.get("localId");
      if (!Strings.isNullOrEmpty(uid)) {
        return uid;
      }
    }
    throw new FirebaseAuthException(INTERNAL_ERROR, "Failed to create new user");
  }

  void updateUser(UpdateRequest request, JsonFactory jsonFactory) throws FirebaseAuthException {
    GenericJson response = post(
        "setAccountInfo", request.getProperties(jsonFactory), GenericJson.class);
    if (response == null || !request.getUid().equals(response.get("localId"))) {
      throw new FirebaseAuthException(INTERNAL_ERROR, "Failed to update user: " + request.getUid());
    }
  }

  void deleteUser(String uid) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of("localId", uid);
    GenericJson response = post(
        "deleteAccount", payload, GenericJson.class);
    if (response == null || !response.containsKey("kind")) {
      throw new FirebaseAuthException(INTERNAL_ERROR, "Failed to delete user: " + uid);
    }
  }

  DownloadAccountResponse listUsers(int maxResults, String pageToken) throws FirebaseAuthException {
    ImmutableMap.Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
        .put("maxResults", maxResults);
    if (pageToken != null) {
      checkArgument(!pageToken.equals(ListUsersPage.END_OF_LIST), "invalid end of list page token");
      builder.put("nextPageToken", pageToken);
    }

    DownloadAccountResponse response = post(
        "downloadAccount", builder.build(), DownloadAccountResponse.class);
    if (response == null) {
      throw new FirebaseAuthException(INTERNAL_ERROR, "Failed to retrieve users.");
    }
    return response;
  }

  private <T> T post(String path, Object content, Class<T> clazz) throws FirebaseAuthException {
    checkArgument(!Strings.isNullOrEmpty(path), "path must not be null or empty");
    checkNotNull(content, "content must not be null");
    checkNotNull(clazz, "response class must not be null");

    GenericUrl url = new GenericUrl(ID_TOOLKIT_URL + path);
    HttpResponse response = null;
    try {
      HttpRequest request = requestFactory.buildPostRequest(url,
          new JsonHttpContent(jsonFactory, content));
      request.setParser(new JsonObjectParser(jsonFactory));
      request.getHeaders().set(CLIENT_VERSION_HEADER, clientVersion);
      request.setResponseInterceptor(interceptor);
      response = request.execute();
      return response.parseAs(clazz);
    } catch (HttpResponseException e) {
      // Server responded with an HTTP error
      handleHttpError(e);
      return null;
    } catch (IOException e) {
      // All other IO errors (Connection refused, reset, parse error etc.)
      throw new FirebaseAuthException(
          INTERNAL_ERROR, "Error while calling user management backend service", e);
    } finally {
      if (response != null) {
        try {
          response.disconnect();
        } catch (IOException ignored) {
          // Ignored
        }
      }
    }
  }

  private void handleHttpError(HttpResponseException e) throws FirebaseAuthException {
    try {
      HttpErrorResponse response = jsonFactory.fromString(e.getContent(), HttpErrorResponse.class);
      String code = ERROR_CODES.get(response.getErrorCode());
      if (code != null) {
        throw new FirebaseAuthException(code, "User management service responded with an error", e);
      }
    } catch (IOException ignored) {
      // Ignored
    }
    String msg = String.format(
        "Unexpected HTTP response with status: %d; body: %s", e.getStatusCode(), e.getContent());
    throw new FirebaseAuthException(INTERNAL_ERROR, msg, e);
  }
}
