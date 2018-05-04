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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebSignature.Header;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.api.client.json.webtoken.JsonWebToken.Payload;
import com.google.api.client.testing.http.FixedClock;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.io.BaseEncoding;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.TestOnlyImplFirebaseAuthTrampolines;
import com.google.firebase.testing.ServiceAccount;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/** 
 * Unit tests for {@link FirebaseTokenVerifier}.
 */
public class FirebaseTokenVerifierTest {

  private static final JsonFactory FACTORY = new GsonFactory();
  private static final FixedClock CLOCK = new FixedClock(2002000L * 1000);
  private static final String PROJECT_ID = "proj-test-101";
  private static final String TOKEN_ISSUER =
      FirebaseTokenVerifier.ID_TOKEN_ISSUER_PREFIX + PROJECT_ID;
  private static final String COOKIE_ISSUER =
      FirebaseTokenVerifier.SESSION_COOKIE_ISSUER_PREFIX + PROJECT_ID;
  private static final String PRIVATE_KEY_ID = "aaaaaaaaaabbbbbbbbbbccccccccccdddddddddd";
  private static final String UID = "someUid";
  private static final String ALGORITHM = "RS256";
  private static final String LEGACY_CUSTOM_TOKEN =
      "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJkIjp7"
          + "InVpZCI6IjEiLCJhYmMiOiIwMTIzNDU2Nzg5fiFAIyQlXiYqKClfKy09YWJjZGVmZ2hpamtsbW5vcHF"
          + "yc3R1dnd4eXpBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWiwuLzsnW11cXDw"
          + "-P1wie318In0sInYiOjAsImlhdCI6MTQ4MDk4Mj"
          + "U2NH0.ZWEpoHgIPCAz8Q-cNFBS8jiqClTJ3j27yuRkQo-QxyI";

  @Rule public ExpectedException thrown = ExpectedException.none();

  private PrivateKey privateKey;
  private FirebaseTokenVerifier idTokenVerifier;
  private FirebaseTokenVerifier cookieVerifier;

  private void initCrypto(String privateKey, String certificate)
      throws NoSuchAlgorithmException, InvalidKeySpecException {
    byte[] privateBytes = BaseEncoding.base64().decode(privateKey);
    KeySpec spec = new PKCS8EncodedKeySpec(privateBytes);
    String serviceAccountCertificates =
        String.format("{\"%s\" : \"%s\"}", PRIVATE_KEY_ID, certificate);

    MockHttpTransport mockTransport =
        new MockHttpTransport.Builder()
            .setLowLevelHttpResponse(
                new MockLowLevelHttpResponse().setContent(serviceAccountCertificates))
            .build();
    KeyManagers keyManagers = KeyManagers.getDefault(mockTransport, CLOCK);
    this.privateKey = KeyFactory.getInstance("RSA").generatePrivate(spec);
    this.idTokenVerifier = FirebaseTokenVerifier.createIdTokenVerifier(
        PROJECT_ID, keyManagers, CLOCK);
    this.cookieVerifier = FirebaseTokenVerifier.createSessionCookieVerifier(
        PROJECT_ID, keyManagers, CLOCK);
  }

  @Before
  public void setUp() throws Exception {
    initCrypto(ServiceAccount.EDITOR.getPrivateKey(), ServiceAccount.EDITOR.getCert());
  }

  private JsonWebSignature.Header createHeader() {
    JsonWebSignature.Header header = new JsonWebSignature.Header();
    header.setAlgorithm(ALGORITHM);
    header.setType("JWT");
    header.setKeyId(PRIVATE_KEY_ID);
    return header;
  }

  private JsonWebToken.Payload createTokenPayload() {
    JsonWebToken.Payload payload = new JsonWebToken.Payload();
    payload.setIssuer(TOKEN_ISSUER);
    payload.setAudience(PROJECT_ID);
    payload.setIssuedAtTimeSeconds(CLOCK.currentTimeMillis() / 1000);
    payload.setExpirationTimeSeconds(CLOCK.currentTimeMillis() / 1000 + 3600);
    payload.setSubject(UID);
    return payload;
  }

  private JsonWebToken.Payload createCookiePayload() {
    JsonWebToken.Payload payload = new JsonWebToken.Payload();
    payload.setIssuer(COOKIE_ISSUER);
    payload.setAudience(PROJECT_ID);
    payload.setIssuedAtTimeSeconds(CLOCK.currentTimeMillis() / 1000);
    payload.setExpirationTimeSeconds(CLOCK.currentTimeMillis() / 1000 + 3600);
    payload.setSubject(UID);
    return payload;
  }

  private String createToken(JsonWebSignature.Header header, JsonWebToken.Payload payload)
      throws GeneralSecurityException, IOException {
    return JsonWebSignature.signUsingRsaSha256(privateKey, FACTORY, header, payload);
  }

  @Test
  public void verifyToken() throws Exception {
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), createTokenPayload()));

    IdToken.Payload payload = (IdToken.Payload) token.getClaims();
    assertTrue(payload.getAudienceAsList().contains(PROJECT_ID));
    assertEquals(TOKEN_ISSUER, payload.getIssuer());
    idTokenVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));

    token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), createCookiePayload()));
    thrown.expectMessage("Firebase ID token has incorrect \"iss\" (issuer) claim.");
    idTokenVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyCookie() throws Exception {
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), createCookiePayload()));

    IdToken.Payload payload = (IdToken.Payload) token.getClaims();
    assertTrue(payload.getAudienceAsList().contains(PROJECT_ID));
    assertEquals(COOKIE_ISSUER, payload.getIssuer());
    cookieVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));

    token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), createTokenPayload()));
    thrown.expectMessage("Firebase session cookie has incorrect \"iss\" (issuer) claim.");
    cookieVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyTokenFailure_MissingKeyId() throws Exception {
    Header header = createHeader();
    header.setKeyId(null);
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(header, createTokenPayload()));
    thrown.expectMessage("Firebase ID token has no \"kid\" claim.");
    idTokenVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyCookieFailure_MissingKeyId() throws Exception {
    Header header = createHeader();
    header.setKeyId(null);
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(header, createCookiePayload()));
    thrown.expectMessage("Firebase session cookie has no \"kid\" claim.");
    cookieVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyTokenFailure_MissingKeyId_CustomToken() throws Exception {
    Header header = createHeader();
    header.setKeyId(null);
    Payload payload = createTokenPayload();
    payload.setAudience(
        "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit"
            + ".v1.IdentityToolkit");
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(FACTORY, createToken(header, payload));
    thrown.expectMessage("verifyIdToken() expects an ID token, but was given a custom token.");
    idTokenVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyCookieFailure_MissingKeyId_CustomToken() throws Exception {
    Header header = createHeader();
    header.setKeyId(null);
    Payload payload = createCookiePayload();
    payload.setAudience(
        "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit"
            + ".v1.IdentityToolkit");
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(FACTORY, createToken(header, payload));
    thrown.expectMessage(
        "verifySessionCookie() expects a session cookie, but was given a custom token.");
    cookieVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyTokenFailure_IncorrectAlgorithm() throws Exception {
    Header header = createHeader();
    header.setAlgorithm("HS256");
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(header, createTokenPayload()));
    thrown.expectMessage("Firebase ID token has incorrect algorithm.");
    idTokenVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyCookieFailure_IncorrectAlgorithm() throws Exception {
    Header header = createHeader();
    header.setAlgorithm("HS256");
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(header, createCookiePayload()));
    thrown.expectMessage("Firebase session cookie has incorrect algorithm.");
    cookieVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyTokenFailure_IncorrectAudience() throws Exception {
    Payload payload = createTokenPayload();
    payload.setAudience(
        "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1."
            + "IdentityToolkit");
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), payload));
    thrown.expectMessage("Firebase ID token has incorrect \"aud\" (audience) claim.");
    idTokenVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyCookieFailure_IncorrectAudience() throws Exception {
    Payload payload = createCookiePayload();
    payload.setAudience(
        "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1."
            + "IdentityToolkit");
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), payload));
    thrown.expectMessage("Firebase session cookie has incorrect \"aud\" (audience) claim.");
    cookieVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyTokenFailure_IncorrectIssuer() throws Exception {
    Payload payload = createTokenPayload();
    payload.setIssuer("https://foobar.google.com/" + PROJECT_ID);
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), payload));
    thrown.expectMessage("Firebase ID token has incorrect \"iss\" (issuer) claim.");
    idTokenVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyCookieFailure_IncorrectIssuer() throws Exception {
    Payload payload = createCookiePayload();
    payload.setIssuer("https://foobar.google.com/" + PROJECT_ID);
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), payload));
    thrown.expectMessage("Firebase session cookie has incorrect \"iss\" (issuer) claim.");
    cookieVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyTokenFailure_MissingSubject() throws Exception {
    Payload payload = createTokenPayload();
    payload.setSubject(null);
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), payload));
    thrown.expectMessage("Firebase ID token has no \"sub\" (subject) claim.");
    idTokenVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyCookieFailure_MissingSubject() throws Exception {
    Payload payload = createCookiePayload();
    payload.setSubject(null);
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), payload));
    thrown.expectMessage("Firebase session cookie has no \"sub\" (subject) claim.");
    cookieVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyTokenFailure_EmptySubject() throws Exception {
    Payload payload = createTokenPayload();
    payload.setSubject("");
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), payload));
    thrown.expectMessage("Firebase ID token has an empty string \"sub\" (subject) claim.");
    idTokenVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyCookieFailure_EmptySubject() throws Exception {
    Payload payload = createCookiePayload();
    payload.setSubject("");
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), payload));
    thrown.expectMessage("Firebase session cookie has an empty string \"sub\" (subject) claim.");
    cookieVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyTokenFailure_LongSubject() throws Exception {
    Payload payload = createTokenPayload();
    payload.setSubject(
        "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuv"
            + "wxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz");
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), payload));
    thrown.expectMessage(
        "Firebase ID token has \"sub\" (subject) claim longer than 128 characters.");
    idTokenVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyCookieFailure_LongSubject() throws Exception {
    Payload payload = createCookiePayload();
    payload.setSubject(
        "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuv"
            + "wxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz");
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), payload));
    thrown.expectMessage(
        "Firebase session cookie has \"sub\" (subject) claim longer than 128 characters.");
    cookieVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyTokenFailure_NotYetIssued() throws Exception {
    Payload payload = createTokenPayload();
    payload.setIssuedAtTimeSeconds(System.currentTimeMillis() / 1000);
    payload.setExpirationTimeSeconds(System.currentTimeMillis() / 1000 + 3600);
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), payload));
    thrown.expectMessage("Firebase ID token has expired or is not yet valid.");
    idTokenVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyCookieFailure_NotYetIssued() throws Exception {
    Payload payload = createCookiePayload();
    payload.setIssuedAtTimeSeconds(System.currentTimeMillis() / 1000);
    payload.setExpirationTimeSeconds(System.currentTimeMillis() / 1000 + 3600);
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), payload));
    thrown.expectMessage("Firebase session cookie has expired or is not yet valid.");
    cookieVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyTokenFailure_Expired() throws Exception {
    Payload payload = createTokenPayload();
    payload.setIssuedAtTimeSeconds(0L);
    payload.setExpirationTimeSeconds(3600L);
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), payload));
    thrown.expectMessage("Firebase ID token has expired or is not yet valid.");
    idTokenVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyCookieFailure_Expired() throws Exception {
    Payload payload = createCookiePayload();
    payload.setIssuedAtTimeSeconds(0L);
    payload.setExpirationTimeSeconds(3600L);
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), payload));
    thrown.expectMessage("Firebase session cookie has expired or is not yet valid.");
    cookieVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyTokenFailure_WrongCert() throws Exception {
    initCrypto(ServiceAccount.OWNER.getPrivateKey(), ServiceAccount.NONE.getCert());
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), createTokenPayload()));
    thrown.expectMessage("Firebase ID token isn't signed by a valid public key.");
    idTokenVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));

    token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), createCookiePayload()));
    thrown.expectMessage("Firebase session cookie isn't signed by a valid public key.");
    cookieVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }

  @Test
  public void verifyTokenCertificateError() throws Exception {
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), createTokenPayload()));

    MockHttpTransport mockTransport = new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
        throw new IOException("Expected error");
      }
    };
    KeyManagers keyManagers = KeyManagers.getDefault(mockTransport, CLOCK);
    FirebaseTokenVerifier verifier = FirebaseTokenVerifier.createIdTokenVerifier(
        PROJECT_ID, keyManagers, CLOCK);
    try {
      verifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
      Assert.fail("No exception thrown");
    } catch (FirebaseAuthException expected) {
      assertTrue(expected.getCause() instanceof IOException);
      assertEquals("Expected error", expected.getCause().getMessage());
    }

    token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(
            FACTORY, createToken(createHeader(), createCookiePayload()));
    verifier = FirebaseTokenVerifier.createSessionCookieVerifier(PROJECT_ID, keyManagers, CLOCK);
    try {
      verifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
      Assert.fail("No exception thrown");
    } catch (FirebaseAuthException expected) {
      assertTrue(expected.getCause() instanceof IOException);
      assertEquals("Expected error", expected.getCause().getMessage());
    }
  }

  @Test
  public void legacyCustomToken() throws Exception {
    initCrypto(ServiceAccount.OWNER.getPrivateKey(), ServiceAccount.NONE.getCert());
    FirebaseToken token =
        TestOnlyImplFirebaseAuthTrampolines.parseToken(FACTORY, LEGACY_CUSTOM_TOKEN);
    thrown.expectMessage(
        "verifyIdToken() expects an ID token, but was given a legacy custom token.");
    idTokenVerifier.verifyTokenAndSignature(TestOnlyImplFirebaseAuthTrampolines.getToken(token));
  }
}
