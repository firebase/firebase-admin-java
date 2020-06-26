/*
 * Copyright 2020 Google LLC
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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.internal.HttpErrorResponse;
import com.google.firebase.auth.internal.ListTenantsResponse;
import com.google.firebase.auth.internal.ManagementClientUtils;
import com.google.firebase.internal.ApiClientUtils;
import com.google.firebase.internal.Nullable;
import com.google.firebase.internal.SdkUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * TenantManagementClient provides methods for interacting with the Google Identity Toolkit via its
 * REST API. This class does not hold any mutable state, and is thread safe.
 *
 * @see <a href="https://developers.google.com/identity/toolkit/web/reference/relyingparty">
 *   Google Identity Toolkit</a>
 */
class TenantManagementClient {

  private static final String ID_TOOLKIT_URL =
      "https://identitytoolkit.googleapis.com/v2/projects/%s";
  private static final String CLIENT_VERSION_HEADER = "X-Client-Version";

  private final String tenantMgtBaseUrl;
  private final JsonFactory jsonFactory;
  private final HttpRequestFactory requestFactory;
  private final String clientVersion = "Java/Admin/" + SdkUtils.getVersion();

  private HttpResponseInterceptor interceptor;

  TenantManagementClient(Builder builder) {
    FirebaseApp app = checkNotNull(builder.app, "FirebaseApp must not be null");
    String projectId = ImplFirebaseTrampolines.getProjectId(app);
    checkArgument(!Strings.isNullOrEmpty(projectId),
        "Project ID is required to access the auth service. Use a service account credential or "
            + "set the project ID explicitly via FirebaseOptions. Alternatively you can also "
            + "set the project ID via the GOOGLE_CLOUD_PROJECT environment variable.");
    final String tenantId = builder.tenantId;
    checkArgument(!Strings.isNullOrEmpty(tenantId), "Tenant ID must not be null or empty.");
    this.tenantMgtBaseUrl = String.format(ID_TOOLKIT_URL, projectId);
    this.jsonFactory = app.getOptions().getJsonFactory();
    this.requestFactory = builder.requestFactory == null
      ? ApiClientUtils.newAuthorizedRequestFactory(app) : builder.requestFactory;
  }

  @VisibleForTesting
  void setInterceptor(HttpResponseInterceptor interceptor) {
    this.interceptor = interceptor;
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
    GenericUrl url = new GenericUrl(tenantMgtBaseUrl + getTenantUrlSuffix(request.getTenantId()));
    url.put("updateMask", Joiner.on(",").join(generateMask(properties)));
    return sendRequest("PATCH", url, properties, Tenant.class);
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
          ListTenantsPage.END_OF_LIST), "Invalid end of list page token");
      builder.put("pageToken", pageToken);
    }

    GenericUrl url = new GenericUrl(tenantMgtBaseUrl + "/tenants");
    url.putAll(builder.build());
    ListTenantsResponse response = sendRequest("GET", url, null, ListTenantsResponse.class);
    if (response == null) {
      throw new FirebaseAuthException(
          ManagementClientUtils.INTERNAL_ERROR, "Failed to retrieve tenants.");
    }
    return response;
  }

  private static Set<String> generateMask(Map<String, Object> properties) {
    ImmutableSortedSet.Builder<String> maskBuilder = ImmutableSortedSet.naturalOrder();
    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      if (entry.getValue() instanceof Map) {
        Set<String> childMask = generateMask((Map<String, Object>) entry.getValue());
        for (String childProperty : childMask) {
          maskBuilder.add(entry.getKey() + "." + childProperty);
        }
      } else {
        maskBuilder.add(entry.getKey());
      }
    }
    return maskBuilder.build();
  }

  private static String getTenantUrlSuffix(String tenantId) {
    checkArgument(!Strings.isNullOrEmpty(tenantId), "Tenant ID must not be null or empty.");
    return "/tenants/" + tenantId;
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
          ManagementClientUtils.INTERNAL_ERROR,
          "Error while calling tenant management backend service",
          e);
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
      String code = ManagementClientUtils.ERROR_CODES.get(response.getErrorCode());
      if (code != null) {
        throw new FirebaseAuthException(
            code,
            "Tenant management service responded with an error",
            e);
      }
    } catch (IOException ignored) {
      // Ignored
    }
    String msg = String.format(
        "Unexpected HTTP response with status: %d; body: %s", e.getStatusCode(), e.getContent());
    throw new FirebaseAuthException(ManagementClientUtils.INTERNAL_ERROR, msg, e);
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

    TenantManagementClient build() {
      return new TenantManagementClient(this);
    }
  }
}
