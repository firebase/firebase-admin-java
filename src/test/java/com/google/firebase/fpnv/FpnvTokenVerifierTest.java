/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.fpnv;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.fpnv.internal.FirebasePnvTokenVerifier;
import com.google.firebase.internal.FirebaseProcessEnvironment;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Date;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class FpnvTokenVerifierTest {
  private static final FirebaseOptions firebaseOptions = FirebaseOptions.builder()
      .setCredentials(TestUtils.getCertCredential(ServiceAccount.OWNER.asStream()))
      .build();
  private static final String PROJECT_ID = "test-project-123";
  private static final String ISSUER = "https://fpnv.googleapis.com/projects/" + PROJECT_ID;
  private static final String[] AUD = new String[]{
      ISSUER,
      "https://google.com/projects/"
  };

  @Mock
  private DefaultJWTProcessor<SecurityContext> mockJwtProcessor;

  private FirebasePnvTokenVerifier verifier;
  private KeyPair rsaKeyPair;
  private ECKey ecKey;
  private JWSHeader header;

  @Before
  public void setUp() throws Exception {
    // noinspection resource
    MockitoAnnotations.openMocks(this);

    // Generate a real RSA key pair for signing test tokens
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    rsaKeyPair = gen.generateKeyPair();

    ecKey = new ECKeyGenerator(Curve.P_256).keyID("ec-key-id").generate();

    // Initialize Verifier and inject mock processor
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(firebaseOptions);
    verifier = new FirebasePnvTokenVerifier(firebaseApp);

    Field processorField = FirebasePnvTokenVerifier.class.getDeclaredField("jwtProcessor");
    processorField.setAccessible(true);
    processorField.set(verifier, mockJwtProcessor);

    // Create a valid ES256 token
    header = new JWSHeader.Builder(JWSAlgorithm.ES256)
        .keyID(ecKey.getKeyID())
        .type(JOSEObjectType.JWT)
        .build();
  }

  @After
  public void tearDown() {
    FirebaseProcessEnvironment.clearCache();
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  // --- Helper to create a signed JWT string ---
  private String createToken(JWSHeader header, JWTClaimsSet claims) throws Exception {
    SignedJWT jwt = new SignedJWT(header, claims);

    // Sign based on the algorithm in the header
    if (JWSAlgorithm.RS256.equals(header.getAlgorithm())) {
      jwt.sign(new RSASSASigner(rsaKeyPair.getPrivate()));
    } else if (JWSAlgorithm.HS256.equals(header.getAlgorithm())) {
      jwt.sign(new MACSigner("12345678901234567890123456789012")); // 32-byte secret
    } else if (JWSAlgorithm.ES256.equals(header.getAlgorithm())) {
      jwt.sign(new ECDSASigner(ecKey.toECPrivateKey()));
    }

    return jwt.serialize();
  }

  @Test
  public void testVerifyToken_Success() throws Exception {
    Date now = new Date();
    Date exp = new Date(now.getTime() + 3600 * 1000); // 1 hour valid

    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .audience(Arrays.asList(AUD))
        .subject("+15551234567")
        .issueTime(now)
        .expirationTime(exp)
        .build();

    String tokenString = createToken(header, claims);

    // 1. Mock the processor to return these claims (skipping real signature verification)
    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenReturn(claims);

    // 2. Execute
    FirebasePnvToken result = verifier.verifyToken(tokenString);

    // 3. Verify
    assertNotNull(result);
    assertEquals("+15551234567", result.getPhoneNumber());
    assertEquals(ISSUER, result.getIssuer());
  }

  @Test
  public void testVerifyToken_Header_WrongAlgorithm() throws Exception {
    // Create token with HS256 (HMAC) instead of ES256
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder().build();

    String tokenString = createToken(header, claims);

    // Should fail at header check, before reaching the processor
    FirebasePnvException e = assertThrows(FirebasePnvException.class, () ->
        verifier.verifyToken(tokenString)
    );

    assertEquals(FirebasePnvErrorCode.INVALID_ARGUMENT, e.getFpnvErrorCode());
    assertTrue(e.getMessage().contains("algorithm"));
  }

  @Test
  public void testVerifyToken_Header_MissingKeyId() throws Exception {
    // ES256 but missing 'kid'
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder().build();

    String tokenString = createToken(header, claims);

    FirebasePnvException e = assertThrows(FirebasePnvException.class, () ->
        verifier.verifyToken(tokenString)
    );

    assertEquals(FirebasePnvErrorCode.INVALID_ARGUMENT, e.getFpnvErrorCode());
    assertTrue(e.getMessage().contains("FPNV has no 'kid' claim"));
  }

  @Test
  public void testVerifyToken_Claims_Expired() throws Exception {
    Date past = new Date(System.currentTimeMillis() - 10000); // Expired

    JWTClaimsSet expiredClaims = new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .audience(ISSUER)
        .subject("+1555")
        .expirationTime(past)
        .build();

    String tokenString = createToken(header, expiredClaims);

    // Mock processor returning the expired claims
    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenReturn(expiredClaims);

    FirebasePnvException e = assertThrows(FirebasePnvException.class, () ->
        verifier.verifyToken(tokenString)
    );

    assertEquals(FirebasePnvErrorCode.TOKEN_EXPIRED, e.getFpnvErrorCode());
  }

  @Test
  public void testVerifyToken_Claims_WrongAudience() throws Exception {
    JWTClaimsSet badClaims = new JWTClaimsSet.Builder()
        .issuer("https://wrong.com") // Wrong issuer
        .audience(ISSUER)
        .subject("+1555")
        .expirationTime(new Date(System.currentTimeMillis() + 10000))
        .build();

    String tokenString = createToken(header, badClaims);
    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenReturn(badClaims);

    FirebasePnvException e = assertThrows(FirebasePnvException.class, () ->
        verifier.verifyToken(tokenString)
    );

    assertEquals(FirebasePnvErrorCode.INVALID_TOKEN, e.getFpnvErrorCode());
    assertTrue(e.getMessage().contains("Invalid audience."));
  }

  @Test
  public void testVerifyToken_Claims_NoSubject() throws Exception {
    JWTClaimsSet noSubClaims = new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .audience(ISSUER)
        .expirationTime(new Date(System.currentTimeMillis() + 10000))
        .build(); // Missing subject

    String tokenString = createToken(header, noSubClaims);
    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenReturn(noSubClaims);

    FirebasePnvException e = assertThrows(FirebasePnvException.class, () ->
        verifier.verifyToken(tokenString)
    );

    assertEquals(FirebasePnvErrorCode.INVALID_TOKEN, e.getFpnvErrorCode());
    assertTrue(e.getMessage().contains("Token has an empty 'sub' (phone number)"));
  }
}
