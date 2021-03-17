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

import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.util.Base64;
import com.google.api.client.util.Clock;
import com.google.api.client.util.StringUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.internal.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Provides helper methods to simplify the creation of Firebase custom auth tokens.
 *
 * <p>This class is designed to hide underlying implementation details from a Firebase developer.
 */
public class FirebaseTokenFactory {

  private final JsonFactory jsonFactory;
  private final Clock clock;
  private final CryptoSigner signer;
  private final String tenantId;

  public FirebaseTokenFactory(
      JsonFactory jsonFactory, Clock clock, CryptoSigner signer, @Nullable String tenantId) {
    this.jsonFactory = checkNotNull(jsonFactory);
    this.clock = checkNotNull(clock);
    this.signer = checkNotNull(signer);
    this.tenantId = tenantId;
  }

  @VisibleForTesting
  FirebaseTokenFactory(JsonFactory jsonFactory, Clock clock, CryptoSigner signer) {
    this(jsonFactory, clock, signer, null);
  }

  String createSignedCustomAuthTokenForUser(String uid) throws FirebaseAuthException {
    return createSignedCustomAuthTokenForUser(uid, null);
  }

  public String createSignedCustomAuthTokenForUser(
      String uid, Map<String, Object> developerClaims) throws FirebaseAuthException {
    checkArgument(!Strings.isNullOrEmpty(uid), "Uid must be provided.");
    checkArgument(uid.length() <= 128, "Uid must be shorter than 128 characters.");

    JsonWebSignature.Header header = new JsonWebSignature.Header().setAlgorithm("RS256");

    final long issuedAt = clock.currentTimeMillis() / 1000;
    FirebaseCustomAuthToken.Payload payload =
        new FirebaseCustomAuthToken.Payload()
            .setUid(uid)
            .setIssuer(signer.getAccount())
            .setSubject(signer.getAccount())
            .setAudience(FirebaseCustomAuthToken.FIREBASE_AUDIENCE)
            .setIssuedAtTimeSeconds(issuedAt)
            .setExpirationTimeSeconds(issuedAt + FirebaseCustomAuthToken.TOKEN_DURATION_SECONDS);
    if (!Strings.isNullOrEmpty(tenantId)) {
      payload.setTenantId(tenantId);
    }

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

  private String signPayload(
      JsonWebSignature.Header header,
      FirebaseCustomAuthToken.Payload payload) throws FirebaseAuthException {
    String content = encodePayload(header, payload);
    byte[] contentBytes = StringUtils.getBytesUtf8(content);
    String signature = Base64.encodeBase64URLSafeString(signer.sign(contentBytes));
    return content + "." + signature;
  }

  private String encodePayload(
      JsonWebSignature.Header header, FirebaseCustomAuthToken.Payload payload) {
    try {
      String headerString = Base64.encodeBase64URLSafeString(jsonFactory.toByteArray(header));
      String payloadString = Base64.encodeBase64URLSafeString(jsonFactory.toByteArray(payload));
      return headerString + "." + payloadString;
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Failed to encode JWT with the given claims: " + e.getMessage(), e);
    }
  }
}
