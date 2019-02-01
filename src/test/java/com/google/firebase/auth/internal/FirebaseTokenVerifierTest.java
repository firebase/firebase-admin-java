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

import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.auth.openidconnect.IdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebSignature.Header;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.api.client.json.webtoken.JsonWebToken.Payload;
import com.google.api.client.testing.http.FixedClock;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.testing.ServiceAccount;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class FirebaseTokenVerifierTest {

  private static final JsonFactory JSON_FACTORY = Utils.getDefaultJsonFactory();
  private static final FixedClock CLOCK = new FixedClock(2002000L * 1000);
  private static final String PROJECT_ID = "proj-test-101";
  private static final String PRIVATE_KEY_ID = "aaaaaaaaaabbbbbbbbbbccccccccccdddddddddd";
  private static final String UID = "someUid";
  private static final String RS256 = "RS256";
  private static final String LEGACY_CUSTOM_TOKEN =
      "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJkIjp7"
          + "InVpZCI6IjEiLCJhYmMiOiIwMTIzNDU2Nzg5fiFAIyQlXiYqKClfKy09YWJjZGVmZ2hpamtsbW5vcHF"
          + "yc3R1dnd4eXpBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWiwuLzsnW11cXDw"
          + "-P1wie318In0sInYiOjAsImlhdCI6MTQ4MDk4Mj"
          + "U2NH0.ZWEpoHgIPCAz8Q-cNFBS8jiqClTJ3j27yuRkQo-QxyI";
  private static final String TEST_TOKEN_ISSUER = "https://test.token.issuer";

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private FirebaseTokenVerifier idTokenVerifier;
  private PrivateKey privateKey;

  @Before
  public void setUp() throws Exception {
    ServiceAccount serviceAccount = ServiceAccount.EDITOR;
    GooglePublicKeysManager publicKeysManager = newPublicKeysManager(serviceAccount.getCert());
    this.idTokenVerifier = newTestTokenVerifier(publicKeysManager);
    this.privateKey = parsePrivateKey(serviceAccount.getPrivateKey());
  }

  @Test
  public void verifyIdToken() throws Exception {
    String token = createToken(createHeader(), createTokenPayload());

    FirebaseIdToken idToken = idTokenVerifier.verifyToken(token);

    IdToken.Payload payload = idToken.getPayload();
    assertTrue(payload.getAudienceAsList().contains(PROJECT_ID));
    assertEquals(TEST_TOKEN_ISSUER, payload.getIssuer());
  }

  @Test
  public void verifyTokenFailure_MissingKeyId() throws Exception {
    Header header = createHeader();
    header.setKeyId(null);
    String token = createToken(header, createTokenPayload());
    thrown.expectMessage("Firebase test token has no \"kid\" claim.");

    idTokenVerifier.verifyToken(token);
  }

  @Test
  public void verifyTokenFailure_MissingKeyId_CustomToken() throws Exception {
    Header header = createHeader();
    header.setKeyId(null);
    Payload payload = createTokenPayload();
    payload.setAudience(
        "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit"
            + ".v1.IdentityToolkit");
    String token = createToken(header, payload);
    thrown.expectMessage("verifyTestToken() expects a test token, but was given a custom token.");

    idTokenVerifier.verifyToken(token);
  }

  @Test
  public void verifyTokenFailure_IncorrectAlgorithm() throws Exception {
    Header header = createHeader();
    header.setAlgorithm("HS256");
    String token = createToken(header, createTokenPayload());
    thrown.expectMessage("Firebase test token has incorrect algorithm.");

    idTokenVerifier.verifyToken(token);
  }

  @Test
  public void verifyTokenFailure_IncorrectAudience() throws Exception {
    Payload payload = createTokenPayload();
    payload.setAudience("invalid-audience");
    String token = createToken(createHeader(), payload);

    thrown.expectMessage("Firebase test token has incorrect \"aud\" (audience) claim.");
    idTokenVerifier.verifyToken(token);
  }

  @Test
  public void verifyTokenFailure_IncorrectIssuer() throws Exception {
    Payload payload = createTokenPayload();
    payload.setIssuer("https://foobar.google.com/" + PROJECT_ID);
    String token = createToken(createHeader(), payload);

    thrown.expectMessage("Firebase test token has incorrect \"iss\" (issuer) claim.");
    idTokenVerifier.verifyToken(token);
  }

  @Test
  public void verifyTokenFailure_MissingSubject() throws Exception {
    Payload payload = createTokenPayload();
    payload.setSubject(null);
    String token = createToken(createHeader(), payload);

    thrown.expectMessage("Firebase test token has no \"sub\" (subject) claim.");
    idTokenVerifier.verifyToken(token);
  }

  @Test
  public void verifyTokenFailure_EmptySubject() throws Exception {
    Payload payload = createTokenPayload();
    payload.setSubject("");
    String token = createToken(createHeader(), payload);

    thrown.expectMessage("Firebase test token has an empty string \"sub\" (subject) claim.");
    idTokenVerifier.verifyToken(token);
  }

  @Test
  public void verifyTokenFailure_LongSubject() throws Exception {
    Payload payload = createTokenPayload();
    payload.setSubject(
        "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuv"
            + "wxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz");
    String token = createToken(createHeader(), payload);

    thrown.expectMessage(
        "Firebase test token has \"sub\" (subject) claim longer than 128 characters.");
    idTokenVerifier.verifyToken(token);
  }

  @Test
  public void verifyTokenFailure_NotYetIssued() throws Exception {
    Payload payload = createTokenPayload();
    payload.setIssuedAtTimeSeconds(System.currentTimeMillis() / 1000);
    payload.setExpirationTimeSeconds(System.currentTimeMillis() / 1000 + 3600);
    String token = createToken(createHeader(), payload);

    thrown.expectMessage("Firebase test token has expired or is not yet valid.");
    idTokenVerifier.verifyToken(token);
  }

  @Test
  public void verifyTokenFailure_Expired() throws Exception {
    Payload payload = createTokenPayload();
    payload.setIssuedAtTimeSeconds(0L);
    payload.setExpirationTimeSeconds(3600L);
    String token = createToken(createHeader(), payload);

    thrown.expectMessage("Firebase test token has expired or is not yet valid.");
    idTokenVerifier.verifyToken(token);
  }

  @Test
  public void verifyTokenFailure_WrongCert() throws Exception {
    GooglePublicKeysManager publicKeysManager = newPublicKeysManager(ServiceAccount.NONE.getCert());
    FirebaseTokenVerifier idTokenVerifier = newTestTokenVerifier(publicKeysManager);
    String token = createToken(createHeader(), createTokenPayload());

    thrown.expectMessage("Firebase test token isn't signed by a valid public key.");
    idTokenVerifier.verifyToken(token);
  }

  @Test
  public void verifyTokenCertificateError() throws Exception {
    MockHttpTransport failingTransport = new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
        throw new IOException("Expected error");
      }
    };
    GooglePublicKeysManager publicKeysManager = newPublicKeysManager(failingTransport);
    FirebaseTokenVerifier idTokenVerifier = newTestTokenVerifier(publicKeysManager);
    String token = createToken(createHeader(), createTokenPayload());

    try {
      idTokenVerifier.verifyToken(token);
      Assert.fail("No exception thrown");
    } catch (FirebaseAuthException expected) {
      assertTrue(expected.getCause() instanceof IOException);
      assertEquals("Expected error", expected.getCause().getMessage());
    }
  }

  @Test
  public void legacyCustomToken() throws Exception {
    thrown.expectMessage(
        "verifyTestToken() expects a test token, but was given a legacy custom token.");
    idTokenVerifier.verifyToken(LEGACY_CUSTOM_TOKEN);
  }

  private PrivateKey parsePrivateKey(String privateKey) throws GeneralSecurityException {
    byte[] privateBytes = BaseEncoding.base64().decode(privateKey);
    KeySpec spec = new PKCS8EncodedKeySpec(privateBytes);
    return KeyFactory.getInstance("RSA").generatePrivate(spec);
  }

  private GooglePublicKeysManager newPublicKeysManager(String certificate) {
    String serviceAccountCertificates =
        String.format("{\"%s\" : \"%s\"}", PRIVATE_KEY_ID, certificate);
    HttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(
            new MockLowLevelHttpResponse().setContent(serviceAccountCertificates))
        .build();
    return newPublicKeysManager(transport);
  }

  private GooglePublicKeysManager newPublicKeysManager(HttpTransport transport) {
    return new GooglePublicKeysManager.Builder(
        transport, FirebaseTokenUtils.UNQUOTED_CTRL_CHAR_JSON_FACTORY)
        .setClock(CLOCK)
        .setPublicCertsEncodedUrl("https://test.cert.url")
        .build();
  }

  private FirebaseTokenVerifier newTestTokenVerifier(GooglePublicKeysManager publicKeysManager) {
    IdTokenVerifier idTokenVerifier = new IdTokenVerifier.Builder()
        .setClock(CLOCK)
        .setAudience(ImmutableList.of(PROJECT_ID))
        .setIssuer(TEST_TOKEN_ISSUER)
        .build();
    return new FirebaseTokenVerifier.Builder()
        .setShortName("test token")
        .setMethod("verifyTestToken()")
        .setDocUrl("https://test.doc.url")
        .setJsonFactory(JSON_FACTORY)
        .setPublicKeysManager(publicKeysManager)
        .setIdTokenVerifier(idTokenVerifier)
        .build();
  }

  private JsonWebSignature.Header createHeader() {
    JsonWebSignature.Header header = new JsonWebSignature.Header();
    header.setAlgorithm(RS256);
    header.setType("JWT");
    header.setKeyId(PRIVATE_KEY_ID);
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

  private String createToken(JsonWebSignature.Header header, JsonWebToken.Payload payload)
      throws GeneralSecurityException, IOException {
    return JsonWebSignature.signUsingRsaSha256(privateKey, JSON_FACTORY, header, payload);
  }
}
