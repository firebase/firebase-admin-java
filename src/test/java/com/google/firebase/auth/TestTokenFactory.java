/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
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

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.api.client.json.webtoken.JsonWebToken.Payload;
import com.google.api.client.testing.http.FixedClock;
import com.google.api.client.util.Base64;
import com.google.api.client.util.Clock;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.firebase.internal.ApiClientUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;

class TestTokenFactory {

  public static final JsonFactory JSON_FACTORY = ApiClientUtils.getDefaultJsonFactory();
  public static final Clock CLOCK = new FixedClock(2002000L * 1000);
  public static final String PROJECT_ID = "proj-test-101";
  public static final String PRIVATE_KEY_ID = "aaaaaaaaaabbbbbbbbbbccccccccccdddddddddd";
  public static final String UID = "someUid";


  private final PrivateKey privateKey;
  private final String issuer;

  TestTokenFactory(String privateKey, String issuer) throws GeneralSecurityException {
    byte[] privateBytes = BaseEncoding.base64().decode(privateKey);
    KeySpec spec = new PKCS8EncodedKeySpec(privateBytes);
    this.privateKey = KeyFactory.getInstance("RSA").generatePrivate(spec);
    this.issuer = issuer;
  }

  public String createToken() {
    return createToken(createTokenPayload());
  }

  public String createToken(JsonWebSignature.Header header) {
    return createToken(header, createTokenPayload());
  }

  public String createToken(JsonWebSignature.Payload payload) {
    return createToken(createHeader(), payload);
  }

  public String createToken(JsonWebSignature.Header header, JsonWebToken.Payload payload) {
    try {
      return JsonWebSignature.signUsingRsaSha256(privateKey, JSON_FACTORY, header, payload);
    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException("Failed to create test token", e);
    }
  }

  public String createUnsignedTokenForEmulator() {
    return createUnsignedTokenForEmulator(createHeaderForEmulator(), createTokenPayload());
  }

  public String createUnsignedTokenForEmulator(JsonWebSignature.Header header) {
    return createUnsignedTokenForEmulator(header, createTokenPayload());
  }

  public String createUnsignedTokenForEmulator(JsonWebToken.Payload payload) {
    return createUnsignedTokenForEmulator(createHeaderForEmulator(), payload);
  }

  public String createUnsignedTokenForEmulator(
      JsonWebSignature.Header header, JsonWebSignature.Payload payload) {
    try {
      String encodedHeader = Base64.encodeBase64URLSafeString(JSON_FACTORY.toByteArray(header));
      String encodedPayload = Base64.encodeBase64URLSafeString(JSON_FACTORY.toByteArray(payload));
      // Unsigned token with no signature component
      return String.format("%s.%s.", encodedHeader, encodedPayload);
    } catch (IOException e) {
      throw new RuntimeException("Failed to create test token", e);
    }
  }

  public JsonWebSignature.Header createHeader() {
    JsonWebSignature.Header header = new JsonWebSignature.Header();
    header.setAlgorithm("RS256");
    header.setType("JWT");
    header.setKeyId(PRIVATE_KEY_ID);
    return header;
  }

  public JsonWebToken.Payload createTokenPayload() {
    JsonWebToken.Payload payload = new JsonWebToken.Payload();
    payload.setIssuer(issuer);
    payload.setAudience(PROJECT_ID);
    payload.setIssuedAtTimeSeconds(CLOCK.currentTimeMillis() / 1000);
    payload.setExpirationTimeSeconds(CLOCK.currentTimeMillis() / 1000 + 3600);
    payload.setSubject(UID);
    return payload;
  }

  public JsonWebSignature.Header createHeaderForEmulator() {
    JsonWebSignature.Header header = new JsonWebSignature.Header();
    header.setAlgorithm("none");
    header.setType("JWT");
    return header;
  }

  public String createTokenWithoutKeyId(boolean isEmulatorMode) {
    JsonWebSignature.Header header = createHeader();
    header.setKeyId(null);
    return isEmulatorMode ? createUnsignedTokenForEmulator(header)
        : createToken(header);
  }

  public String createTokenWithSubject(String sub, boolean isEmulatorMode) {
    Payload payload = createTokenPayload();
    payload.setSubject(sub);
    return isEmulatorMode ? createUnsignedTokenForEmulator(payload)
        : createToken(payload);
  }

  public String createTokenWithIncorrectAlgorithm(boolean isEmulatorMode) {
    JsonWebSignature.Header header = createHeader();
    header.setAlgorithm("HSA");
    return isEmulatorMode ? createUnsignedTokenForEmulator(header)
        : createToken(header);
  }

  public String createTokenWithoutAlgorithm(boolean isEmulatorMode) {
    JsonWebSignature.Header header = createHeader();
    header.setAlgorithm("none");
    return isEmulatorMode ? createUnsignedTokenForEmulator(header)
        : createToken(header);
  }

  public String createTokenWithIncorrectAudience(boolean isEmulatorMode) {
    Payload payload = createTokenPayload();
    payload.setAudience("invalid-audience");
    return isEmulatorMode ? createUnsignedTokenForEmulator(payload)
        : createToken(payload);
  }

  public String createTokenWithIncorrectIssuer(boolean isEmulatorMode) {
    Payload payload = createTokenPayload();
    payload.setIssuer("https://incorrect.issuer.prefix/" + TestTokenFactory.PROJECT_ID);
    return isEmulatorMode ? createUnsignedTokenForEmulator(payload)
        : createToken(payload);
  }

  public String createTokenWithTimestamps(long issuedAtSeconds, long expirationSeconds,
      boolean isEmulatorMode) {
    Payload payload = createTokenPayload();
    payload.setIssuedAtTimeSeconds(issuedAtSeconds);
    payload.setExpirationTimeSeconds(expirationSeconds);
    return isEmulatorMode ? createUnsignedTokenForEmulator(payload)
        : createToken(payload);
  }

  public String createTokenWithTenantId(String tenantId, boolean isEmulatorMode) {
    Payload payload = createTokenPayload();
    payload.set("firebase", ImmutableMap.of("tenant", tenantId));
    return isEmulatorMode ? createUnsignedTokenForEmulator(payload)
        : createToken(payload);
  }
}
