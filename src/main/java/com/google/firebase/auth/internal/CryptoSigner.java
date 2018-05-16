/*
 * Copyright 2018 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.util.Key;
import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import com.google.firebase.FirebaseApp;
import com.google.firebase.internal.FirebaseRequestInitializer;
import java.io.IOException;
import java.util.Map;

interface CryptoSigner {

  byte[] sign(byte[] payload) throws IOException;

  class ServiceAccountCryptoSigner implements CryptoSigner {

    private final ServiceAccountSigner signer;

    ServiceAccountCryptoSigner(ServiceAccountSigner signer) {
      this.signer = checkNotNull(signer);
    }

    @Override
    public byte[] sign(byte[] payload) {
      return signer.sign(payload);
    }
  }

  class IAMCryptoSigner implements CryptoSigner {

    private static final String METADATA_SERVICE_URL =
        "http://metadata/computeMetadata/v1/instance/service-accounts/default/email";
    private static final String IAM_SIGN_BLOB_URL =
        "https://iam.googleapis.com/v1/projects/-/serviceAccounts/%s:signBlob";

    private final HttpRequestFactory requestFactory;
    private final JsonFactory jsonFactory;
    private final String serviceAccount;
    private final HttpResponseInterceptor interceptor;

    IAMCryptoSigner(FirebaseApp app) throws IOException {
      this(app, null);
    }

    IAMCryptoSigner(FirebaseApp app, HttpResponseInterceptor interceptor) throws IOException {
      this.requestFactory = app.getOptions().getHttpTransport().createRequestFactory(
          new FirebaseRequestInitializer(app));
      this.jsonFactory = app.getOptions().getJsonFactory();
      String serviceAccount = app.getOptions().getServiceAccount();
      if (!Strings.isNullOrEmpty(serviceAccount)) {
        this.serviceAccount = serviceAccount;
      } else {
        HttpRequest request = requestFactory.buildGetRequest(new GenericUrl(METADATA_SERVICE_URL));
        request.getHeaders().set("Metadata-Flavor", "Google");
        request.setResponseInterceptor(interceptor);
        HttpResponse response = request.execute();
        byte[] output = ByteStreams.toByteArray(response.getContent());
        this.serviceAccount = new String(output).trim();
      }
      this.interceptor = interceptor;
    }

    @Override
    public byte[] sign(byte[] payload) throws IOException {
      String encodedUrl = String.format(IAM_SIGN_BLOB_URL, serviceAccount);
      HttpResponse response = null;
      String encodedPayload = BaseEncoding.base64().encode(payload);
      Map<String, String> content = ImmutableMap.of("bytesToSign", encodedPayload);
      try {
        HttpRequest request = requestFactory.buildPostRequest(new GenericUrl(encodedUrl),
            new JsonHttpContent(jsonFactory, content));
        request.setParser(new JsonObjectParser(jsonFactory));
        request.setResponseInterceptor(interceptor);
        response = request.execute();
        SignBlobResponse parsed = response.parseAs(SignBlobResponse.class);
        return BaseEncoding.base64().decode(parsed.signature);
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
  }

  class SignBlobResponse {
    @Key("signature")
    private String signature;
  }
}


