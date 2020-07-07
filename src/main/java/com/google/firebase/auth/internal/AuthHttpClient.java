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

package com.google.firebase.auth.internal;

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
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.internal.Nullable;
import com.google.firebase.internal.SdkUtils;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Provides a convenient API for making REST calls to the Firebase Auth backend servers.
 */
public final class AuthHttpClient {

  private static final String CLIENT_VERSION_HEADER = "X-Client-Version";

  private static final String CLIENT_VERSION = "Java/Admin/" + SdkUtils.getVersion();

  private static final String INTERNAL_ERROR = "internal-error";

  // Map of server-side error codes to SDK error codes.
  // SDK error codes defined at: https://firebase.google.com/docs/auth/admin/errors
  private static final Map<String, String> ERROR_CODES = ImmutableMap.<String, String>builder()
      .put("CLAIMS_TOO_LARGE", "claims-too-large")
      .put("CONFIGURATION_NOT_FOUND", "configuration-not-found")
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
      .put("USER_NOT_FOUND", "user-not-found")
      .put("WEAK_PASSWORD", "invalid-password")
      .put("UNAUTHORIZED_DOMAIN", "unauthorized-continue-uri")
      .put("INVALID_DYNAMIC_LINK_DOMAIN", "invalid-dynamic-link-domain")
      .put("TENANT_NOT_FOUND", "tenant-not-found")
      .build();

  private final JsonFactory jsonFactory;
  private final HttpRequestFactory requestFactory;

  private HttpResponseInterceptor interceptor;

  public AuthHttpClient(JsonFactory jsonFactory, HttpRequestFactory requestFactory) {
    this.jsonFactory = jsonFactory;
    this.requestFactory = requestFactory;
  }

  public static Set<String> generateMask(Map<String, Object> properties) {
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

  public void setInterceptor(HttpResponseInterceptor interceptor) {
    this.interceptor = interceptor;
  }

  public <T> T sendRequest(
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
      request.getHeaders().set(CLIENT_VERSION_HEADER, CLIENT_VERSION);
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
}
