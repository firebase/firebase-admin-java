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

import static com.google.firebase.auth.FirebaseTokenVerifierImplTestUtils.TEST_TOKEN_ISSUER;
import static com.google.firebase.auth.FirebaseTokenVerifierImplTestUtils.checkInvalidTokenException;
import static com.google.firebase.auth.FirebaseTokenVerifierImplTestUtils.fullyPopulatedBuilder;
import static com.google.firebase.auth.FirebaseTokenVerifierImplTestUtils.newPublicKeysManager;
import static com.google.firebase.auth.FirebaseTokenVerifierImplTestUtils.newTestTokenVerifier;
import static com.google.firebase.auth.internal.Utils.AUTH_EMULATOR_HOST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.common.base.Strings;
import com.google.firebase.ErrorCode;
import com.google.firebase.internal.FirebaseProcessEnvironment;
import com.google.firebase.testing.ServiceAccount;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EmulatorFirebaseTokenVerifierImplTest {
  private FirebaseTokenVerifier tokenVerifier;
  private TestTokenFactory tokenFactory;

  @Before
  public void setUp() throws Exception {
    // Set the Auth Emulator host prior to initialization
    FirebaseProcessEnvironment.setenv(AUTH_EMULATOR_HOST, "localhost:9099");
    ServiceAccount serviceAccount = ServiceAccount.EDITOR;
    GooglePublicKeysManager publicKeysManager = newPublicKeysManager(serviceAccount.getCert());
    this.tokenVerifier = newTestTokenVerifier(publicKeysManager);
    this.tokenFactory = new TestTokenFactory(serviceAccount.getPrivateKey(), TEST_TOKEN_ISSUER);
  }

  @After
  public void tearDown() {
    FirebaseProcessEnvironment.clearCache();
  }

  @Test
  public void testVerifyToken() throws Exception {
    String token = tokenFactory.createUnsignedTokenForEmulator();

    FirebaseToken firebaseToken = tokenVerifier.verifyToken(token);

    assertEquals(TEST_TOKEN_ISSUER, firebaseToken.getIssuer());
    assertEquals(TestTokenFactory.UID, firebaseToken.getUid());
  }

  @Test
  public void testVerifyTokenWithoutKeyId() throws Exception {
    String token = createTokenWithoutKeyId();

    // Should not throw, even if missing kid
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void testVerifyTokenIncorrectAlgorithm() throws Exception {
    String token = createTokenWithIncorrectAlgorithm();

    // Should not throw, even if missing algorithm
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void testVerifyTokenWithoutAlgorithm() throws Exception {
    String token = createTokenWithoutAlgorithm();

    // Should not throw, even if algorithm is none
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void testVerifyTokenIncorrectAudience() {
    String token = createTokenWithIncorrectAudience();

    try {
      tokenVerifier.verifyToken(token);
      fail("No error thrown for incorrect audience");
    } catch (FirebaseAuthException e) {
      String message = String.format("Firebase test token has incorrect \"aud\" (audience) claim. "
              + "Expected \"%s\" but got \"invalid-audience\". "
              + "Make sure the test token comes from the same Firebase project as the service "
              + "account used to authenticate this SDK. "
              + "See https://test.doc.url for details on how to retrieve a test token.",
          TestTokenFactory.PROJECT_ID);
      checkInvalidTokenException(e, message);
    }
  }

  @Test
  public void testVerifyTokenIncorrectIssuer() {
    String token = createTokenWithIncorrectIssuer();

    try {
      tokenVerifier.verifyToken(token);
      fail("No error thrown for incorrect issuer");
    } catch (FirebaseAuthException e) {
      String message = String.format("Firebase test token has incorrect \"iss\" (issuer) claim. "
              + "Expected \"%s\" but got "
              + "\"https://incorrect.issuer.prefix/%s\". Make sure the test token comes "
              + "from the same Firebase project as the service account used to authenticate this "
              + "SDK. See https://test.doc.url for details on how to retrieve a test token.",
          TEST_TOKEN_ISSUER, TestTokenFactory.PROJECT_ID);
      checkInvalidTokenException(e, message);
    }
  }

  @Test
  public void testVerifyTokenMissingSubject() {
    String token = createTokenWithSubject(null);

    try {
      tokenVerifier.verifyToken(token);
      fail("No error thrown for missing subject");
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
      fail("No error thrown for empty subject");
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
      fail("No error thrown for long subject");
    } catch (FirebaseAuthException e) {
      String message = "Firebase test token has \"sub\" (subject) claim longer "
          + "than 128 characters. "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      checkInvalidTokenException(e, message);
    }
  }

  @Test
  public void testVerifyTokenIssuedAtInFuture() {
    long tenMinutesIntoTheFuture = (TestTokenFactory.CLOCK.currentTimeMillis() / 1000)
        + TimeUnit.MINUTES.toSeconds(10);
    String token = createTokenWithTimestamps(
        tenMinutesIntoTheFuture,
        tenMinutesIntoTheFuture + TimeUnit.HOURS.toSeconds(1));

    try {
      tokenVerifier.verifyToken(token);
      fail("No error thrown for token issued in future");
    } catch (FirebaseAuthException e) {
      String message = "Firebase test token is not yet valid. "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      checkInvalidTokenException(e, message);
    }
  }

  @Test
  public void testVerifyTokenExpired() {
    long twoHoursInPast = (TestTokenFactory.CLOCK.currentTimeMillis() / 1000)
        - TimeUnit.HOURS.toSeconds(2);
    String token = createTokenWithTimestamps(
        twoHoursInPast,
        twoHoursInPast + TimeUnit.HOURS.toSeconds(1));

    try {
      tokenVerifier.verifyToken(token);
      fail("No error thrown for expired token");
    } catch (FirebaseAuthException e) {
      String message = "Firebase test token has expired. "
          + "Get a fresh test token and try again. "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      FirebaseTokenVerifierImplTestUtils.checkException(e, message, AuthErrorCode.EXPIRED_ID_TOKEN);
    }
  }

  @Test
  public void testWithMalformedSignature() throws FirebaseAuthException {
    String token = tokenFactory.createUnsignedTokenForEmulator();
    String[] segments = token.split("\\.");
    token = String.format("%s.%s.%s", segments[0], segments[1], "MalformedSignature");

    // No need to assert. verifyToken should not throw
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void testMalformedToken() {
    try {
      tokenVerifier.verifyToken("not.a.jwt");
      fail("No error thrown for malformed token");
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
    FirebaseToken firebaseToken = tokenVerifier
        .verifyToken(createTokenWithTenantId("TENANT_1"));

    assertEquals(TEST_TOKEN_ISSUER, firebaseToken.getIssuer());
    assertEquals(TestTokenFactory.UID, firebaseToken.getUid());
    assertEquals("TENANT_1", firebaseToken.getTenantId());
  }

  @Test
  public void testVerifyTokenWithMatchingTenantId() throws FirebaseAuthException {
    FirebaseToken firebaseToken = fullyPopulatedBuilder()
        .setTenantId("TENANT_1")
        .build().verifyToken(createTokenWithTenantId("TENANT_1"));

    assertEquals(TEST_TOKEN_ISSUER, firebaseToken.getIssuer());
    assertEquals(TestTokenFactory.UID, firebaseToken.getUid());
    assertEquals("TENANT_1", firebaseToken.getTenantId());
  }

  @Test
  public void testVerifyTokenDifferentTenantIds() {
    try {
      fullyPopulatedBuilder()
          .setTenantId("TENANT_1")
          .build().verifyToken(createTokenWithTenantId("TENANT_2"));
      fail("No error thrown for mismatched tenant IDs");
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
          .build()
          .verifyToken(tokenFactory.createUnsignedTokenForEmulator());
      fail("No error thrown for missing tenant ID in token");
    } catch (FirebaseAuthException e) {
      assertEquals(AuthErrorCode.TENANT_ID_MISMATCH, e.getAuthErrorCode());
      assertEquals(
          "The tenant ID ('') of the token did not match the expected value ('TENANT_1')",
          e.getMessage());
    }
  }

  private String createTokenWithIncorrectAudience() {
    return tokenFactory.createTokenWithIncorrectAudience(true);
  }

  private String createTokenWithoutKeyId() {
    return tokenFactory.createTokenWithoutKeyId(true);
  }

  private String createTokenWithIncorrectAlgorithm() {
    return tokenFactory.createTokenWithIncorrectAlgorithm(true);
  }

  private String createTokenWithoutAlgorithm() {
    return tokenFactory.createTokenWithoutAlgorithm(true);
  }

  private String createTokenWithIncorrectIssuer() {
    return tokenFactory.createTokenWithIncorrectIssuer(true);
  }

  private String createTokenWithSubject(String sub) {
    return tokenFactory.createTokenWithSubject(sub, true);
  }

  private String createTokenWithTenantId(String tenantId) {
    return tokenFactory.createTokenWithTenantId(tenantId, true);
  }

  private String createTokenWithTimestamps(long issuedAtSeconds, long expirationSeconds) {
    return tokenFactory.createTokenWithTimestamps(issuedAtSeconds, expirationSeconds, true);
  }
}
