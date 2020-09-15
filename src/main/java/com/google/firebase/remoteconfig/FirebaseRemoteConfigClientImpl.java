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

package com.google.firebase.remoteconfig;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.json.JsonFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.internal.AbstractPlatformErrorHandler;
import com.google.firebase.internal.ApiClientUtils;
import com.google.firebase.internal.ErrorHandlingHttpClient;
import com.google.firebase.internal.HttpRequestInfo;
import com.google.firebase.internal.SdkUtils;
import com.google.firebase.remoteconfig.internal.RemoteConfigServiceErrorResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A helper class for interacting with Firebase Remote Config service.
 */
final class FirebaseRemoteConfigClientImpl implements FirebaseRemoteConfigClient {

  private static final String REMOTE_CONFIG_URL = "https://firebaseremoteconfig.googleapis.com/v1/projects/%s/remoteConfig";

  private static final Map<String, String> COMMON_HEADERS =
          ImmutableMap.of(
                  "X-Firebase-Client", "fire-admin-java/" + SdkUtils.getVersion(),
                  // There is a known issue in which the ETag is not properly returned in cases
                  // where the request does not specify a compression type. Currently, it is
                  // required to include the header `Accept-Encoding: gzip` or equivalent in all
                  // requests. https://firebase.google.com/docs/remote-config/use-config-rest#etag_usage_and_forced_updates
                  "Accept-Encoding", "gzip"
          );

  private final String remoteConfigUrl;
  private final HttpRequestFactory requestFactory;
  private final JsonFactory jsonFactory;
  private final ErrorHandlingHttpClient<FirebaseRemoteConfigException> httpClient;

  private FirebaseRemoteConfigClientImpl(Builder builder) {
    checkArgument(!Strings.isNullOrEmpty(builder.projectId));
    this.remoteConfigUrl = String.format(REMOTE_CONFIG_URL, builder.projectId);
    this.requestFactory = checkNotNull(builder.requestFactory);
    this.jsonFactory = checkNotNull(builder.jsonFactory);
    HttpResponseInterceptor responseInterceptor = builder.responseInterceptor;
    RemoteConfigErrorHandler errorHandler = new RemoteConfigErrorHandler(this.jsonFactory);
    this.httpClient = new ErrorHandlingHttpClient<>(requestFactory, jsonFactory, errorHandler)
            .setInterceptor(responseInterceptor);
  }

  @VisibleForTesting
  String getRemoteConfigUrl() {
    return remoteConfigUrl;
  }

  @VisibleForTesting
  HttpRequestFactory getRequestFactory() {
    return requestFactory;
  }

  @VisibleForTesting
  JsonFactory getJsonFactory() {
    return jsonFactory;
  }

  @Override
  public RemoteConfigTemplate getTemplate() throws FirebaseRemoteConfigException {
    HttpRequestInfo request = HttpRequestInfo.buildGetRequest(remoteConfigUrl)
            .addAllHeaders(COMMON_HEADERS);
    IncomingHttpResponse response = httpClient.send(request);
    RemoteConfigTemplate parsed = httpClient.parse(response, RemoteConfigTemplate.class);
    parsed.setETag(getETag(response));
    return parsed;
  }

  private String getETag(IncomingHttpResponse response) {
    List<String> etagList = (List<String>) response.getHeaders().get("etag");
    checkState(etagList != null && !etagList.isEmpty(),
            "ETag header is not available in the server response.");

    String etag = etagList.get(0);
    checkState(!Strings.isNullOrEmpty(etag),
            "ETag header is not available in the server response.");

    return etag;
  }

  static FirebaseRemoteConfigClientImpl fromApp(FirebaseApp app) {
    String projectId = ImplFirebaseTrampolines.getProjectId(app);
    checkArgument(!Strings.isNullOrEmpty(projectId),
            "Project ID is required to access Remote Config service. Use a service "
                    + "account credential or set the project ID explicitly via FirebaseOptions. "
                    + "Alternatively you can also set the project ID via the GOOGLE_CLOUD_PROJECT "
                    + "environment variable.");
    return FirebaseRemoteConfigClientImpl.builder()
            .setProjectId(projectId)
            .setRequestFactory(ApiClientUtils.newAuthorizedRequestFactory(app))
            .setJsonFactory(app.getOptions().getJsonFactory())
            .build();
  }

  static Builder builder() {
    return new Builder();
  }

  static final class Builder {

    private String projectId;
    private HttpRequestFactory requestFactory;
    private JsonFactory jsonFactory;
    private HttpResponseInterceptor responseInterceptor;

    private Builder() { }

    Builder setProjectId(String projectId) {
      this.projectId = projectId;
      return this;
    }

    Builder setRequestFactory(HttpRequestFactory requestFactory) {
      this.requestFactory = requestFactory;
      return this;
    }

    Builder setJsonFactory(JsonFactory jsonFactory) {
      this.jsonFactory = jsonFactory;
      return this;
    }

    Builder setResponseInterceptor(
            HttpResponseInterceptor responseInterceptor) {
      this.responseInterceptor = responseInterceptor;
      return this;
    }

    FirebaseRemoteConfigClientImpl build() {
      return new FirebaseRemoteConfigClientImpl(this);
    }
  }

  private static class RemoteConfigErrorHandler
          extends AbstractPlatformErrorHandler<FirebaseRemoteConfigException> {

    private RemoteConfigErrorHandler(JsonFactory jsonFactory) {
      super(jsonFactory);
    }

    @Override
    protected FirebaseRemoteConfigException createException(FirebaseException base) {
      String response = getResponse(base);
      RemoteConfigServiceErrorResponse parsed = safeParse(response);
      return FirebaseRemoteConfigException.withRemoteConfigErrorCode(
              base, parsed.getRemoteConfigErrorCode());
    }

    private String getResponse(FirebaseException base) {
      if (base.getHttpResponse() == null) {
        return null;
      }

      return base.getHttpResponse().getContent();
    }

    private RemoteConfigServiceErrorResponse safeParse(String response) {
      if (!Strings.isNullOrEmpty(response)) {
        try {
          return jsonFactory.createJsonParser(response)
                  .parseAndClose(RemoteConfigServiceErrorResponse.class);
        } catch (IOException ignore) {
          // Ignore any error that may occur while parsing the error response. The server
          // may have responded with a non-json payload.
        }
      }

      return new RemoteConfigServiceErrorResponse();
    }
  }
}
