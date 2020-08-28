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
import com.google.firebase.ErrorCode;
import com.google.firebase.testing.ServiceAccount;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
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
    } catch (FirebaseAuthException e) {
      String message = "Firebase test token has incorrect algorithm. "
          + "Expected \"RS256\" but got \"HSA\". "
          + "See https://test.doc.url for details on how to retrieve a test token.";
      checkInvalidTokenException(e, message);
    }
  }

  @Test
  public void testVerifyTokenIncorrectAudience() {
    String token = createTokenWithIncorrectAudience();

    try {
      tokenVerifier.verifyToken(token);
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
    long tenMinutesIntoTheFuture = (TestTokenFactory.CLOCK.currentTimeMillis() / 1000)
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
    long twoHoursInPast = (TestTokenFactory.CLOCK.currentTimeMillis() / 1000)
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
  public void testVerifyTokenSignatureMismatch() {
    String token = tokenFactory.createToken();
    GooglePublicKeysManager publicKeysManager = newPublicKeysManager(
        ServiceAccount.NONE.getCert());
    FirebaseTokenVerifier tokenVerifier = newTestTokenVerifier(publicKeysManager);

    try {
      tokenVerifier.verifyToken(token);
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
      Assert.fail("No exception thrown");
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
        .verifyToken(tokenFactory.createToken());
    } catch (FirebaseAuthException e) {
      assertEquals(AuthErrorCode.TENANT_ID_MISMATCH, e.getAuthErrorCode());
      assertEquals(
          "The tenant ID ('') of the token did not match the expected value ('TENANT_ID')",
          e.getMessage());
    }
  }

  @Test
  public void testVerifyTokenUnexpectedTenantId() {
    try {
      fullyPopulatedBuilder()
        .build()
        .verifyToken(createTokenWithTenantId("TENANT_ID"));
    } catch (FirebaseAuthException e) {
      assertEquals(AuthErrorCode.TENANT_ID_MISMATCH, e.getAuthErrorCode());
      assertEquals(
          "The tenant ID ('TENANT_ID') of the token did not match the expected value ('')",
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
    JsonWebSignature.Header header = tokenFactory.createHeader();
    header.setKeyId(null);
    return tokenFactory.createToken(header);
  }

  private String createTokenWithSubject(String sub) {
    Payload payload = tokenFactory.createTokenPayload();
    payload.setSubject(sub);
    return tokenFactory.createToken(payload);
  }

  private String createCustomToken() {
    JsonWebSignature.Header header = tokenFactory.createHeader();
    header.setKeyId(null);
    Payload payload = tokenFactory.createTokenPayload();
    payload.setAudience(CUSTOM_TOKEN_AUDIENCE);
    return tokenFactory.createToken(header, payload);
  }

  private String createTokenWithIncorrectAlgorithm() {
    JsonWebSignature.Header header = tokenFactory.createHeader();
    header.setAlgorithm("HSA");
    return tokenFactory.createToken(header);
  }

  private String createTokenWithIncorrectAudience() {
    Payload payload = tokenFactory.createTokenPayload();
    payload.setAudience("invalid-audience");
    return tokenFactory.createToken(payload);
  }

  private String createTokenWithIncorrectIssuer() {
    Payload payload = tokenFactory.createTokenPayload();
    payload.setIssuer("https://incorrect.issuer.prefix/" + TestTokenFactory.PROJECT_ID);
    return tokenFactory.createToken(payload);
  }

  private String createTokenWithTimestamps(long issuedAtSeconds, long expirationSeconds) {
    Payload payload = tokenFactory.createTokenPayload();
    payload.setIssuedAtTimeSeconds(issuedAtSeconds);
    payload.setExpirationTimeSeconds(expirationSeconds);
    return tokenFactory.createToken(payload);
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
    Payload payload = tokenFactory.createTokenPayload();
    payload.set("firebase", ImmutableMap.of("tenant", tenantId));
    return tokenFactory.createToken(payload);
  }
}
