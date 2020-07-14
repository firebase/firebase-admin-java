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
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.Key;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.auth.internal.AuthHttpClient;
import com.google.firebase.auth.internal.BatchDeleteResponse;
import com.google.firebase.auth.internal.DownloadAccountResponse;
import com.google.firebase.auth.internal.GetAccountInfoRequest;
import com.google.firebase.auth.internal.GetAccountInfoResponse;
import com.google.firebase.auth.internal.ListOidcProviderConfigsResponse;
import com.google.firebase.auth.internal.ListSamlProviderConfigsResponse;
import com.google.firebase.auth.internal.UploadAccountResponse;
import com.google.firebase.internal.ApiClientUtils;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FirebaseUserManager provides methods for interacting with the Google Identity Toolkit via its
 * REST API. This class does not hold any mutable state, and is thread safe.
 *
 * @see <a href="https://developers.google.com/identity/toolkit/web/reference/relyingparty">
 *   Google Identity Toolkit</a>
 */
class FirebaseUserManager {

  static final int MAX_LIST_PROVIDER_CONFIGS_RESULTS = 100;
  static final int MAX_GET_ACCOUNTS_BATCH_SIZE = 100;
  static final int MAX_DELETE_ACCOUNTS_BATCH_SIZE = 1000;
  static final int MAX_LIST_USERS_RESULTS = 1000;
  static final int MAX_IMPORT_USERS = 1000;

  static final List<String> RESERVED_CLAIMS = ImmutableList.of(
      "amr", "at_hash", "aud", "auth_time", "azp", "cnf", "c_hash", "exp", "iat",
      "iss", "jti", "nbf", "nonce", "sub", "firebase");

  private static final String ID_TOOLKIT_URL =
      "https://identitytoolkit.googleapis.com/%s/projects/%s";

  private final String userMgtBaseUrl;
  private final String idpConfigMgtBaseUrl;
  private final JsonFactory jsonFactory;
  private final AuthHttpClient httpClient;

  private FirebaseUserManager(Builder builder) {
    FirebaseApp app = checkNotNull(builder.app, "FirebaseApp must not be null");
    String projectId = ImplFirebaseTrampolines.getProjectId(app);
    checkArgument(!Strings.isNullOrEmpty(projectId),
        "Project ID is required to access the auth service. Use a service account credential or "
            + "set the project ID explicitly via FirebaseOptions. Alternatively you can also "
            + "set the project ID via the GOOGLE_CLOUD_PROJECT environment variable.");
    final String idToolkitUrlV1 = String.format(ID_TOOLKIT_URL, "v1", projectId);
    final String idToolkitUrlV2 = String.format(ID_TOOLKIT_URL, "v2", projectId);
    final String tenantId = builder.tenantId;
    if (tenantId == null) {
      this.userMgtBaseUrl = idToolkitUrlV1;
      this.idpConfigMgtBaseUrl = idToolkitUrlV2;
    } else {
      checkArgument(!tenantId.isEmpty(), "Tenant ID must not be empty.");
      this.userMgtBaseUrl = idToolkitUrlV1 + "/tenants/" + tenantId;
      this.idpConfigMgtBaseUrl = idToolkitUrlV2 + "/tenants/" + tenantId;
    }

    this.jsonFactory = app.getOptions().getJsonFactory();
    HttpRequestFactory requestFactory = builder.requestFactory == null
        ? ApiClientUtils.newAuthorizedRequestFactory(app) : builder.requestFactory;
    this.httpClient = new AuthHttpClient(jsonFactory, requestFactory);
  }

  @VisibleForTesting
  void setInterceptor(HttpResponseInterceptor interceptor) {
    httpClient.setInterceptor(interceptor);
  }

  UserRecord getUserById(String uid) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "localId", ImmutableList.of(uid));
    GetAccountInfoResponse response = post(
        "/accounts:lookup", payload, GetAccountInfoResponse.class);
    if (response == null || response.getUsers() == null || response.getUsers().isEmpty()) {
      throw new FirebaseAuthException(
          AuthHttpClient.USER_NOT_FOUND_ERROR,
          "No user record found for the provided user ID: " + uid);
    }
    return new UserRecord(response.getUsers().get(0), jsonFactory);
  }

  UserRecord getUserByEmail(String email) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "email", ImmutableList.of(email));
    GetAccountInfoResponse response = post(
        "/accounts:lookup", payload, GetAccountInfoResponse.class);
    if (response == null || response.getUsers() == null || response.getUsers().isEmpty()) {
      throw new FirebaseAuthException(
          AuthHttpClient.USER_NOT_FOUND_ERROR,
          "No user record found for the provided email: " + email);
    }
    return new UserRecord(response.getUsers().get(0), jsonFactory);
  }

  UserRecord getUserByPhoneNumber(String phoneNumber) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "phoneNumber", ImmutableList.of(phoneNumber));
    GetAccountInfoResponse response = post(
        "/accounts:lookup", payload, GetAccountInfoResponse.class);
    if (response == null || response.getUsers() == null || response.getUsers().isEmpty()) {
      throw new FirebaseAuthException(
          AuthHttpClient.USER_NOT_FOUND_ERROR,
          "No user record found for the provided phone number: " + phoneNumber);
    }
    return new UserRecord(response.getUsers().get(0), jsonFactory);
  }

  Set<UserRecord> getAccountInfo(@NonNull Collection<UserIdentifier> identifiers)
      throws FirebaseAuthException {
    if (identifiers.isEmpty()) {
      return new HashSet<>();
    }

    GetAccountInfoRequest payload = new GetAccountInfoRequest();
    for (UserIdentifier id : identifiers) {
      id.populate(payload);
    }

    GetAccountInfoResponse response = post(
        "/accounts:lookup", payload, GetAccountInfoResponse.class);

    if (response == null) {
      throw new FirebaseAuthException(
          AuthHttpClient.INTERNAL_ERROR, "Failed to parse server response");
    }

    Set<UserRecord> results = new HashSet<>();
    if (response.getUsers() != null) {
      for (GetAccountInfoResponse.User user : response.getUsers()) {
        results.add(new UserRecord(user, jsonFactory));
      }
    }
    return results;
  }

  String createUser(UserRecord.CreateRequest request) throws FirebaseAuthException {
    GenericJson response = post(
        "/accounts", request.getProperties(), GenericJson.class);
    if (response != null) {
      String uid = (String) response.get("localId");
      if (!Strings.isNullOrEmpty(uid)) {
        return uid;
      }
    }
    throw new FirebaseAuthException(AuthHttpClient.INTERNAL_ERROR, "Failed to create new user");
  }

  void updateUser(UserRecord.UpdateRequest request, JsonFactory jsonFactory)
      throws FirebaseAuthException {
    GenericJson response = post(
        "/accounts:update", request.getProperties(jsonFactory), GenericJson.class);
    if (response == null || !request.getUid().equals(response.get("localId"))) {
      throw new FirebaseAuthException(
          AuthHttpClient.INTERNAL_ERROR, "Failed to update user: " + request.getUid());
    }
  }

  void deleteUser(String uid) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of("localId", uid);
    GenericJson response = post(
        "/accounts:delete", payload, GenericJson.class);
    if (response == null || !response.containsKey("kind")) {
      throw new FirebaseAuthException(
          AuthHttpClient.INTERNAL_ERROR, "Failed to delete user: " + uid);
    }
  }

  /**
   * @pre uids != null
   * @pre uids.size() <= MAX_DELETE_ACCOUNTS_BATCH_SIZE
   */
  DeleteUsersResult deleteUsers(@NonNull List<String> uids) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.of(
        "localIds", uids,
        "force", true);
    BatchDeleteResponse response = post(
        "/accounts:batchDelete", payload, BatchDeleteResponse.class);
    if (response == null) {
      throw new FirebaseAuthException(AuthHttpClient.INTERNAL_ERROR, "Failed to delete users");
    }

    return new DeleteUsersResult(uids.size(), response);
  }

  DownloadAccountResponse listUsers(int maxResults, String pageToken) throws FirebaseAuthException {
    ImmutableMap.Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
        .put("maxResults", maxResults);
    if (pageToken != null) {
      checkArgument(!pageToken.equals(ListUsersPage.END_OF_LIST), "invalid end of list page token");
      builder.put("nextPageToken", pageToken);
    }

    GenericUrl url = new GenericUrl(userMgtBaseUrl + "/accounts:batchGet");
    url.putAll(builder.build());
    DownloadAccountResponse response = httpClient.sendRequest(
            "GET", url, null, DownloadAccountResponse.class);
    if (response == null) {
      throw new FirebaseAuthException(AuthHttpClient.INTERNAL_ERROR, "Failed to retrieve users.");
    }
    return response;
  }

  UserImportResult importUsers(UserImportRequest request) throws FirebaseAuthException {
    checkNotNull(request);
    UploadAccountResponse response = post(
            "/accounts:batchCreate", request, UploadAccountResponse.class);
    if (response == null) {
      throw new FirebaseAuthException(AuthHttpClient.INTERNAL_ERROR, "Failed to import users.");
    }
    return new UserImportResult(request.getUsersCount(), response);
  }

  String createSessionCookie(String idToken,
      SessionCookieOptions options) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "idToken", idToken, "validDuration", options.getExpiresInSeconds());
    GenericJson response = post(":createSessionCookie", payload, GenericJson.class);
    if (response != null) {
      String cookie = (String) response.get("sessionCookie");
      if (!Strings.isNullOrEmpty(cookie)) {
        return cookie;
      }
    }
    throw new FirebaseAuthException(
        AuthHttpClient.INTERNAL_ERROR, "Failed to create session cookie");
  }

  String getEmailActionLink(EmailLinkType type, String email,
      @Nullable ActionCodeSettings settings) throws FirebaseAuthException {
    ImmutableMap.Builder<String, Object> payload = ImmutableMap.<String, Object>builder()
            .put("requestType", type.name())
            .put("email", email)
            .put("returnOobLink", true);
    if (settings != null) {
      payload.putAll(settings.getProperties());
    }
    GenericJson response = post("/accounts:sendOobCode", payload.build(), GenericJson.class);
    if (response != null) {
      String link = (String) response.get("oobLink");
      if (!Strings.isNullOrEmpty(link)) {
        return link;
      }
    }
    throw new FirebaseAuthException(
        AuthHttpClient.INTERNAL_ERROR, "Failed to create email action link");
  }

  OidcProviderConfig createOidcProviderConfig(
      OidcProviderConfig.CreateRequest request) throws FirebaseAuthException {
    GenericUrl url = new GenericUrl(idpConfigMgtBaseUrl + "/oauthIdpConfigs");
    url.set("oauthIdpConfigId", request.getProviderId());
    return httpClient.sendRequest("POST", url, request.getProperties(), OidcProviderConfig.class);
  }

  SamlProviderConfig createSamlProviderConfig(
      SamlProviderConfig.CreateRequest request) throws FirebaseAuthException {
    GenericUrl url = new GenericUrl(idpConfigMgtBaseUrl + "/inboundSamlConfigs");
    url.set("inboundSamlConfigId", request.getProviderId());
    return httpClient.sendRequest("POST", url, request.getProperties(), SamlProviderConfig.class);
  }

  OidcProviderConfig updateOidcProviderConfig(OidcProviderConfig.UpdateRequest request)
      throws FirebaseAuthException {
    Map<String, Object> properties = request.getProperties();
    GenericUrl url =
        new GenericUrl(idpConfigMgtBaseUrl + getOidcUrlSuffix(request.getProviderId()));
    url.put("updateMask", Joiner.on(",").join(AuthHttpClient.generateMask(properties)));
    return httpClient.sendRequest("PATCH", url, properties, OidcProviderConfig.class);
  }

  SamlProviderConfig updateSamlProviderConfig(SamlProviderConfig.UpdateRequest request)
      throws FirebaseAuthException {
    Map<String, Object> properties = request.getProperties();
    GenericUrl url =
        new GenericUrl(idpConfigMgtBaseUrl + getSamlUrlSuffix(request.getProviderId()));
    url.put("updateMask", Joiner.on(",").join(AuthHttpClient.generateMask(properties)));
    return httpClient.sendRequest("PATCH", url, properties, SamlProviderConfig.class);
  }

  OidcProviderConfig getOidcProviderConfig(String providerId) throws FirebaseAuthException {
    GenericUrl url = new GenericUrl(idpConfigMgtBaseUrl + getOidcUrlSuffix(providerId));
    return httpClient.sendRequest("GET", url, null, OidcProviderConfig.class);
  }

  SamlProviderConfig getSamlProviderConfig(String providerId) throws FirebaseAuthException {
    GenericUrl url = new GenericUrl(idpConfigMgtBaseUrl + getSamlUrlSuffix(providerId));
    return httpClient.sendRequest("GET", url, null, SamlProviderConfig.class);
  }

  ListOidcProviderConfigsResponse listOidcProviderConfigs(int maxResults, String pageToken)
      throws FirebaseAuthException {
    ImmutableMap.Builder<String, Object> builder =
        ImmutableMap.<String, Object>builder().put("pageSize", maxResults);
    if (pageToken != null) {
      checkArgument(!pageToken.equals(
          ListProviderConfigsPage.END_OF_LIST), "Invalid end of list page token.");
      builder.put("nextPageToken", pageToken);
    }

    GenericUrl url = new GenericUrl(idpConfigMgtBaseUrl + "/oauthIdpConfigs");
    url.putAll(builder.build());
    ListOidcProviderConfigsResponse response =
        httpClient.sendRequest("GET", url, null, ListOidcProviderConfigsResponse.class);
    if (response == null) {
      throw new FirebaseAuthException(
          AuthHttpClient.INTERNAL_ERROR, "Failed to retrieve provider configs.");
    }
    return response;
  }

  ListSamlProviderConfigsResponse listSamlProviderConfigs(int maxResults, String pageToken)
      throws FirebaseAuthException {
    ImmutableMap.Builder<String, Object> builder =
        ImmutableMap.<String, Object>builder().put("pageSize", maxResults);
    if (pageToken != null) {
      checkArgument(!pageToken.equals(
          ListProviderConfigsPage.END_OF_LIST), "Invalid end of list page token.");
      builder.put("nextPageToken", pageToken);
    }

    GenericUrl url = new GenericUrl(idpConfigMgtBaseUrl + "/inboundSamlConfigs");
    url.putAll(builder.build());
    ListSamlProviderConfigsResponse response =
        httpClient.sendRequest("GET", url, null, ListSamlProviderConfigsResponse.class);
    if (response == null) {
      throw new FirebaseAuthException(
          AuthHttpClient.INTERNAL_ERROR, "Failed to retrieve provider configs.");
    }
    return response;
  }

  void deleteOidcProviderConfig(String providerId) throws FirebaseAuthException {
    GenericUrl url = new GenericUrl(idpConfigMgtBaseUrl + getOidcUrlSuffix(providerId));
    httpClient.sendRequest("DELETE", url, null, GenericJson.class);
  }

  void deleteSamlProviderConfig(String providerId) throws FirebaseAuthException {
    GenericUrl url = new GenericUrl(idpConfigMgtBaseUrl + getSamlUrlSuffix(providerId));
    httpClient.sendRequest("DELETE", url, null, GenericJson.class);
  }

  private static String getOidcUrlSuffix(String providerId) {
    checkArgument(!Strings.isNullOrEmpty(providerId), "Provider ID must not be null or empty.");
    return "/oauthIdpConfigs/" + providerId;
  }

  private static String getSamlUrlSuffix(String providerId) {
    checkArgument(!Strings.isNullOrEmpty(providerId), "Provider ID must not be null or empty.");
    return "/inboundSamlConfigs/" + providerId;
  }

  private <T> T post(String path, Object content, Class<T> clazz) throws FirebaseAuthException {
    checkArgument(!Strings.isNullOrEmpty(path), "path must not be null or empty");
    checkNotNull(content, "content must not be null for POST requests");
    GenericUrl url = new GenericUrl(userMgtBaseUrl + path);
    return httpClient.sendRequest("POST", url, content, clazz);
  }

  static class UserImportRequest extends GenericJson {

    @Key("users")
    private final List<Map<String, Object>> users;

    UserImportRequest(List<ImportUserRecord> users, UserImportOptions options,
        JsonFactory jsonFactory) {
      checkArgument(users != null && !users.isEmpty(), "users must not be null or empty");
      checkArgument(users.size() <= FirebaseUserManager.MAX_IMPORT_USERS,
          "users list must not contain more than %s items", FirebaseUserManager.MAX_IMPORT_USERS);

      boolean hasPassword = false;
      ImmutableList.Builder<Map<String, Object>> usersBuilder = ImmutableList.builder();
      for (ImportUserRecord user : users) {
        if (user.hasPassword()) {
          hasPassword = true;
        }
        usersBuilder.add(user.getProperties(jsonFactory));
      }
      this.users = usersBuilder.build();

      if (hasPassword) {
        checkArgument(options != null && options.getHash() != null,
            "UserImportHash option is required when at least one user has a password. Provide "
                + "a UserImportHash via UserImportOptions.withHash().");
        this.putAll(options.getProperties());
      }
    }

    int getUsersCount() {
      return users.size();
    }
  }

  enum EmailLinkType {
    VERIFY_EMAIL,
    EMAIL_SIGNIN,
    PASSWORD_RESET,
  }

  static Builder builder() {
    return new Builder();
  }

  static class Builder {

    private FirebaseApp app;
    private String tenantId;
    private HttpRequestFactory requestFactory;

    Builder setFirebaseApp(FirebaseApp app) {
      this.app = app;
      return this;
    }

    Builder setTenantId(String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    Builder setHttpRequestFactory(HttpRequestFactory requestFactory) {
      this.requestFactory = requestFactory;
      return this;
    }

    FirebaseUserManager build() {
      return new FirebaseUserManager(this);
    }
  }
}
