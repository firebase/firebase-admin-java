/*
 * Copyright  2019 Google Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.api.client.auth.openidconnect.IdTokenVerifier;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.api.client.json.webtoken.JsonWebToken.Payload;
import com.google.api.client.testing.http.FixedClock;
import com.google.api.client.util.Base64;
import com.google.api.client.util.Clock;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

public class FirebaseEmulatorTokenVerifierTest {
  private static final String TEST_TOKEN_ISSUER = "https://test.token.issuer";
  private static final Clock CLOCK = new FixedClock(2002000L * 1000);
  private static final String PROJECT_ID = "test-project-id";
  private static final String UID = "someUid";
  private static final JsonFactory JSON_FACTORY = Utils.getDefaultJsonFactory();

  private FirebaseTokenVerifier tokenVerifier;

  @Before
  public void setUp() {
    this.tokenVerifier = fullyPopulatedBuilder().build();
  }

  @Test
  public void testVerifyToken() throws Exception {
    String token = createUnsignedTokenForEmulator();

    FirebaseToken firebaseToken = tokenVerifier.verifyToken(token);

    assertEquals(TEST_TOKEN_ISSUER, firebaseToken.getIssuer());
    assertEquals(UID, firebaseToken.getUid());
  }

  @Test
  public void testVerifyTokenIncorrectAudience() {
    String token = createTokenWithIncorrectAudience();

    try {
      tokenVerifier.verifyToken(token);
    } catch (FirebaseAuthException e) {
      String message = String.format("Firebase test token has incorrect \"aud\" (audience) claim. "
          + "Expected \"%s\" but got \"invalid-audience\". "
          + "Make sure the test token comes from the same Firebase project as the service account "
          + "used to authenticate this SDK. "
          + "See https://test.doc.url for details on how to retrieve a test token.", PROJECT_ID);
      checkInvalidTokenException(e, message);
    }
  }

  @Test
  public void testVerifyTokenIncorrectIssuer() {
    String token = createTokenWithIncorrectIssuer();

    try {
      tokenVerifier.verifyToken(token);
    } catch (FirebaseAuthException e) {
      String message = String.format("Firebase test token has incorrect \"iss\" (issuer) claim. "
          + "Expected \"%s\" but got "
          + "\"https://incorrect.issuer.prefix/%s\". Make sure the test token comes "
          + "from the same Firebase project as the service account used to authenticate this SDK. "
          + "See https://test.doc.url for details on how to retrieve a test token.", TEST_TOKEN_ISSUER, PROJECT_ID);
      checkInvalidTokenException(e, message);
    }
  }

  @Test
  public void testVerifyTokenMissingSubject() {
    String token = createTokenWithSubject(null);

    try {
      tokenVerifier.verifyToken(token);
    } catch (FirebaseAuthException e) {
      String message = "Firebase test token has no \"sub\" (subject) claim. "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      checkInvalidTokenException(e, message);
    }
  }

  @Test
  public void testVerifyTokenEmptySubject() {
    String token = createTokenWithSubject("");

    try {
      tokenVerifier.verifyToken(token);
    } catch (FirebaseAuthException e) {
      String message = "Firebase test token has an empty string \"sub\" (subject) claim. "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      checkInvalidTokenException(e, message);
    }
  }

  @Test
  public void testVerifyTokenLongSubject() {
    String token = createTokenWithSubject(Strings.repeat("a", 129));

    try {
      tokenVerifier.verifyToken(token);
    } catch (FirebaseAuthException e) {
      String message = "Firebase test token has \"sub\" (subject) claim longer "
          + "than 128 characters. "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      checkInvalidTokenException(e, message);
    }
  }

  @Test
  public void testVerifyTokenIssuedAtInFuture() {
    long tenMinutesIntoTheFuture = (CLOCK.currentTimeMillis() / 1000)
        + TimeUnit.MINUTES.toSeconds(10);
    String token = createTokenWithTimestamps(
        tenMinutesIntoTheFuture,
        tenMinutesIntoTheFuture + TimeUnit.HOURS.toSeconds(1));

    try {
      tokenVerifier.verifyToken(token);
    } catch (FirebaseAuthException e) {
      String message = "Firebase test token is not yet valid. "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      checkInvalidTokenException(e, message);
    }
  }

  @Test
  public void testVerifyTokenExpired() {
    long twoHoursInPast = (CLOCK.currentTimeMillis() / 1000)
        - TimeUnit.HOURS.toSeconds(2);
    String token = createTokenWithTimestamps(
        twoHoursInPast,
        twoHoursInPast + TimeUnit.HOURS.toSeconds(1));

    try {
      tokenVerifier.verifyToken(token);
    } catch (FirebaseAuthException e) {
      String message = "Firebase test token has expired. "
          + "Get a fresh test token and try again. "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      checkException(e, message, AuthErrorCode.EXPIRED_ID_TOKEN);
    }
  }

  @Test
  public void testWithMalformedSignature() throws FirebaseAuthException {
    String token = createUnsignedTokenForEmulator();
    String[] segments = token.split("\\.");
    token = String.format("%s.%s.%s", segments[0], segments[1], "MalformedSignature");

    // No need to assert. verifyToken should not throw
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void testMalformedToken() {
    try {
      tokenVerifier.verifyToken("not.a.jwt");
    } catch (FirebaseAuthException e) {
      String message = "Failed to parse Firebase test token. "
          + "Make sure you passed a string that represents a complete and valid JWT. "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      assertEquals(ErrorCode.INVALID_ARGUMENT, e.getErrorCode());
      assertEquals(message, e.getMessage());
      assertTrue(e.getCause() instanceof IllegalArgumentException);
      assertNull(e.getHttpResponse());
      assertEquals(AuthErrorCode.INVALID_ID_TOKEN, e.getAuthErrorCode());
    }
  }

  @Test
  public void testVerifyTokenWithTenantId() throws FirebaseAuthException {
    FirebaseEmulatorTokenVerifier verifier = fullyPopulatedBuilder().build();

    FirebaseToken firebaseToken = verifier.verifyToken(createTokenWithTenantId("TENANT_1"));

    assertEquals(TEST_TOKEN_ISSUER, firebaseToken.getIssuer());
    assertEquals(UID, firebaseToken.getUid());
    assertEquals("TENANT_1", firebaseToken.getTenantId());
  }

  @Test
  public void testVerifyTokenWithMatchingTenantId() throws FirebaseAuthException {
    FirebaseToken firebaseToken = fullyPopulatedBuilder()
        .setTenantId("TENANT_1")
        .build().verifyToken(createTokenWithTenantId("TENANT_1"));

    assertEquals(TEST_TOKEN_ISSUER, firebaseToken.getIssuer());
    assertEquals(UID, firebaseToken.getUid());
    assertEquals("TENANT_1", firebaseToken.getTenantId());
  }

  @Test
  public void testVerifyTokenDifferentTenantIds() {
    try {
      fullyPopulatedBuilder()
          .setTenantId("TENANT_1")
          .build().verifyToken(createTokenWithTenantId("TENANT_2"));
    } catch (FirebaseAuthException e) {
      assertEquals(AuthErrorCode.TENANT_ID_MISMATCH, e.getAuthErrorCode());
      assertEquals(
          "The tenant ID ('TENANT_2') of the token did not match the expected value ('TENANT_1')",
          e.getMessage());
    }
  }

  @Test
  public void testVerifyTokenNoTenantId() {
    try {
      fullyPopulatedBuilder()
          .setTenantId("TENANT_1")
          .build().verifyToken(createUnsignedTokenForEmulator());
    } catch (FirebaseAuthException e) {
      assertEquals(AuthErrorCode.TENANT_ID_MISMATCH, e.getAuthErrorCode());
      assertEquals(
          "The tenant ID ('') of the token did not match the expected value ('TENANT_1')",
          e.getMessage());
    }
  }

  @Test
  public void testVerifyTokenMissingTenantId() {
    try {
      fullyPopulatedBuilder()
        .setTenantId("TENANT_ID")
        .build()
        .verifyToken(createUnsignedTokenForEmulator());
    } catch (FirebaseAuthException e) {
      assertEquals(AuthErrorCode.TENANT_ID_MISMATCH, e.getAuthErrorCode());
      assertEquals(
          "The tenant ID ('') of the token did not match the expected value ('TENANT_ID')",
          e.getMessage());
    }
  }

  @Test(expected = NullPointerException.class)
  public void testBuilderNoJsonFactory() {
    fullyPopulatedBuilder().setJsonFactory(null).build();
  }

  @Test(expected = NullPointerException.class)
  public void testBuilderNoIdTokenVerifier() {
    fullyPopulatedBuilder().setIdTokenVerifier(null).build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderNoShortName() {
    fullyPopulatedBuilder().setShortName(null).build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderNoDocUrl() {
    fullyPopulatedBuilder().setDocUrl(null).build();
  }

  private FirebaseEmulatorTokenVerifier.Builder fullyPopulatedBuilder() {
    return FirebaseEmulatorTokenVerifier.builder()
        .setShortName("test token")
        .setDocUrl("https://test.doc.url")
        .setJsonFactory(JSON_FACTORY)
        .setInvalidTokenErrorCode(AuthErrorCode.INVALID_ID_TOKEN)
        .setExpiredTokenErrorCode(AuthErrorCode.EXPIRED_ID_TOKEN)
        .setIdTokenVerifier(newIdTokenVerifier());
  }

  private IdTokenVerifier newIdTokenVerifier() {
    return new IdTokenVerifier.Builder()
          .setClock(CLOCK)
          .setAudience(ImmutableList.of(PROJECT_ID))
          .setIssuer(TEST_TOKEN_ISSUER)
          .build();
  }

  private JsonWebSignature.Header createHeader() {
    JsonWebSignature.Header header = new JsonWebSignature.Header();
    header.setAlgorithm("none");
    header.setType("JWT");
    return header;
  }

  private JsonWebToken.Payload createTokenPayload() {
    JsonWebToken.Payload payload = new JsonWebToken.Payload();
    payload.setIssuer(TEST_TOKEN_ISSUER);
    payload.setAudience(PROJECT_ID);
    payload.setIssuedAtTimeSeconds(CLOCK.currentTimeMillis() / 1000);
    payload.setExpirationTimeSeconds(CLOCK.currentTimeMillis() / 1000 + 3600);
    payload.setSubject(UID);
    return payload;
  }

  private String createUnsignedTokenForEmulator() {
    return createUnsignedTokenForEmulator(createHeader(), createTokenPayload());
  }

  private String createUnsignedTokenForEmulator(JsonWebSignature.Header header, JsonWebSignature.Payload payload) {
    try {
      String content =
          Base64.encodeBase64URLSafeString(JSON_FACTORY.toByteArray(header)) + "." + Base64
              .encodeBase64URLSafeString(JSON_FACTORY.toByteArray(payload));
      // Unsigned token
      return content + ".";
    } catch (IOException e) {
      throw new RuntimeException("Failed to create test token", e);
    }
  }

  private String createTokenWithSubject(String sub) {
    Payload payload = createTokenPayload();
    payload.setSubject(sub);
    return createUnsignedTokenForEmulator(createHeader(), payload);
  }

  private String createTokenWithIncorrectAudience() {
    Payload payload = createTokenPayload();
    payload.setAudience("invalid-audience");
    return createUnsignedTokenForEmulator(createHeader(), payload);
  }

  private String createTokenWithIncorrectIssuer() {
    Payload payload = createTokenPayload();
    payload.setIssuer("https://incorrect.issuer.prefix/" + PROJECT_ID);
    return createUnsignedTokenForEmulator(createHeader(), payload);
  }

  private String createTokenWithTimestamps(long issuedAtSeconds, long expirationSeconds) {
    Payload payload = createTokenPayload();
    payload.setIssuedAtTimeSeconds(issuedAtSeconds);
    payload.setExpirationTimeSeconds(expirationSeconds);
    return createUnsignedTokenForEmulator(createHeader(), payload);
  }

  private void checkInvalidTokenException(FirebaseAuthException e, String message) {
    checkException(e, message, AuthErrorCode.INVALID_ID_TOKEN);
  }

  private void checkException(FirebaseAuthException e, String message, AuthErrorCode errorCode) {
    assertEquals(ErrorCode.INVALID_ARGUMENT, e.getErrorCode());
    assertEquals(message, e.getMessage());
    assertNull(e.getCause());
    assertNull(e.getHttpResponse());
    assertEquals(errorCode, e.getAuthErrorCode());
  }

  private String createTokenWithTenantId(String tenantId) {
    Payload payload = createTokenPayload();
    payload.set("firebase", ImmutableMap.of("tenant", tenantId));
    return createUnsignedTokenForEmulator(createHeader(), payload);
  }
}
