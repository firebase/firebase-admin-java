/*
 * Copyright 2021 Google Inc.
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

package com.google.firebase.database.integration;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.common.io.ByteStreams;
import com.google.firebase.FirebaseApp;
import com.google.firebase.internal.ApiClientUtils;
import java.io.IOException;
import java.io.InputStream;

/**
 * A simple HTTP client wrapper for updating RTDB security rules.
 */
final class RulesClient {

  private final String databaseUrl;
  private final HttpRequestFactory requestFactory;

  RulesClient() {
    this(FirebaseApp.getInstance());
  }

  RulesClient(FirebaseApp app) {
    this.databaseUrl = checkNotNull(app).getOptions().getDatabaseUrl();
    this.requestFactory = ApiClientUtils.newAuthorizedRequestFactory(
      app, /*retryConfig*/ null);
  }

  public void updateRules(String json) throws IOException {
    String url = this.databaseUrl + "/.settings/rules.json";
    HttpRequest request = requestFactory.buildPutRequest(
        new GenericUrl(url),
        ByteArrayContent.fromString("application/json", json));
    HttpResponse response = request.execute();
    try {
      InputStream in = response.getContent();
      if (in != null) {
        ByteStreams.exhaust(in);
      }
    } finally {
      ApiClientUtils.disconnectQuietly(response);
    }
  }
}
