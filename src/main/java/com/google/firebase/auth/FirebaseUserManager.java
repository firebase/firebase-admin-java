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
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.util.Key;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.internal.DownloadAccountResponse;
import com.google.firebase.auth.internal.GetAccountInfoResponse;
import com.google.firebase.auth.internal.HttpErrorResponse;
import com.google.firebase.auth.internal.ListTenantsResponse;
import com.google.firebase.auth.internal.UploadAccountResponse;
import com.google.firebase.internal.ApiClientUtils;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import com.google.firebase.internal.SdkUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * FirebaseUserManager provides methods for interacting with the Google Identity Toolkit via its
 * REST API. This class does not hold any mutable state, and is thread safe.
 *
 * <p>TODO(micahstairs): Rename this class to IdentityToolkitClient.
 *
 * @see <a href="https://developers.google.com/identity/toolkit/web/reference/relyingparty">
 *   Google Identity Toolkit</a>
 */
class FirebaseUserManager {

  static final String CONFIGURATION_NOT_FOUND = "configuration-not-found";
  static final String TENANT_ID_MISMATCH_ERROR = "tenant-id-mismatch";
  static final String TENANT_NOT_FOUND_ERROR = "tenant-not-found";
  static final String USER_NOT_FOUND_ERROR = "user-not-found";
  static final String INTERNAL_ERROR = "internal-error";

  // Map of server-side error codes to SDK error codes.
  // SDK error codes defined at: https://firebase.google.com/docs/auth/admin/errors
  private static final Map<String, String> ERROR_CODES = ImmutableMap.<String, String>builder()
      .put("CLAIMS_TOO_LARGE", "claims-too-large")
      .put("CONFIGURATION_NOT_FOUND", CONFIGURATION_NOT_FOUND)
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
      .put("TENANT_ID_MISMATCH", TENANT_ID_MISMATCH_ERROR)
      .put("TENANT_NOT_FOUND", TENANT_NOT_FOUND_ERROR)
      .put("USER_NOT_FOUND", USER_NOT_FOUND_ERROR)
      .put("WEAK_PASSWORD", "invalid-password")
      .put("UNAUTHORIZED_DOMAIN", "unauthorized-continue-uri")
      .put("INVALID_DYNAMIC_LINK_DOMAIN", "invalid-dynamic-link-domain")
      .build();

  static final int MAX_LIST_TENANTS_RESULTS = 1000;
  static final int MAX_LIST_USERS_RESULTS = 1000;
  static final int MAX_IMPORT_USERS = 1000;

  static final List<String> RESERVED_CLAIMS = ImmutableList.of(
      "amr", "at_hash", "aud", "auth_time", "azp", "cnf", "c_hash", "exp", "iat",
      "iss", "jti", "nbf", "nonce", "sub", "firebase");

  private static final String ID_TOOLKIT_URL =
      "https://identitytoolkit.googleapis.com/%s/projects/%s";
  private static final String CLIENT_VERSION_HEADER = "X-Client-Version";

  private final String userMgtBaseUrl;
  private final String idpConfigMgtBaseUrl;
  private final String tenantMgtBaseUrl;
  private final JsonFactory jsonFactory;
  private final HttpRequestFactory requestFactory;
  private final String clientVersion = "Java/Admin/" + SdkUtils.getVersion();

  private HttpResponseInterceptor interceptor;

  FirebaseUserManager(Builder builder) {
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
      this.idpConfigMgtBaseUrl = idToolkitUrlV2 + "/oauthIdpConfigs";
    } else {
      checkArgument(!tenantId.isEmpty(), "tenant ID must not be empty");
      this.userMgtBaseUrl = idToolkitUrlV1 + getTenantUrlSuffix(tenantId);
      this.idpConfigMgtBaseUrl = idToolkitUrlV2 + getTenantUrlSuffix(tenantId) + "/oauthIdpConfigs";
    }
    this.tenantMgtBaseUrl = idToolkitUrlV2;
    this.jsonFactory = app.getOptions().getJsonFactory();
    this.requestFactory = builder.requestFactory == null
      ? ApiClientUtils.newAuthorizedRequestFactory(app) : builder.requestFactory;
  }

  @VisibleForTesting
  void setInterceptor(HttpResponseInterceptor interceptor) {
    this.interceptor = interceptor;
  }

  UserRecord getUserById(String uid) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "localId", ImmutableList.of(uid));
    GetAccountInfoResponse response = post(
        "/accounts:lookup", payload, GetAccountInfoResponse.class);
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
        "/accounts:lookup", payload, GetAccountInfoResponse.class);
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
        "/accounts:lookup", payload, GetAccountInfoResponse.class);
    if (response == null || response.getUsers() == null || response.getUsers().isEmpty()) {
      throw new FirebaseAuthException(USER_NOT_FOUND_ERROR,
          "No user record found for the provided phone number: " + phoneNumber);
    }
    return new UserRecord(response.getUsers().get(0), jsonFactory);
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
    throw new FirebaseAuthException(INTERNAL_ERROR, "Failed to create new user");
  }

  void updateUser(UserRecord.UpdateRequest request, JsonFactory jsonFactory)
      throws FirebaseAuthException {
    GenericJson response = post(
        "/accounts:update", request.getProperties(jsonFactory), GenericJson.class);
    if (response == null || !request.getUid().equals(response.get("localId"))) {
      throw new FirebaseAuthException(INTERNAL_ERROR, "Failed to update user: " + request.getUid());
    }
  }

  void deleteUser(String uid) throws FirebaseAuthException {
    final Map<String, Object> payload = ImmutableMap.<String, Object>of("localId", uid);
    GenericJson response = post(
        "/accounts:delete", payload, GenericJson.class);
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

    GenericUrl url = new GenericUrl(userMgtBaseUrl + "/accounts:batchGet");
    url.putAll(builder.build());
    DownloadAccountResponse response = sendRequest(
            "GET", url, null, DownloadAccountResponse.class);
    if (response == null) {
      throw new FirebaseAuthException(INTERNAL_ERROR, "Failed to retrieve users.");
    }
    return response;
  }

  UserImportResult importUsers(UserImportRequest request) throws FirebaseAuthException {
    checkNotNull(request);
    UploadAccountResponse response = post(
            "/accounts:batchCreate", request, UploadAccountResponse.class);
    if (response == null) {
      throw new FirebaseAuthException(INTERNAL_ERROR, "Failed to import users.");
    }
    return new UserImportResult(request.getUsersCount(), response);
  }

  Tenant getTenant(String tenantId) throws FirebaseAuthException {
    GenericUrl url = new GenericUrl(tenantMgtBaseUrl + getTenantUrlSuffix(tenantId));
    return sendRequest("GET", url, null, Tenant.class);
  }

  Tenant createTenant(Tenant.CreateRequest request) throws FirebaseAuthException {
    GenericUrl url = new GenericUrl(tenantMgtBaseUrl + "/tenants");
    return sendRequest("POST", url, request.getProperties(), Tenant.class);
  }

  Tenant updateTenant(Tenant.UpdateRequest request) throws FirebaseAuthException {
    Map<String, Object> properties = request.getProperties();
    checkArgument(!properties.isEmpty(), "tenant update must have at least one property set");
    GenericUrl url = new GenericUrl(tenantMgtBaseUrl + getTenantUrlSuffix(request.getTenantId()));
    url.put("updateMask", generateMask(properties));
    return sendRequest("PATCH", url, properties, Tenant.class);
  }

  private static String generateMask(Map<String, Object> properties) {
    // This implementation does not currently handle the case of nested properties. This is fine
    // since we do not currently generate masks for any properties with nested values. When it
    // comes time to implement this, we can check if a property has nested properties by checking
    // if it is an instance of the Map class.
    return Joiner.on(",").join(ImmutableSortedSet.copyOf(properties.keySet()));
  }

  void deleteTenant(String tenantId) throws FirebaseAuthException {
    GenericUrl url = new GenericUrl(tenantMgtBaseUrl + getTenantUrlSuffix(tenantId));
    sendRequest("DELETE", url, null, GenericJson.class);
  }

  ListTenantsResponse listTenants(int maxResults, String pageToken)
      throws FirebaseAuthException {
    ImmutableMap.Builder<String, Object> builder =
        ImmutableMap.<String, Object>builder().put("pageSize", maxResults);
    if (pageToken != null) {
      checkArgument(!pageToken.equals(
          ListTenantsPage.END_OF_LIST), "invalid end of list page token");
      builder.put("pageToken", pageToken);
    }

    GenericUrl url = new GenericUrl(tenantMgtBaseUrl + "/tenants");
    url.putAll(builder.build());
    ListTenantsResponse response = sendRequest("GET", url, null, ListTenantsResponse.class);
    if (response == null) {
      throw new FirebaseAuthException(INTERNAL_ERROR, "Failed to retrieve tenants.");
    }
    return response;
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
    throw new FirebaseAuthException(INTERNAL_ERROR, "Failed to create session cookie");
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
    throw new FirebaseAuthException(INTERNAL_ERROR, "Failed to create email action link");
  }

  OidcProviderConfig createOidcProviderConfig(
      OidcProviderConfig.CreateRequest request) throws FirebaseAuthException {
    GenericUrl url = new GenericUrl(idpConfigMgtBaseUrl);
    String providerId = request.getProviderId();
    checkArgument(!Strings.isNullOrEmpty(providerId), "provider ID must not be null or empty");
    url.set("oauthIdpConfigId", providerId);
    return sendRequest("POST", url, request.getProperties(), OidcProviderConfig.class);
  }

  void deleteProviderConfig(String providerId) throws FirebaseAuthException {
    GenericUrl url = new GenericUrl(idpConfigMgtBaseUrl + "/" + providerId);
    sendRequest("DELETE", url, null, GenericJson.class);
  }

  private static String getTenantUrlSuffix(String tenantId) {
    checkArgument(!Strings.isNullOrEmpty(tenantId));
    return "/tenants/" + tenantId;
  }

  private <T> T post(String path, Object content, Class<T> clazz) throws FirebaseAuthException {
    checkArgument(!Strings.isNullOrEmpty(path), "path must not be null or empty");
    checkNotNull(content, "content must not be null for POST requests");
    GenericUrl url = new GenericUrl(userMgtBaseUrl + path);
    return sendRequest("POST", url, content, clazz);
  }

  private <T> T sendRequest(
          String method, GenericUrl url,
          @Nullable Object content, Class<T> clazz) throws FirebaseAuthException {

    checkArgument(!Strings.isNullOrEmpty(method), "method must not be null or empty");
    checkNotNull(url, "url must not be null");
    checkNotNull(clazz, "response class must not be null");
    HttpResponse response = null;
    try {
      HttpContent httpContent = content != null ? new JsonHttpContent(jsonFactory, content) : null;
      HttpRequest request =
          requestFactory.buildRequest(method.equals("PATCH") ? "POST" : method, url, httpContent);
      request.setParser(new JsonObjectParser(jsonFactory));
      request.getHeaders().set(CLIENT_VERSION_HEADER, clientVersion);
      if (method.equals("PATCH")) {
        request.getHeaders().set("X-HTTP-Method-Override", "PATCH");
      }
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
