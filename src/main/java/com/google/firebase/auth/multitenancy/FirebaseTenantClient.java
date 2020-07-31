/*
 * Copyright 2020 Google Inc.
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

package com.google.firebase.auth.multitenancy;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.internal.AuthHttpClient;
import com.google.firebase.auth.internal.ListTenantsResponse;
import com.google.firebase.internal.ApiClientUtils;
import com.google.firebase.internal.HttpRequestInfo;
import java.util.Map;

final class FirebaseTenantClient {

  static final int MAX_LIST_TENANTS_RESULTS = 100;

  private static final String ID_TOOLKIT_URL =
      "https://identitytoolkit.googleapis.com/%s/projects/%s";

  private final String tenantMgtBaseUrl;
  private final AuthHttpClient httpClient;

  FirebaseTenantClient(FirebaseApp app) {
    this(
        ImplFirebaseTrampolines.getProjectId(checkNotNull(app)),
        app.getOptions().getJsonFactory(),
        ApiClientUtils.newAuthorizedRequestFactory(app));
  }

  FirebaseTenantClient(
      String projectId, JsonFactory jsonFactory, HttpRequestFactory requestFactory) {
    checkArgument(!Strings.isNullOrEmpty(projectId),
        "Project ID is required to access the auth service. Use a service account credential or "
            + "set the project ID explicitly via FirebaseOptions. Alternatively you can also "
            + "set the project ID via the GOOGLE_CLOUD_PROJECT environment variable.");
    this.tenantMgtBaseUrl = String.format(ID_TOOLKIT_URL, "v2", projectId);
    this.httpClient = new AuthHttpClient(jsonFactory, requestFactory);
  }

  void setInterceptor(HttpResponseInterceptor interceptor) {
    httpClient.setInterceptor(interceptor);
  }

  Tenant getTenant(String tenantId) throws FirebaseAuthException {
    String url = tenantMgtBaseUrl + getTenantUrlSuffix(tenantId);
    return httpClient.sendRequest(HttpRequestInfo.buildGetRequest(url), Tenant.class);
  }

  Tenant createTenant(Tenant.CreateRequest request) throws FirebaseAuthException {
    String url = tenantMgtBaseUrl + "/tenants";
    return httpClient.sendRequest(
        HttpRequestInfo.buildJsonPostRequest(url, request.getProperties()),
        Tenant.class);
  }

  Tenant updateTenant(Tenant.UpdateRequest request) throws FirebaseAuthException {
    Map<String, Object> properties = request.getProperties();
    String url = tenantMgtBaseUrl + getTenantUrlSuffix(request.getTenantId());
    HttpRequestInfo requestInfo = HttpRequestInfo.buildJsonPatchRequest(url, properties)
        .addParameter("updateMask", Joiner.on(",").join(AuthHttpClient.generateMask(properties)));
    return httpClient.sendRequest(requestInfo, Tenant.class);
  }

  void deleteTenant(String tenantId) throws FirebaseAuthException {
    String url = tenantMgtBaseUrl + getTenantUrlSuffix(tenantId);
    httpClient.sendRequest(HttpRequestInfo.buildDeleteRequest(url), GenericJson.class);
  }

  ListTenantsResponse listTenants(int maxResults, String pageToken)
      throws FirebaseAuthException {
    ImmutableMap.Builder<String, Object> builder =
        ImmutableMap.<String, Object>builder().put("pageSize", maxResults);
    if (pageToken != null) {
      checkArgument(!pageToken.equals(
          ListTenantsPage.END_OF_LIST), "Invalid end of list page token.");
      builder.put("pageToken", pageToken);
    }

    HttpRequestInfo requestInfo = HttpRequestInfo.buildGetRequest(tenantMgtBaseUrl + "/tenants")
        .addAllParameters(builder.build());
    return httpClient.sendRequest(requestInfo, ListTenantsResponse.class);
  }

  private static String getTenantUrlSuffix(String tenantId) {
    checkArgument(!Strings.isNullOrEmpty(tenantId), "Tenant ID must not be null or empty.");
    return "/tenants/" + tenantId;
  }
}
