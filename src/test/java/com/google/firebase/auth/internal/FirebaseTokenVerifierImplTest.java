/*
 * Copyright 2019 Google Inc.
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

import static org.junit.Assert.assertEquals;
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
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.testing.ServiceAccount;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

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

  @Rule
  public ExpectedException thrown = ExpectedException.none();

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

    FirebaseIdToken idToken = tokenVerifier.verifyToken(token);

    FirebaseIdToken.Payload payload = idToken.getPayload();
    assertTrue(payload.getAudienceAsList().contains(TestTokenFactory.PROJECT_ID));
    assertEquals(TEST_TOKEN_ISSUER, payload.getIssuer());
    assertEquals(TestTokenFactory.UID, payload.getUid());
    assertEquals(TestTokenFactory.PROJECT_ID, payload.getAudience());
  }

  @Test
  public void testVerifyTokenWithoutKeyId() throws Exception {
    String token = createTokenWithoutKeyId();

    thrown.expectMessage("Firebase test token has no \"kid\" claim.");
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void testVerifyTokenFirebaseCustomToken() throws Exception {
    String token = createCustomToken();

    thrown.expectMessage("verifyTestToken() expects a test token, but was given a custom token.");
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void testVerifyTokenIncorrectAlgorithm() throws Exception {
    String token = createTokenWithIncorrectAlgorithm();

    thrown.expectMessage("Firebase test token has incorrect algorithm.");
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void testVerifyTokenIncorrectAudience() throws Exception {
    String token = createTokenWithIncorrectAudience();

    thrown.expectMessage("Firebase test token has incorrect \"aud\" (audience) claim.");
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void testVerifyTokenIncorrectIssuer() throws Exception {
    String token = createTokenWithIncorrectIssuer();

    thrown.expectMessage("Firebase test token has incorrect \"iss\" (issuer) claim.");
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void testVerifyTokenMissingSubject() throws Exception {
    String token = createTokenWithSubject(null);

    thrown.expectMessage("Firebase test token has no \"sub\" (subject) claim.");
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void testVerifyTokenEmptySubject() throws Exception {
    String token = createTokenWithSubject("");

    thrown.expectMessage("Firebase test token has an empty string \"sub\" (subject) claim.");
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void testVerifyTokenLongSubject() throws Exception {
    String token = createTokenWithSubject(Strings.repeat("a", 129));

    thrown.expectMessage(
        "Firebase test token has \"sub\" (subject) claim longer than 128 characters.");
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void testVerifyTokenIssuedAtInFuture() throws Exception {
    long tenMinutesIntoTheFuture = (TestTokenFactory.CLOCK.currentTimeMillis() / 1000)
        + TimeUnit.MINUTES.toSeconds(10);
    String token = createTokenWithTimestamps(
        tenMinutesIntoTheFuture,
        tenMinutesIntoTheFuture + TimeUnit.HOURS.toSeconds(1));

    thrown.expectMessage("Firebase test token has expired or is not yet valid.");
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void testVerifyTokenExpired() throws Exception {
    long twoHoursInPast = (TestTokenFactory.CLOCK.currentTimeMillis() / 1000)
        - TimeUnit.HOURS.toSeconds(2);
    String token = createTokenWithTimestamps(
        twoHoursInPast,
        twoHoursInPast + TimeUnit.HOURS.toSeconds(1));

    thrown.expectMessage("Firebase test token has expired or is not yet valid.");
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void testVerifyTokenIncorrectCert() throws Exception {
    String token = tokenFactory.createToken();
    GooglePublicKeysManager publicKeysManager = newPublicKeysManager(
        ServiceAccount.NONE.getCert());
    FirebaseTokenVerifier tokenVerifier = newTestTokenVerifier(publicKeysManager);

    thrown.expectMessage("Failed to verify the signature of Firebase test token. "
        + "See https://test.doc.url for details on how to retrieve a test token.");
    tokenVerifier.verifyToken(token);
  }

  @Test
  public void verifyTokenCertificateError() {
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
    } catch (FirebaseAuthException expected) {
      assertTrue(expected.getCause() instanceof IOException);
      assertEquals("Expected error", expected.getCause().getMessage());
    }
  }

  @Test
  public void testLegacyCustomToken() throws Exception {
    thrown.expectMessage(
        "verifyTestToken() expects a test token, but was given a legacy custom token.");
    tokenVerifier.verifyToken(LEGACY_CUSTOM_TOKEN);
  }

  @Test
  public void testMalformedToken() throws Exception {
    thrown.expectMessage(
        "Decoding Firebase test token failed. Make sure you passed a string that "
            + "represents a complete and valid JWT. See https://test.doc.url for details on "
            + "how to retrieve a test token.");
    tokenVerifier.verifyToken("not.a.jwt");
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
    return new FirebaseTokenVerifierImpl.Builder()
        .setShortName("test token")
        .setMethod("verifyTestToken()")
        .setDocUrl("https://test.doc.url")
        .setJsonFactory(TestTokenFactory.JSON_FACTORY)
        .setPublicKeysManager(publicKeysManager)
        .setIdTokenVerifier(newIdTokenVerifier())
        .build();
  }

  private FirebaseTokenVerifierImpl.Builder fullyPopulatedBuilder() {
    return new FirebaseTokenVerifierImpl.Builder()
        .setShortName("test token")
        .setMethod("verifyTestToken()")
        .setDocUrl("https://test.doc.url")
        .setJsonFactory(TestTokenFactory.JSON_FACTORY)
        .setPublicKeysManager(newPublicKeysManager(ServiceAccount.EDITOR.getCert()))
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
}
