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

package com.google.firebase.fpnv.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.fpnv.FirebasePnvErrorCode;
import com.google.firebase.fpnv.FirebasePnvException;
import com.google.firebase.fpnv.FirebasePnvToken;
import com.google.firebase.internal.FirebaseProcessEnvironment;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.ExpiredJWTException;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class FpnvTokenVerifierTest {
  private static final String PROJECT_ID = "mock-project-id-2";
  private static final FirebaseOptions firebaseOptions = FirebaseOptions.builder()
      .setProjectId(PROJECT_ID)
      .setCredentials(TestUtils.getCertCredential(ServiceAccount.OWNER.asStream()))
      .build();
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
  private JWTClaimsSet claims;
  private final String  subject = "+15551234567";
  private final Date issueTime = new Date();
  private final Date expirationTime = new Date(System.currentTimeMillis() + 10000);

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

    // Create a valid JWTClaimsSet
    claims = new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .audience(Arrays.asList(AUD))
        .subject(subject)
        .issueTime(issueTime)
        .expirationTime(expirationTime)
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
  public void testVerifyToken_NullOrEmptyToken() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
        verifier.verifyToken("")
    );
    assertTrue(e.getMessage().contains("FPNV token must not be null"));
  }

  @Test
  public void testVerifyToken_Success() throws Exception {
    String tokenString = createToken(header, claims);

    // 1. Mock the processor to return these claims (skipping real signature verification)
    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenReturn(claims);

    // 2. Execute
    FirebasePnvToken result = verifier.verifyToken(tokenString);

    // 3. Verify
    assertNotNull(result);
    assertEquals(subject, result.getPhoneNumber());
    assertEquals(issueTime.getTime() / 1000L, result.getIssuedAt());
    assertEquals(expirationTime.getTime() / 1000L, result.getExpirationTime());
    assertEquals(Arrays.asList(AUD), result.getAudience());
    assertEquals(ISSUER, result.getIssuer());
    assertEquals(ISSUER, result.getClaims().get("iss"));
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
  public void testVerifyToken_Header_WrongTyp() throws Exception {
    JWSHeader header = new JWSHeader
        .Builder(JWSAlgorithm.ES256)
        .keyID(ecKey.getKeyID())
        .type(JOSEObjectType.JOSE)
        .build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder().build();

    String tokenString = createToken(header, claims);

    FirebasePnvException e = assertThrows(FirebasePnvException.class, () ->
        verifier.verifyToken(tokenString)
    );

    assertEquals(FirebasePnvErrorCode.INVALID_ARGUMENT, e.getFpnvErrorCode());
    assertTrue(e.getMessage().contains("has incorrect 'typ'"));
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
  public void testVerifyToken_Claims_Null() throws Exception {
    JWTClaimsSet noSubClaims = new JWTClaimsSet.Builder()
        .build();

    String tokenString = createToken(header, noSubClaims);
    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenReturn(null);

    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
        verifier.verifyToken(tokenString)
    );

    assertTrue(e.getMessage().contains("JWTClaimsSet claims must not be null"));
  }

  @Test
  public void testVerifyToken_Claims_NoIssuer() throws Exception {
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .audience(ISSUER)
        .expirationTime(new Date(System.currentTimeMillis() + 10000))
        .build();

    String tokenString = createToken(header, claims);
    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenReturn(claims);

    FirebasePnvException e = assertThrows(FirebasePnvException.class, () ->
        verifier.verifyToken(tokenString)
    );

    assertEquals(FirebasePnvErrorCode.INVALID_ARGUMENT, e.getFpnvErrorCode());
    assertTrue(e.getMessage().contains("FPNV token has no 'iss' (issuer) claim."));
  }

  @Test
  public void testVerifyToken_Claims_Expired() throws Exception {
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .audience(ISSUER)
        .subject("+1555")
        .expirationTime(new Date(System.currentTimeMillis() + 10000))
        .build();

    String tokenString = createToken(header, claims);
    ExpiredJWTException error = new ExpiredJWTException("Bad token");

    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenThrow(error);

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
        .subject(subject)
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
  public void testVerifyToken_Claims_EmptyAudience() throws Exception {
    JWTClaimsSet badClaims = new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .audience(Collections.emptyList())
        .subject(subject)
        .expirationTime(new Date(System.currentTimeMillis() + 10000))
        .build();

    String tokenString = createToken(header, badClaims);
    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenReturn(badClaims);

    FirebasePnvException e = assertThrows(FirebasePnvException.class, () ->
        verifier.verifyToken(tokenString)
    );

    assertEquals(FirebasePnvErrorCode.INVALID_TOKEN, e.getFpnvErrorCode());
    assertTrue(e.getMessage().contains("Invalid audience. Expected to contain: "));
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

  @Test
  public void testVerifyToken_ParseException() {
    FirebasePnvException e = assertThrows(FirebasePnvException.class, () ->
        verifier.verifyToken(" ")
    );
    assertEquals(FirebasePnvErrorCode.INVALID_TOKEN, e.getFpnvErrorCode());
    assertTrue(e.getMessage().contains("Failed to parse JWT token"));
  }

  @Test
  public void testVerifyToken_BadJOSEException() throws Exception {
    String tokenString = createToken(header, claims);
    String errorMessage = "BadJOSEException";
    BadJOSEException error = new BadJOSEException(errorMessage);

    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenThrow(error);

    FirebasePnvException e = assertThrows(FirebasePnvException.class, () ->
        verifier.verifyToken(tokenString)
    );

    assertEquals(FirebasePnvErrorCode.INVALID_TOKEN, e.getFpnvErrorCode());
    assertEquals(
        "Check your project: "
            + PROJECT_ID
            + ". FPNV token is invalid: "
            + errorMessage,
        e.getMessage()
    );
  }

  @Test
  public void testVerifyToken_JOSEException() throws Exception {
    String tokenString = createToken(header, claims);
    String errorMessage = "JOSEException";
    JOSEException error = new JOSEException(errorMessage);

    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenThrow(error);

    FirebasePnvException e = assertThrows(FirebasePnvException.class, () ->
        verifier.verifyToken(tokenString)
    );

    assertEquals(FirebasePnvErrorCode.INTERNAL_ERROR, e.getFpnvErrorCode());
    assertEquals(
        "Check your project: "
            + PROJECT_ID
            + ". Failed to verify FPNV token signature: "
            + errorMessage,
        e.getMessage()
    );
  }

  @Test
  public void testVerifyToken_EmptyProjectId() {
    String jsonString = "{\n"
        + "  \"type\": \"service_account\",\n"
        + "  \"project_id\": \"\",\n"
        + "  \"private_key_id\": \"mock-key-id-1\",\n"
        + "  \"private_key\": \"-----BEGIN PRIVATE KEY-----\\n"
        + "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDrqbYkSM6sixYX"
        + "\\ngClj447vB/04RwUFykc54ntbyvbymUOJgyAUJLNjEIig60OIXpvdwt/xzyxvmns4"
        + "\\nivbmWxJpANBDUziUt7AwLkYAEQxkfcP72PFiSGNkFPrxzZWEGcK3E4slaEe6xdFa"
        + "\\n0AuefIcDSwIMmRP7+20unThJw1jCG4rQTbnuEwM/4U5mK1nXC3s3mzf8p9IHZ5Xi"
        + "\\nEBBxKWY1c9Ly6VNwDR7xxh8sLfEJmG57C+iJRZLUloAWqQlnRM0vK5Z6MmMwnSpZ"
        + "\\nW7KgYEl13WEMhR4ZaCZ5Gy5O+5x4Do363459obDbWK67grcx/qtFnyQq8HVDKyI9"
        + "\\nJZpVpwR1AgMBAAECggEAa8AcHLkBbljlz/b0dcydFOO1Pt8SB9S1/lx0hMLnaIL1"
        + "\\nI1HGAA/LyZbMsa8AIMEJSTsKA9jy+1BJ2M+JFkg7wbDyiGXrr+vQ7iaqMOuam/P5"
        + "\\nARTvQT3R2/fPyXFzVIQmyGhyLbdhXJ+IGpqXRW6wmKvaEwKG5abPBAo0q11bHtxy"
        + "\\nUV9RMXiW6cvzqgkthb7lO3k1ae4s+juiCPZFFpgTT9LkHYxf0XkpAZCvdUJlmf+B"
        + "\\ngc6bgobtN/zQ3l2hjGHFnNFhaQtNzd2xGcAuAR+BmoOx37YIn7ddYtm4RUgKnjZm"
        + "\\nFesOC8YumD1S2ioHsXXCb+BXVrARJTTFxIFboiVGnQKBgQD67nXZfsuKXE/BPh/X"
        + "\\nMMDjtcoYf4T++3BNe01I69fnfB4DAQ5yQ1dA7MTe7tQUO99e5OzhZJhAMsQYc82D"
        + "\\nLodOpYAeQCa0wN8eYuw5PAIe5G0+62bwNIy9WljcePQl2nkl4rU7fFZLu6yERvRQ"
        + "\\nA+kn5Dx+wyVYTvDLeE13x3DoVwKBgQDwbEySxyPtxmuDPw2s8rdW9ZVYs71hzULu"
        + "\\nc9RaPpzSdSzOEewgGOygL3wcqENcU3nT3baMlqZEp/BIL8z1bf8UzQRGebimfWG8"
        + "\\nlUL1BzLjZMnGXMA7+bhL+iQ98E5BBXHC7I8ir4Qej5235N4UPvqTuhNCisiGod8F"
        + "\\nE1ScFGSqEwKBgQCzv9HHxR5EtK+k+72PRqtF8tkcB2zbwn3F4wePrvHwLmbJPB5/"
        + "\\nF2IPbgvwriBZhjISJebR5l9xzWvPIFUdHV1rpv5JrSaM4IRzneUdcrEKNBNVuQb6"
        + "\\nFoqisW9qL3KlEwUpcGbmf8DJa1y/PJySHNsN6l6zZ1L/GT1AY6MKpGFq7QKBgQCw"
        + "\\nvNw5lhzqYU+Npt91wONYEKaeE1tntw253vo+8QI1kB/EyNYM7mWch+uz4VnLWC4Z"
        + "\\nukXE6cYGeHIhjsobraWzc9btu/MqqMcda5hSKd2V3fSaVnqWXEfHynWz9qCAGfF7"
        + "\\n+oxqUh5MnQSzN5KtzXJFAKfB5eXtWrdossIjDrbFcwKBgQDOUO39/wRP781pf8vV"
        + "\\naEzklwT64QlbgqK5iBntKvQLTy3xPMqtzJd2RGfTwgMQ6G2PV6W4WHKj9bTpujcM"
        + "\\nxk7rLcIEXovagJC82ZCGujo5joJ3fam9/q9I5ju5xw13yMOHyeyzsErCpSP/Xr8f"
        + "\\nr5uOncBw2twGqOZ+FlQtCdE1Dg==\\n-----END PRIVATE KEY-----\\n\",\n"
        + "  \"client_email\": \"mock-project-id-none@mock-project-id.iam.gserviceaccount.com\",\n"
        + "  \"client_id\": \"1234567890\",\n"
        + "  \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n"
        + "  \"token_uri\": \"https://accounts.google.com/o/oauth2/token\",\n"
        + "  \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\",\n"
        + "  \"client_x509_cert_url\": \"https://www.googleapis.com/robot/v1/metadata/x509/mock-project-id-none%40mock-project-id.iam.gserviceaccount.com\"\n"
        + "}";

    FirebaseOptions localFirebaseOptions = FirebaseOptions.builder()
        .setCredentials(TestUtils.getCertCredential(
                new ByteArrayInputStream(
                    jsonString.getBytes(StandardCharsets.UTF_8)
                )
            )
        ).build();

    // Initialize Verifier and inject mock processor
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(localFirebaseOptions, "second");

    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
        new FirebasePnvTokenVerifier(firebaseApp)
    );

    assertEquals("Project ID is required in FirebaseOptions.", e.getMessage());
  }

  @Test
  public void testCreateJwtProcessor_HandlesException() throws Exception {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(firebaseOptions, "third");
    // 1. Create the spy
    FirebasePnvTokenVerifier original = new FirebasePnvTokenVerifier(firebaseApp);
    FirebasePnvTokenVerifier spyClass = spy(original);

    // 2. Force the protected method to throw the checked exception
    doThrow(new MalformedURLException("Simulated bad URL"))
        .when(spyClass).createKeySource();

    // 3. Invoke and catch
    Method method = FirebasePnvTokenVerifier.class.getDeclaredMethod("createJwtProcessor");
    method.setAccessible(true);

    try {
      method.invoke(spyClass);
    } catch (Exception e) {
      Throwable cause = e.getCause();
      assertEquals(RuntimeException.class, cause.getClass());
      assertEquals("Invalid JWKS URL", cause.getMessage());
      assertTrue(cause.getCause() instanceof MalformedURLException);
    }
  }

}
