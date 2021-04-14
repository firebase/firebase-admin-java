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

import static com.google.firebase.auth.Utils.AUTH_EMULATOR_HOST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.auth.openidconnect.IdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebToken.Payload;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.firebase.ErrorCode;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FirebaseTokenVerifierImplTest {

  private static final String CUSTOM_TOKEN_AUDIENCE =
      "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit"
          + ".v1.IdentityToolkit";

  private static final String LEGACY_CUSTOM_TOKEN =
      "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJkIjp7"
          + "InVpZCI6IjEiLCJhYmMiOiIwMTIzNDU2Nzg5fiFAIyQlXiYqKClfKy09YWJjZGVmZ2hpamtsbW5vcHF"
          + "yc3R1dnd4eXpBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWiwuLzsnW11cXDw"
          + "-P1wie318In0sInYiOjAsImlhdCI6MTQ4MDk4Mj"
          + "U2NH0.ZWEpoHgIPCAz8Q-cNFBS8jiqClTJ3j27yuRkQo-QxyI";

  private static final String TEST_TOKEN_ISSUER = "https://test.token.issuer";

  private FirebaseTokenVerifier tokenVerifier;
  private TestTokenFactory tokenFactory;

  @Before
  public void setUp() throws Exception {
    ServiceAccount serviceAccount = ServiceAccount.EDITOR;
    GooglePublicKeysManager publicKeysManager = newPublicKeysManager(serviceAccount.getCert());
    this.tokenVerifier = newTestTokenVerifier(publicKeysManager);
    this.tokenFactory = new TestTokenFactory(serviceAccount.getPrivateKey(), TEST_TOKEN_ISSUER);
  }

  @After
  public void tearDown() {
    TestUtils.unsetEnvironmentVariables(ImmutableSet.of(AUTH_EMULATOR_HOST));
  }

  @Test
  public void testVerifyToken() throws Exception {
    String token = tokenFactory.createToken();

    FirebaseToken firebaseToken = tokenVerifier.verifyToken(token);

    assertEquals(TEST_TOKEN_ISSUER, firebaseToken.getIssuer());
    assertEquals(TestTokenFactory.UID, firebaseToken.getUid());
  }

  @Test
  public void testVerifyTokenWithoutKeyId() {
    String token = createTokenWithoutKeyId();

    try {
      tokenVerifier.verifyToken(token);
      fail("No error thrown for missing kid");
    } catch (FirebaseAuthException e) {
      String message = "Firebase test token has no \"kid\" claim. "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      checkInvalidTokenException(e, message);
    }
  }

  @Test
  public void testVerifyTokenFirebaseCustomToken() {
    String token = createCustomToken();

    try {
      tokenVerifier.verifyToken(token);
      fail("No error thrown for passing custom token");
    } catch (FirebaseAuthException e) {
      String message = "verifyTestToken() expects a test token, but was given a custom token. "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      checkInvalidTokenException(e, message);
    }
  }

  @Test
  public void testVerifyTokenIncorrectAlgorithm() {
    String token = createTokenWithIncorrectAlgorithm();

    try {
      tokenVerifier.verifyToken(token);
      fail("No error thrown for incorrect alg");
    } catch (FirebaseAuthException e) {
      String message = "Firebase test token has incorrect algorithm. "
          + "Expected \"RS256\" but got \"HSA\". "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      checkInvalidTokenException(e, message);
    }
  }

  @Test
  public void testVerifyTokenWithoutAlgorithm() {
    String token = createTokenWithoutAlgorithm();

    try {
      tokenVerifier.verifyToken(token);
      fail("No error thrown for NONE alg");
    } catch (FirebaseAuthException e) {
      String message = "Firebase test token has incorrect algorithm. "
          + "Expected \"RS256\" but got \"NONE\". "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      checkInvalidTokenException(e, message);
    }
  }

  @Test
  public void testVerifyTokenIncorrectAudience() {
    String token = createTokenWithIncorrectAudience();

    try {
      tokenVerifier.verifyToken(token);
      fail("No error thrown for incorrect audience");
    } catch (FirebaseAuthException e) {
      String message = "Firebase test token has incorrect \"aud\" (audience) claim. "
          + "Expected \"proj-test-101\" but got \"invalid-audience\". "
          + "Make sure the test token comes from the same Firebase project as the service account "
          + "used to authenticate this SDK. "
          + "See https://test.doc.url for details on how to retrieve a test token.";
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
      String message = "Firebase test token has incorrect \"iss\" (issuer) claim. "
          + "Expected \"https://test.token.issuer\" but got "
          + "\"https://incorrect.issuer.prefix/proj-test-101\". Make sure the test token comes "
          + "from the same Firebase project as the service account used to authenticate this SDK. "
          + "See https://test.doc.url for details on how to retrieve a test token.";
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
      fail("No error thrown for very long subject");
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
      fail("No error thrown for token issued in the future");
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
      checkException(e, message, AuthErrorCode.EXPIRED_ID_TOKEN);
    }
  }

  @Test
  public void testVerifyTokenSignatureMismatch() {
    String token = tokenFactory.createToken();
    GooglePublicKeysManager publicKeysManager = newPublicKeysManager(
        ServiceAccount.NONE.getCert());
    FirebaseTokenVerifier tokenVerifier = newTestTokenVerifier(publicKeysManager);

    try {
      tokenVerifier.verifyToken(token);
      fail("No error thrown for mismatched signature");
    } catch (FirebaseAuthException e) {
      String message = "Failed to verify the signature of Firebase test token. "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      checkInvalidTokenException(e, message);
    }
  }

  @Test
  public void testMalformedCert() {
    String token = tokenFactory.createToken();
    GooglePublicKeysManager publicKeysManager = newPublicKeysManager("malformed.cert");
    FirebaseTokenVerifier tokenVerifier = newTestTokenVerifier(publicKeysManager);

    try {
      tokenVerifier.verifyToken(token);
      fail("No error thrown for malformed cert");
    } catch (FirebaseAuthException e) {
      String message = "Error while fetching public key certificates: Could not parse certificate";
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertTrue(e.getMessage().startsWith(message));
      assertTrue(e.getCause() instanceof GeneralSecurityException);
      assertNull(e.getHttpResponse());
      assertEquals(AuthErrorCode.CERTIFICATE_FETCH_FAILED, e.getAuthErrorCode());
    }
  }

  @Test
  public void testCertificateFetchError() {
    MockHttpTransport failingTransport = new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
        throw new IOException("Expected error");
      }
    };
    GooglePublicKeysManager publicKeysManager = newPublicKeysManager(failingTransport);
    FirebaseTokenVerifier idTokenVerifier = newTestTokenVerifier(publicKeysManager);
    String token = tokenFactory.createToken();

    try {
      idTokenVerifier.verifyToken(token);
      fail("No error thrown for failing to fetch certificate");
    } catch (FirebaseAuthException e) {
      String message = "Error while fetching public key certificates: Expected error";
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals(message, e.getMessage());
      assertTrue(e.getCause() instanceof IOException);
      assertNull(e.getHttpResponse());
      assertEquals(AuthErrorCode.CERTIFICATE_FETCH_FAILED, e.getAuthErrorCode());
    }
  }

  @Test
  public void testMalformedSignature() {
    String token = tokenFactory.createToken();
    String[] segments = token.split("\\.");
    token = String.format("%s.%s.%s", segments[0], segments[1], "MalformedSignature");

    try {
      tokenVerifier.verifyToken(token);
      fail("No error thrown for malformed signature");
    } catch (FirebaseAuthException e) {
      String message = "Failed to verify the signature of Firebase test token. "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      checkInvalidTokenException(e, message);
    }
  }

  @Test
  public void testLegacyCustomToken() {
    try {
      tokenVerifier.verifyToken(LEGACY_CUSTOM_TOKEN);
      fail("No error thrown for passing legacy token");
    } catch (FirebaseAuthException e) {
      String message = "verifyTestToken() expects a test token, but was given a "
          + "legacy custom token. "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      checkInvalidTokenException(e, message);
    }
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
    FirebaseTokenVerifierImpl verifier = fullyPopulatedBuilder().build();

    FirebaseToken firebaseToken = verifier.verifyToken(createTokenWithTenantId("TENANT_1"));

    assertEquals(TEST_TOKEN_ISSUER, firebaseToken.getIssuer());
    assertEquals(TestTokenFactory.UID, firebaseToken.getUid());
    assertEquals("TENANT_1", firebaseToken.getTenantId());
  }

  @Test
  public void testVerifyTokenWithMatchingTenantId() throws FirebaseAuthException {
    FirebaseTokenVerifierImpl verifier = fullyPopulatedBuilder()
        .setTenantId("TENANT_1")
        .build();

    FirebaseToken firebaseToken = verifier.verifyToken(createTokenWithTenantId("TENANT_1"));

    assertEquals(TEST_TOKEN_ISSUER, firebaseToken.getIssuer());
    assertEquals(TestTokenFactory.UID, firebaseToken.getUid());
    assertEquals("TENANT_1", firebaseToken.getTenantId());
  }

  @Test
  public void testVerifyTokenDifferentTenantIds() {
    FirebaseTokenVerifierImpl verifier = fullyPopulatedBuilder()
        .setTenantId("TENANT_1")
        .build();
    String token = createTokenWithTenantId("TENANT_2");

    try {
      verifier.verifyToken(token);
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
    FirebaseTokenVerifierImpl verifier = fullyPopulatedBuilder()
        .setTenantId("TENANT_1")
        .build();
    String token = tokenFactory.createToken();

    try {
      verifier.verifyToken(token);
      fail("No error thrown for missing tenant ID");
    } catch (FirebaseAuthException e) {
      assertEquals(AuthErrorCode.TENANT_ID_MISMATCH, e.getAuthErrorCode());
      assertEquals(
          "The tenant ID ('') of the token did not match the expected value ('TENANT_1')",
          e.getMessage());
    }
  }

  @Test(expected = NullPointerException.class)
  public void testBuilderNoPublicKeysManager() {
    fullyPopulatedBuilder().setPublicKeysManager(null).build();
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
  public void testBuilderNoMethodName() {
    fullyPopulatedBuilder().setMethod(null).build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderNoShortName() {
    fullyPopulatedBuilder().setShortName(null).build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderNoDocUrl() {
    fullyPopulatedBuilder().setDocUrl(null).build();
  }

  @Test
  public void testVerifyTokenForEmulator() throws Exception {
    setUpForEmulator();
    String token = tokenFactory.createUnsignedTokenForEmulator();

    FirebaseToken firebaseToken = tokenVerifier.verifyToken(token);

    assertEquals(TEST_TOKEN_ISSUER, firebaseToken.getIssuer());
    assertEquals(TestTokenFactory.UID, firebaseToken.getUid());
  }

  @Test
  public void testVerifyTokenWithoutKeyIdForEmulator() throws Exception {
    setUpForEmulator();
    String token = createTokenWithoutKeyId(true);

    // Should not throw, even if missing kid
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void testVerifyTokenIncorrectAlgorithmForEmulator() {
    setUpForEmulator();
    String token = createTokenWithIncorrectAlgorithm(true);

    try {
      tokenVerifier.verifyToken(token);
    } catch (FirebaseAuthException e) {
      String message = "Firebase test token has incorrect algorithm. "
          + "Expected \"RS256\" but got \"HSA\". "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      checkInvalidTokenException(e, message);
    }
  }

  @Test
  public void testVerifyTokenWithoutAlgorithmForEmulator() throws Exception {
    setUpForEmulator();
    String token = createTokenWithoutAlgorithm(true);

    // Should not throw, even if algorithm is none
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void testVerifyTokenIncorrectAudienceForEmulator() {
    setUpForEmulator();
    String token = createTokenWithIncorrectAudience(true);

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
  public void testVerifyTokenIncorrectIssuerForEmulator() {
    setUpForEmulator();
    String token = createTokenWithIncorrectIssuer(true);

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
  public void testVerifyTokenMissingSubjectForEmulator() {
    setUpForEmulator();
    String token = createTokenWithSubject(null, true);

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
  public void testVerifyTokenEmptySubjectForEmulator() {
    setUpForEmulator();
    String token = createTokenWithSubject("", true);

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
  public void testVerifyTokenLongSubjectForEmulator() {
    setUpForEmulator();
    String token = createTokenWithSubject(Strings.repeat("a", 129), true);

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
  public void testVerifyTokenIssuedAtInFutureForEmulator() {
    setUpForEmulator();
    long tenMinutesIntoTheFuture = (TestTokenFactory.CLOCK.currentTimeMillis() / 1000)
        + TimeUnit.MINUTES.toSeconds(10);
    String token = createTokenWithTimestamps(
        tenMinutesIntoTheFuture,
        tenMinutesIntoTheFuture + TimeUnit.HOURS.toSeconds(1), true);

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
  public void testVerifyTokenExpiredForEmulator() {
    setUpForEmulator();
    long twoHoursInPast = (TestTokenFactory.CLOCK.currentTimeMillis() / 1000)
        - TimeUnit.HOURS.toSeconds(2);
    String token = createTokenWithTimestamps(
        twoHoursInPast,
        twoHoursInPast + TimeUnit.HOURS.toSeconds(1), true);

    try {
      tokenVerifier.verifyToken(token);
      fail("No error thrown for expired token");
    } catch (FirebaseAuthException e) {
      String message = "Firebase test token has expired. "
          + "Get a fresh test token and try again. "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      checkException(e, message, AuthErrorCode.EXPIRED_ID_TOKEN);
    }
  }

  @Test
  public void testWithMalformedSignatureForEmulator() throws FirebaseAuthException {
    setUpForEmulator();
    String token = tokenFactory.createUnsignedTokenForEmulator();
    String[] segments = token.split("\\.");
    token = String.format("%s.%s.%s", segments[0], segments[1], "MalformedSignature");

    // No need to assert. verifyToken should not throw
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void testMalformedTokenForEmulator() {
    setUpForEmulator();
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
  public void testVerifyTokenWithTenantIdForEmulator() throws FirebaseAuthException {
    setUpForEmulator();
    FirebaseToken firebaseToken = tokenVerifier
        .verifyToken(createTokenWithTenantId("TENANT_1", true));

    assertEquals(TEST_TOKEN_ISSUER, firebaseToken.getIssuer());
    assertEquals(TestTokenFactory.UID, firebaseToken.getUid());
    assertEquals("TENANT_1", firebaseToken.getTenantId());
  }

  @Test
  public void testVerifyTokenWithMatchingTenantIdForEmulator() throws FirebaseAuthException {
    setUpForEmulator();
    FirebaseToken firebaseToken = fullyPopulatedBuilder()
        .setTenantId("TENANT_1")
        .build().verifyToken(createTokenWithTenantId("TENANT_1", true));

    assertEquals(TEST_TOKEN_ISSUER, firebaseToken.getIssuer());
    assertEquals(TestTokenFactory.UID, firebaseToken.getUid());
    assertEquals("TENANT_1", firebaseToken.getTenantId());
  }

  @Test
  public void testVerifyTokenDifferentTenantIdsForEmulator() {
    setUpForEmulator();
    try {
      fullyPopulatedBuilder()
          .setTenantId("TENANT_1")
          .build().verifyToken(createTokenWithTenantId("TENANT_2", true));
      fail("No error thrown for mismatched tenant IDs");
    } catch (FirebaseAuthException e) {
      assertEquals(AuthErrorCode.TENANT_ID_MISMATCH, e.getAuthErrorCode());
      assertEquals(
          "The tenant ID ('TENANT_2') of the token did not match the expected value ('TENANT_1')",
          e.getMessage());
    }
  }

  @Test
  public void testVerifyTokenNoTenantIdForEmulator() {
    setUpForEmulator();
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

  private GooglePublicKeysManager newPublicKeysManager(String certificate) {
    String serviceAccountCertificates =
        String.format("{\"%s\" : \"%s\"}", TestTokenFactory.PRIVATE_KEY_ID, certificate);
    HttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(
            new MockLowLevelHttpResponse().setContent(serviceAccountCertificates))
        .build();
    return newPublicKeysManager(transport);
  }

  private GooglePublicKeysManager newPublicKeysManager(HttpTransport transport) {
    return new GooglePublicKeysManager.Builder(
        transport, FirebaseTokenUtils.UNQUOTED_CTRL_CHAR_JSON_FACTORY)
        .setClock(TestTokenFactory.CLOCK)
        .setPublicCertsEncodedUrl("https://test.cert.url")
        .build();
  }

  private FirebaseTokenVerifier newTestTokenVerifier(GooglePublicKeysManager publicKeysManager) {
    return fullyPopulatedBuilder()
        .setPublicKeysManager(publicKeysManager)
        .build();
  }

  private FirebaseTokenVerifierImpl.Builder fullyPopulatedBuilder() {
    return FirebaseTokenVerifierImpl.builder()
        .setShortName("test token")
        .setMethod("verifyTestToken()")
        .setDocUrl("https://test.doc.url")
        .setJsonFactory(TestTokenFactory.JSON_FACTORY)
        .setPublicKeysManager(newPublicKeysManager(ServiceAccount.EDITOR.getCert()))
        .setInvalidTokenErrorCode(AuthErrorCode.INVALID_ID_TOKEN)
        .setExpiredTokenErrorCode(AuthErrorCode.EXPIRED_ID_TOKEN)
        .setIdTokenVerifier(newIdTokenVerifier());
  }

  private IdTokenVerifier newIdTokenVerifier() {
    return new IdTokenVerifier.Builder()
          .setClock(TestTokenFactory.CLOCK)
          .setAudience(ImmutableList.of(TestTokenFactory.PROJECT_ID))
          .setIssuer(TEST_TOKEN_ISSUER)
          .build();
  }

  private String createTokenWithoutKeyId() {
    return createTokenWithoutKeyId(false);
  }

  private String createTokenWithoutKeyId(boolean isEmulatorMode) {
    JsonWebSignature.Header header = tokenFactory.createHeader();
    header.setKeyId(null);
    return isEmulatorMode ? tokenFactory.createUnsignedTokenForEmulator(header)
        : tokenFactory.createToken(header);
  }

  private String createTokenWithSubject(String sub) {
    return createTokenWithSubject(sub, false);
  }

  private String createTokenWithSubject(String sub, boolean isEmulatorMode) {
    Payload payload = tokenFactory.createTokenPayload();
    payload.setSubject(sub);
    return isEmulatorMode ? tokenFactory.createUnsignedTokenForEmulator(payload)
        : tokenFactory.createToken(payload);
  }

  private String createCustomToken() {
    JsonWebSignature.Header header = tokenFactory.createHeader();
    header.setKeyId(null);
    Payload payload = tokenFactory.createTokenPayload();
    payload.setAudience(CUSTOM_TOKEN_AUDIENCE);
    return tokenFactory.createToken(header, payload);
  }

  private String createTokenWithIncorrectAlgorithm() {
    return createTokenWithIncorrectAlgorithm(false);
  }

  private String createTokenWithIncorrectAlgorithm(boolean isEmulatorMode) {
    JsonWebSignature.Header header = tokenFactory.createHeader();
    header.setAlgorithm("HSA");
    return isEmulatorMode ? tokenFactory.createUnsignedTokenForEmulator(header)
        : tokenFactory.createToken(header);
  }

  private String createTokenWithoutAlgorithm() {
    return createTokenWithoutAlgorithm(false);
  }

  private String createTokenWithoutAlgorithm(boolean isEmulatorMode) {
    JsonWebSignature.Header header = tokenFactory.createHeader();
    header.setAlgorithm("NONE");
    return isEmulatorMode ? tokenFactory.createUnsignedTokenForEmulator(header)
        : tokenFactory.createToken(header);
  }

  private String createTokenWithIncorrectAudience() {
    return createTokenWithIncorrectAudience(false);
  }

  private String createTokenWithIncorrectAudience(boolean isEmulatorMode) {
    Payload payload = tokenFactory.createTokenPayload();
    payload.setAudience("invalid-audience");
    return isEmulatorMode ? tokenFactory.createUnsignedTokenForEmulator(payload)
        : tokenFactory.createToken(payload);
  }

  private String createTokenWithIncorrectIssuer() {
    return createTokenWithIncorrectIssuer(false);
  }

  private String createTokenWithIncorrectIssuer(boolean isEmulatorMode) {
    Payload payload = tokenFactory.createTokenPayload();
    payload.setIssuer("https://incorrect.issuer.prefix/" + TestTokenFactory.PROJECT_ID);
    return isEmulatorMode ? tokenFactory.createUnsignedTokenForEmulator(payload)
        : tokenFactory.createToken(payload);
  }

  private String createTokenWithTimestamps(long issuedAtSeconds, long expirationSeconds) {
    return createTokenWithTimestamps(issuedAtSeconds, expirationSeconds, false);
  }

  private String createTokenWithTimestamps(long issuedAtSeconds, long expirationSeconds,
      boolean isEmulatorMode) {
    Payload payload = tokenFactory.createTokenPayload();
    payload.setIssuedAtTimeSeconds(issuedAtSeconds);
    payload.setExpirationTimeSeconds(expirationSeconds);
    return isEmulatorMode ? tokenFactory.createUnsignedTokenForEmulator(payload)
        : tokenFactory.createToken(payload);
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
    return createTokenWithTenantId(tenantId, false);
  }

  private String createTokenWithTenantId(String tenantId, boolean isEmulatorMode) {
    Payload payload = tokenFactory.createTokenPayload();
    payload.set("firebase", ImmutableMap.of("tenant", tenantId));
    return isEmulatorMode ? tokenFactory.createUnsignedTokenForEmulator(payload)
        : tokenFactory.createToken(payload);
  }

  private void setUpForEmulator() {
    TestUtils.setEnvironmentVariables(ImmutableMap.of(AUTH_EMULATOR_HOST, "localhost:9099"));
  }
}
