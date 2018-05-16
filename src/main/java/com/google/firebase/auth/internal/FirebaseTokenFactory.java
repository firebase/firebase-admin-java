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

package com.google.firebase.auth.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.util.Base64;
import com.google.api.client.util.Clock;
import com.google.api.client.util.StringUtils;

import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.auth.internal.CryptoSigner.IAMCryptoSigner;
import com.google.firebase.auth.internal.CryptoSigner.ServiceAccountCryptoSigner;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Provides helper methods to simplify the creation of FirebaseCustomAuthTokens.
 *
 * <p>This class is designed to hide underlying implementation details from a Firebase developer.
 */
public class FirebaseTokenFactory {

  private final JsonFactory factory;
  private final Clock clock;
  private final CryptoSigner signer;

  public FirebaseTokenFactory(FirebaseApp app, Clock clock) throws IOException {
    this(app.getOptions().getJsonFactory(), clock, initSigner(app));
  }

  FirebaseTokenFactory(JsonFactory factory, Clock clock, CryptoSigner signer) {
    this.factory = checkNotNull(factory);
    this.clock = checkNotNull(clock);
    this.signer = checkNotNull(signer);
  }

  private static CryptoSigner initSigner(FirebaseApp app) throws IOException {
    GoogleCredentials credentials = ImplFirebaseTrampolines.getCredentials(app);
    if (credentials instanceof ServiceAccountSigner) {
      return new ServiceAccountCryptoSigner((ServiceAccountSigner) credentials);
    }
    return new IAMCryptoSigner(app);
  }

  public String createSignedCustomAuthTokenForUser(
      String uid, String issuer) throws IOException {
    return createSignedCustomAuthTokenForUser(uid, null, issuer);
  }

  public String createSignedCustomAuthTokenForUser(
      String uid, Map<String, Object> developerClaims, String issuer) throws IOException {
    checkState(signer != null, "Crypto signer not initialized. Make sure "
        + "to initialize the SDK with a service account credential, or at least specify a service "
        + "account ID with iam.serviceAccounts.signBlob permission that can be accessed remotely.");
    checkArgument(uid != null, "Uid must be provided.");
    checkArgument(issuer != null && !"".equals(issuer), "Must provide an issuer.");
    checkArgument(uid.length() <= 128, "Uid must be shorter than 128 characters.");

    JsonWebSignature.Header header = new JsonWebSignature.Header().setAlgorithm("RS256");

    long issuedAt = clock.currentTimeMillis() / 1000;
    FirebaseCustomAuthToken.Payload payload =
        new FirebaseCustomAuthToken.Payload()
            .setUid(uid)
            .setIssuer(issuer)
            .setSubject(issuer)
            .setAudience(FirebaseCustomAuthToken.FIREBASE_AUDIENCE)
            .setIssuedAtTimeSeconds(issuedAt)
            .setExpirationTimeSeconds(issuedAt + FirebaseCustomAuthToken.TOKEN_DURATION_SECONDS);

    if (developerClaims != null) {
      Collection<String> reservedNames = payload.getClassInfo().getNames();
      for (String key : developerClaims.keySet()) {
        if (reservedNames.contains(key)) {
          throw new IllegalArgumentException(
              String.format("developerClaims must not contain a reserved key: %s", key));
        }
      }
      GenericJson jsonObject = new GenericJson();
      jsonObject.putAll(developerClaims);
      payload.setDeveloperClaims(jsonObject);
    }
    return signPayload(header, payload);
  }

  private String signPayload(JsonWebSignature.Header header,
      FirebaseCustomAuthToken.Payload payload) throws IOException {
    String headerString = Base64.encodeBase64URLSafeString(factory.toByteArray(header));
    String payloadString = Base64.encodeBase64URLSafeString(factory.toByteArray(payload));
    String content = headerString + "." + payloadString;
    byte[] contentBytes = StringUtils.getBytesUtf8(content);
    String signature = Base64.encodeBase64URLSafeString(signer.sign(contentBytes));
    return content + "." + signature;
  }
}
