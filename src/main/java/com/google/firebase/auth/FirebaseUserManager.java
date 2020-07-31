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
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.auth.internal.AuthHttpClient;
import com.google.firebase.auth.internal.BatchDeleteResponse;
import com.google.firebase.auth.internal.DownloadAccountResponse;
import com.google.firebase.auth.internal.GetAccountInfoRequest;
import com.google.firebase.auth.internal.GetAccountInfoResponse;
import com.google.firebase.auth.internal.ListOidcProviderConfigsResponse;
import com.google.firebase.auth.internal.ListSamlProviderConfigsResponse;
import com.google.firebase.auth.internal.UploadAccountResponse;
import com.google.firebase.internal.ApiClientUtils;
import com.google.firebase.internal.HttpRequestInfo;
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
final class FirebaseUserManager {

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
    String projectId = builder.projectId;
    checkArgument(!Strings.isNullOrEmpty(projectId),
        "Project ID is required to access the auth service. Use a service account credential or "
            + "set the project ID explicitly via FirebaseOptions. Alternatively you can also "
            + "set the project ID via the GOOGLE_CLOUD_PROJECT environment variable.");
    this.jsonFactory = checkNotNull(builder.jsonFactory, "JsonFactory must not be null");
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

    this.httpClient = new AuthHttpClient(jsonFactory, builder.requestFactory);
  }

  @VisibleForTesting
  void setInterceptor(HttpResponseInterceptor interceptor) {
    httpClient.setInterceptor(interceptor);
  }

  UserRecord getUserById(String uid) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "localId", ImmutableList.of(uid));
    return lookupUserAccount(payload, "user ID: " + uid);
  }

  UserRecord getUserByEmail(String email) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "email", ImmutableList.of(email));
    return lookupUserAccount(payload, "email: " + email);
  }

  UserRecord getUserByPhoneNumber(String phoneNumber) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "phoneNumber", ImmutableList.of(phoneNumber));
    return lookupUserAccount(payload, "phone number: " + phoneNumber);
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
    Set<UserRecord> results = new HashSet<>();
    if (response.getUsers() != null) {
      for (GetAccountInfoResponse.User user : response.getUsers()) {
        results.add(new UserRecord(user, jsonFactory));
      }
    }
    return results;
  }

  String createUser(UserRecord.CreateRequest request) throws FirebaseAuthException {
    GenericJson response = post("/accounts", request.getProperties(), GenericJson.class);
    return (String) response.get("localId");
  }

  void updateUser(UserRecord.UpdateRequest request, JsonFactory jsonFactory)
      throws FirebaseAuthException {
    post("/accounts:update", request.getProperties(jsonFactory), GenericJson.class);
  }

  void deleteUser(String uid) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of("localId", uid);
    post("/accounts:delete", payload, GenericJson.class);
  }

  DeleteUsersResult deleteUsers(@NonNull List<String> uids) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.of(
        "localIds", uids,
        "force", true);
    BatchDeleteResponse response = post(
        "/accounts:batchDelete", payload, BatchDeleteResponse.class);
    return new DeleteUsersResult(uids.size(), response);
  }

  DownloadAccountResponse listUsers(int maxResults, String pageToken) throws FirebaseAuthException {
    ImmutableMap.Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
        .put("maxResults", maxResults);
    if (pageToken != null) {
      checkArgument(!pageToken.equals(ListUsersPage.END_OF_LIST), "invalid end of list page token");
      builder.put("nextPageToken", pageToken);
    }

    String url = userMgtBaseUrl + "/accounts:batchGet";
    HttpRequestInfo requestInfo = HttpRequestInfo.buildGetRequest(url)
        .addAllParameters(builder.build());
    return httpClient.sendRequest(requestInfo, DownloadAccountResponse.class);
  }

  UserImportResult importUsers(UserImportRequest request) throws FirebaseAuthException {
    checkNotNull(request);
    UploadAccountResponse response = post(
            "/accounts:batchCreate", request, UploadAccountResponse.class);
    return new UserImportResult(request.getUsersCount(), response);
  }

  String createSessionCookie(String idToken,
      SessionCookieOptions options) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "idToken", idToken, "validDuration", options.getExpiresInSeconds());
    GenericJson response = post(":createSessionCookie", payload, GenericJson.class);
    return (String) response.get("sessionCookie");
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
    return (String) response.get("oobLink");
  }

  private UserRecord lookupUserAccount(
      Map<String, Object> payload, String identifier) throws FirebaseAuthException {
    HttpRequestInfo requestInfo = HttpRequestInfo.buildJsonPostRequest(
        userMgtBaseUrl + "/accounts:lookup", payload);
    IncomingHttpResponse response = httpClient.sendRequest(requestInfo);
    GetAccountInfoResponse parsed = httpClient.parse(response, GetAccountInfoResponse.class);
    if (parsed.getUsers() == null || parsed.getUsers().isEmpty()) {
      throw new FirebaseAuthException(ErrorCode.NOT_FOUND,
          "No user record found for the provided " + identifier,
          null,
          response,
          AuthErrorCode.USER_NOT_FOUND);
    }

    return new UserRecord(parsed.getUsers().get(0), jsonFactory);
  }

  OidcProviderConfig createOidcProviderConfig(
      OidcProviderConfig.CreateRequest request) throws FirebaseAuthException {
    String url = idpConfigMgtBaseUrl + "/oauthIdpConfigs";
    HttpRequestInfo requestInfo = HttpRequestInfo.buildJsonPostRequest(url, request.getProperties())
        .addParameter("oauthIdpConfigId", request.getProviderId());
    return httpClient.sendRequest(requestInfo, OidcProviderConfig.class);
  }

  SamlProviderConfig createSamlProviderConfig(
      SamlProviderConfig.CreateRequest request) throws FirebaseAuthException {
    String url = idpConfigMgtBaseUrl + "/inboundSamlConfigs";
    HttpRequestInfo requestInfo = HttpRequestInfo.buildJsonPostRequest(url, request.getProperties())
        .addParameter("inboundSamlConfigId", request.getProviderId());
    return httpClient.sendRequest(requestInfo, SamlProviderConfig.class);
  }

  OidcProviderConfig updateOidcProviderConfig(OidcProviderConfig.UpdateRequest request)
      throws FirebaseAuthException {
    Map<String, Object> properties = request.getProperties();
    String url = idpConfigMgtBaseUrl + getOidcUrlSuffix(request.getProviderId());
    HttpRequestInfo requestInfo = HttpRequestInfo.buildJsonPatchRequest(url, properties)
        .addParameter("updateMask", Joiner.on(",").join(AuthHttpClient.generateMask(properties)));
    return httpClient.sendRequest(requestInfo, OidcProviderConfig.class);
  }

  SamlProviderConfig updateSamlProviderConfig(SamlProviderConfig.UpdateRequest request)
      throws FirebaseAuthException {
    Map<String, Object> properties = request.getProperties();
    String url = idpConfigMgtBaseUrl + getSamlUrlSuffix(request.getProviderId());
    HttpRequestInfo requestInfo = HttpRequestInfo.buildJsonPatchRequest(url, properties)
        .addParameter("updateMask", Joiner.on(",").join(AuthHttpClient.generateMask(properties)));
    return httpClient.sendRequest(requestInfo, SamlProviderConfig.class);
  }

  OidcProviderConfig getOidcProviderConfig(String providerId) throws FirebaseAuthException {
    String url = idpConfigMgtBaseUrl + getOidcUrlSuffix(providerId);
    return httpClient.sendRequest(HttpRequestInfo.buildGetRequest(url), OidcProviderConfig.class);
  }

  SamlProviderConfig getSamlProviderConfig(String providerId) throws FirebaseAuthException {
    String url = idpConfigMgtBaseUrl + getSamlUrlSuffix(providerId);
    return httpClient.sendRequest(HttpRequestInfo.buildGetRequest(url), SamlProviderConfig.class);
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

    String url = idpConfigMgtBaseUrl + "/oauthIdpConfigs";
    HttpRequestInfo requestInfo = HttpRequestInfo.buildGetRequest(url)
        .addAllParameters(builder.build());
    return httpClient.sendRequest(requestInfo, ListOidcProviderConfigsResponse.class);
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

    String url = idpConfigMgtBaseUrl + "/inboundSamlConfigs";
    HttpRequestInfo requestInfo = HttpRequestInfo.buildGetRequest(url)
        .addAllParameters(builder.build());
    return httpClient.sendRequest(requestInfo, ListSamlProviderConfigsResponse.class);
  }

  void deleteOidcProviderConfig(String providerId) throws FirebaseAuthException {
    String url = idpConfigMgtBaseUrl + getOidcUrlSuffix(providerId);
    httpClient.sendRequest(HttpRequestInfo.buildDeleteRequest(url));
  }

  void deleteSamlProviderConfig(String providerId) throws FirebaseAuthException {
    String url = idpConfigMgtBaseUrl + getSamlUrlSuffix(providerId);
    httpClient.sendRequest(HttpRequestInfo.buildDeleteRequest(url));
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
    String url = userMgtBaseUrl + path;
    return httpClient.sendRequest(HttpRequestInfo.buildJsonPostRequest(url, content), clazz);
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

  static FirebaseUserManager createUserManager(FirebaseApp app, String tenantId) {
    return FirebaseUserManager.builder()
        .setProjectId(ImplFirebaseTrampolines.getProjectId(app))
        .setTenantId(tenantId)
        .setHttpRequestFactory(ApiClientUtils.newAuthorizedRequestFactory(app))
        .setJsonFactory(app.getOptions().getJsonFactory())
        .build();
  }

  static Builder builder() {
    return new Builder();
  }

  static class Builder {

    private String projectId;
    private String tenantId;
    private HttpRequestFactory requestFactory;
    private JsonFactory jsonFactory;

    private Builder() { }

    public Builder setProjectId(String projectId) {
      this.projectId = projectId;
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

    public Builder setJsonFactory(JsonFactory jsonFactory) {
      this.jsonFactory = jsonFactory;
      return this;
    }

    FirebaseUserManager build() {
      return new FirebaseUserManager(this);
    }
  }
}
