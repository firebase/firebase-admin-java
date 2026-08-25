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

package com.google.firebase.appcheck.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.appcheck.DecodedAppCheckToken;
import com.google.firebase.appcheck.FirebaseAppCheckException;
import com.google.firebase.appcheck.VerifyAppCheckTokenOptions;
import com.google.firebase.appcheck.VerifyAppCheckTokenResponse;
import com.google.firebase.internal.ApiClientUtils;
import com.google.firebase.internal.FirebaseProcessEnvironment;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.ExpiredJWTException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Collections;
import java.util.Date;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AppCheckTokenVerifierTest {

  private static final String PROJECT_ID = "test-project-id";
  private static final FirebaseOptions firebaseOptions =
      FirebaseOptions.builder()
          .setProjectId(PROJECT_ID)
          .setCredentials(TestUtils.getCertCredential(ServiceAccount.OWNER.asStream()))
          .build();
  private static final String ISSUER = "https://firebaseappcheck.googleapis.com/";
  private static final String AUDIENCE = "projects/" + PROJECT_ID;
  private static final String APP_ID = "test-app-id";
  private static final String KEY_ID = "key-id-1";

  @Mock private DefaultJWTProcessor<SecurityContext> mockJwtProcessor;

  private AppCheckTokenVerifier verifier;
  private KeyPair rsaKeyPair;
  private JWSHeader header;
  private JWTClaimsSet claims;
  private Date issueTime;
  private Date expirationTime;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);

    issueTime = new Date();
    expirationTime = new Date(System.currentTimeMillis() + 10000);

    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    rsaKeyPair = gen.generateKeyPair();

    FirebaseApp firebaseApp = FirebaseApp.initializeApp(firebaseOptions);

    HttpRequestFactory requestFactory = ApiClientUtils.newAuthorizedRequestFactory(firebaseApp);
    JsonFactory jsonFactory = ApiClientUtils.getDefaultJsonFactory();

    verifier =
        new AppCheckTokenVerifier(firebaseApp, requestFactory, jsonFactory, mockJwtProcessor);

    header =
        new JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(KEY_ID)
            .type(JOSEObjectType.JWT)
            .build();

    claims =
        new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .audience(AUDIENCE)
            .subject(APP_ID)
            .issueTime(issueTime)
            .expirationTime(expirationTime)
            .build();
  }

  @After
  public void tearDown() {
    FirebaseProcessEnvironment.clearCache();
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  private String createToken(JWSHeader header, JWTClaimsSet claims) throws Exception {
    SignedJWT jwt = new SignedJWT(header, claims);

    if (JWSAlgorithm.RS256.equals(header.getAlgorithm())
        || JWSAlgorithm.RS384.equals(header.getAlgorithm())
        || JWSAlgorithm.RS512.equals(header.getAlgorithm())) {
      jwt.sign(new RSASSASigner(rsaKeyPair.getPrivate()));
    } else if (JWSAlgorithm.HS256.equals(header.getAlgorithm())) {
      jwt.sign(new com.nimbusds.jose.crypto.MACSigner("12345678901234567890123456789012"));
    }

    return jwt.serialize();
  }

  @Test
  public void testVerifyToken_Success() throws Exception {
    String token = createToken(header, claims);
    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenReturn(claims);

    VerifyAppCheckTokenResponse response = verifier.verifyToken(token);

    assertNotNull(response);
    assertEquals(APP_ID, response.getAppId());
    assertNotNull(response.getToken());
    assertFalse(response.isAlreadyConsumed().isPresent());

    DecodedAppCheckToken decodedToken = response.getToken();
    assertEquals(APP_ID, decodedToken.getSubject());
    assertEquals(ISSUER, decodedToken.getIssuer());
    assertEquals(Collections.singletonList(AUDIENCE), decodedToken.getAudience());
    assertEquals(issueTime.getTime() / 1000L, decodedToken.getIssuedAt());
    assertEquals(expirationTime.getTime() / 1000L, decodedToken.getExpirationTime());
    assertEquals(ISSUER, decodedToken.getClaims().get("iss"));
    assertEquals(APP_ID, decodedToken.getClaims().get("sub"));
  }

  @Test
  public void testVerifyToken_WithConsumeOption_CallsBackend() throws Exception {
    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenReturn(claims);

    MockLowLevelHttpResponse mockResponse = new MockLowLevelHttpResponse();
    mockResponse.setContentType("application/json");
    mockResponse.setContent("{\"alreadyConsumed\": true}");

    MockHttpTransport transport =
        new MockHttpTransport.Builder().setLowLevelHttpResponse(mockResponse).build();

    HttpRequestFactory mockRequestFactory = transport.createRequestFactory();
    JsonFactory jsonFactory = ApiClientUtils.getDefaultJsonFactory();

    FirebaseApp app = FirebaseApp.getInstance();
    AppCheckTokenVerifier customVerifier =
        new AppCheckTokenVerifier(app, mockRequestFactory, jsonFactory, mockJwtProcessor);

    String token = createToken(header, claims);
    VerifyAppCheckTokenOptions options =
        VerifyAppCheckTokenOptions.builder().setConsume(true).build();
    VerifyAppCheckTokenResponse response = customVerifier.verifyToken(token, options);

    assertNotNull(response);
    assertEquals(APP_ID, response.getAppId());
    assertTrue(response.isAlreadyConsumed().isPresent());
    assertTrue(response.isAlreadyConsumed().get());
  }

  @Test
  public void testVerifyToken_NullOrEmptyToken_ThrowsException() {
    IllegalArgumentException ex1 =
        assertThrows(IllegalArgumentException.class, () -> verifier.verifyToken(null));
    assertTrue(ex1.getMessage().contains("must not be null or empty"));

    IllegalArgumentException ex2 =
        assertThrows(IllegalArgumentException.class, () -> verifier.verifyToken(""));
    assertTrue(ex2.getMessage().contains("must not be null or empty"));
  }

  @Test
  public void testVerifyHeader_IncorrectAlgorithm_ThrowsException() throws Exception {
    JWSHeader badHeader =
        new JWSHeader.Builder(JWSAlgorithm.RS384)
            .keyID(KEY_ID)
            .type(JOSEObjectType.JWT)
            .build();
    final String token = createToken(badHeader, claims);

    FirebaseAppCheckException ex =
        assertThrows(FirebaseAppCheckException.class, () -> verifier.verifyToken(token));
    assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("incorrect algorithm"));
  }

  @Test
  public void testVerifyHeader_MissingKid_ThrowsException() throws Exception {
    JWSHeader badHeader =
        new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build();
    final String token = createToken(badHeader, claims);

    FirebaseAppCheckException ex =
        assertThrows(FirebaseAppCheckException.class, () -> verifier.verifyToken(token));
    assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("no 'kid'"));
  }

  @Test
  public void testVerifyHeader_IncorrectType_ThrowsException() throws Exception {
    JWSHeader badHeader =
        new JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(KEY_ID)
            .type(JOSEObjectType.JOSE)
            .build();
    String token = createToken(badHeader, claims);

    FirebaseAppCheckException ex =
        assertThrows(FirebaseAppCheckException.class, () -> verifier.verifyToken(token));
    assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("incorrect 'typ'"));
  }

  @Test
  public void testVerifyClaims_MissingIssuer_ThrowsException() throws Exception {
    JWTClaimsSet badClaims =
        new JWTClaimsSet.Builder()
            .audience(AUDIENCE)
            .subject(APP_ID)
            .expirationTime(new Date(System.currentTimeMillis() + 10000))
            .build();
    String token = createToken(header, badClaims);
    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenReturn(badClaims);

    FirebaseAppCheckException ex =
        assertThrows(FirebaseAppCheckException.class, () -> verifier.verifyToken(token));
    assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("no 'iss'"));
  }

  @Test
  public void testVerifyClaims_IncorrectIssuer_ThrowsException() throws Exception {
    JWTClaimsSet badClaims =
        new JWTClaimsSet.Builder()
            .issuer("https://invalid-issuer.com")
            .audience(AUDIENCE)
            .subject(APP_ID)
            .expirationTime(new Date(System.currentTimeMillis() + 10000))
            .build();
    String token = createToken(header, badClaims);
    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenReturn(badClaims);

    FirebaseAppCheckException ex =
        assertThrows(FirebaseAppCheckException.class, () -> verifier.verifyToken(token));
    assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("incorrect issuer"));
  }

  @Test
  public void testVerifyClaims_MissingAudience_ThrowsException() throws Exception {
    JWTClaimsSet badClaims =
        new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .subject(APP_ID)
            .expirationTime(new Date(System.currentTimeMillis() + 10000))
            .build();
    String token = createToken(header, badClaims);
    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenReturn(badClaims);

    FirebaseAppCheckException ex =
        assertThrows(FirebaseAppCheckException.class, () -> verifier.verifyToken(token));
    assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("incorrect audience"));
  }

  @Test
  public void testVerifyClaims_IncorrectAudience_ThrowsException() throws Exception {
    JWTClaimsSet badClaims =
        new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .audience("projects/wrong-project-id")
            .subject(APP_ID)
            .expirationTime(new Date(System.currentTimeMillis() + 10000))
            .build();
    String token = createToken(header, badClaims);
    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenReturn(badClaims);

    FirebaseAppCheckException ex =
        assertThrows(FirebaseAppCheckException.class, () -> verifier.verifyToken(token));
    assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("incorrect audience"));
  }

  @Test
  public void testVerifyClaims_MissingSubject_ThrowsException() throws Exception {
    JWTClaimsSet badClaims =
        new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .audience(AUDIENCE)
            .expirationTime(new Date(System.currentTimeMillis() + 10000))
            .build();
    String token = createToken(header, badClaims);
    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenReturn(badClaims);

    FirebaseAppCheckException ex =
        assertThrows(FirebaseAppCheckException.class, () -> verifier.verifyToken(token));
    assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("empty 'sub'"));
  }

  @Test
  public void testVerifyToken_ExpiredToken_ThrowsException() throws Exception {
    String token = createToken(header, claims);
    when(mockJwtProcessor.process(any(SignedJWT.class), any()))
        .thenThrow(new ExpiredJWTException("Expired token"));

    FirebaseAppCheckException ex =
        assertThrows(FirebaseAppCheckException.class, () -> verifier.verifyToken(token));
    assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("has expired"));
  }

  @Test
  public void testVerifyToken_BadJOSEException_ThrowsException() throws Exception {
    String token = createToken(header, claims);
    when(mockJwtProcessor.process(any(SignedJWT.class), any()))
        .thenThrow(new BadJOSEException("Bad signature"));

    FirebaseAppCheckException ex =
        assertThrows(FirebaseAppCheckException.class, () -> verifier.verifyToken(token));
    assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("Failed to verify App Check token signature"));
  }

  @Test
  public void testVerifyToken_JOSEException_ThrowsException() throws Exception {
    String token = createToken(header, claims);
    when(mockJwtProcessor.process(any(SignedJWT.class), any()))
        .thenThrow(new JOSEException("Key error"));

    FirebaseAppCheckException ex =
        assertThrows(FirebaseAppCheckException.class, () -> verifier.verifyToken(token));
    assertEquals(ErrorCode.INTERNAL, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("Internal error processing App Check token"));
  }

  @Test
  public void testVerifyToken_ParseException_ThrowsException() {
    String invalidToken = "invalid-token-string";

    FirebaseAppCheckException ex =
        assertThrows(FirebaseAppCheckException.class, () -> verifier.verifyToken(invalidToken));
    assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("Failed to parse App Check JWT token"));
  }

  @Test
  public void testVerifyToken_Claims_Null() throws Exception {
    JWTClaimsSet noSubClaims = new JWTClaimsSet.Builder().build();
    String tokenString = createToken(header, noSubClaims);
    when(mockJwtProcessor.process(any(SignedJWT.class), any())).thenReturn(null);

    NullPointerException e =
        assertThrows(NullPointerException.class, () -> verifier.verifyToken(tokenString));
    assertTrue(e.getMessage().contains("JWTClaimsSet claims must not be null"));
  }

  @Test
  public void testVerifierWithoutProjectId() {
    FirebaseOptions localFirebaseOptions =
        FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.create(null))
            .build();

    FirebaseApp localApp =
        FirebaseApp.initializeApp(localFirebaseOptions, "no-project-id-app");

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> new AppCheckTokenVerifier(localApp));
    assertEquals("Project ID is required in FirebaseOptions.", e.getMessage());
  }

  @Test
  public void testCreateJwtProcessor_HandlesException() throws Exception {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "third");
    AppCheckTokenVerifier original = new AppCheckTokenVerifier(app);
    AppCheckTokenVerifier spyClass = spy(original);

    doThrow(new MalformedURLException("Simulated bad URL"))
        .when(spyClass)
        .createKeySource();

    Method method = AppCheckTokenVerifier.class.getDeclaredMethod("createJwtProcessor");
    method.setAccessible(true);

    try {
      method.invoke(spyClass);
    } catch (Exception e) {
      Throwable cause = e.getCause();
      assertEquals(IllegalStateException.class, cause.getClass());
      assertEquals("Invalid JWKS URL", cause.getMessage());
      assertTrue(cause.getCause() instanceof MalformedURLException);
    }
  }
}
