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

import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.util.Clock;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.Collection;
import java.util.Map;

/**
 * Provides helper methods to simplify the creation of FirebaseCustomAuthTokens.
 *
 * <p>This class is designed to hide underlying implementation details from a Firebase developer.
 */
public class FirebaseTokenFactory {

  private static FirebaseTokenFactory instance;

  private JsonFactory factory;
  private Clock clock;

  public FirebaseTokenFactory(JsonFactory factory, Clock clock) {
    this.factory = factory;
    this.clock = clock;
  }

  public static FirebaseTokenFactory getInstance() {
    if (null == instance) {
      instance = new FirebaseTokenFactory(new GsonFactory(), Clock.SYSTEM);
    }

    return instance;
  }

  public String createSignedCustomAuthTokenForUser(String uid, String issuer, PrivateKey privateKey)
      throws GeneralSecurityException, IOException {
    return createSignedCustomAuthTokenForUser(uid, null, issuer, privateKey);
  }

  public String createSignedCustomAuthTokenForUser(
      String uid, Map<String, Object> developerClaims, String issuer, PrivateKey privateKey)
      throws GeneralSecurityException, IOException {
    Preconditions.checkState(uid != null, "Uid must be provided.");
    Preconditions.checkState(issuer != null && !"".equals(issuer), "Must provide an issuer.");
    Preconditions.checkState(uid.length() <= 128, "Uid must be shorter than 128 characters.");

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
              String.format("developer_claims can not contain a reserved key: %s", key));
        }
      }
      GenericJson jsonObject = new GenericJson();
      jsonObject.putAll(developerClaims);
      payload.setDeveloperClaims(jsonObject);
    }

    return JsonWebSignature.signUsingRsaSha256(privateKey, factory, header, payload);
  }
}
