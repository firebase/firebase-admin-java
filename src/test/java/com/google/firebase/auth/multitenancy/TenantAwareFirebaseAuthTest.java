/*
 * Copyright 2020 Google LLC
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

package com.google.firebase.auth.multitenancy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.json.GenericJson;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.base.Suppliers;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthTest;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.auth.MockTokenVerifier;
import com.google.firebase.auth.SessionCookieOptions;
import com.google.firebase.testing.TestResponseInterceptor;
import com.google.firebase.testing.TestUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;

public class TenantAwareFirebaseAuthTest {

  private static final String TENANT_ID = "test-tenant";

  private static final String AUTH_BASE_URL = "/v1/projects/test-project-id/tenants/" + TENANT_ID;

  private static final SessionCookieOptions COOKIE_OPTIONS = SessionCookieOptions.builder()
      .setExpiresIn(TimeUnit.HOURS.toMillis(1))
      .build();

  private static final FirebaseAuthException AUTH_EXCEPTION = new FirebaseAuthException(
      ErrorCode.INVALID_ARGUMENT, "Test error message", null, null, null);

  private static final String CREATE_COOKIE_RESPONSE = TestUtils.loadResource(
      "createSessionCookie.json");

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testCreateSessionCookieAsync() throws Exception {
    MockTokenVerifier verifier = MockTokenVerifier.fromUid("uid");
    TenantAwareFirebaseAuth.Builder builder = builderForTokenVerification(verifier);
    TestResponseInterceptor interceptor = setUserManager(builder, CREATE_COOKIE_RESPONSE);
    TenantAwareFirebaseAuth auth = builder.build();

    String cookie = auth.createSessionCookieAsync("testToken", COOKIE_OPTIONS).get();

    assertEquals("MockCookieString", cookie);
    assertEquals("testToken", verifier.getLastTokenString());
    GenericJson parsed = parseRequestContent(interceptor);
    assertEquals(2, parsed.size());
    assertEquals("testToken", parsed.get("idToken"));
    assertEquals(new BigDecimal(3600), parsed.get("validDuration"));
    checkUrl(interceptor, AUTH_BASE_URL + ":createSessionCookie");
  }

  @Test
  public void testCreateSessionCookieAsyncError() throws Exception {
    TenantAwareFirebaseAuth.Builder builder = builderForTokenVerification(
        MockTokenVerifier.fromException(AUTH_EXCEPTION));
    TestResponseInterceptor interceptor = setUserManager(builder, CREATE_COOKIE_RESPONSE);
    TenantAwareFirebaseAuth auth = builder.build();

    try {
      auth.createSessionCookieAsync("testToken", COOKIE_OPTIONS).get();
      fail("No error thrown for invalid ID token");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      FirebaseAuthException cause = (FirebaseAuthException) e.getCause();
      assertEquals(ErrorCode.INVALID_ARGUMENT, cause.getErrorCode());
    }

    assertNull(interceptor.getResponse());
  }

  @Test
  public void testCreateSessionCookie() throws Exception {
    TenantAwareFirebaseAuth.Builder builder = builderForTokenVerification(
        MockTokenVerifier.fromUid("uid"));
    TestResponseInterceptor interceptor = setUserManager(builder, CREATE_COOKIE_RESPONSE);
    TenantAwareFirebaseAuth auth = builder.build();

    String cookie = auth.createSessionCookie("testToken", COOKIE_OPTIONS);

    assertEquals("MockCookieString", cookie);
    GenericJson parsed = parseRequestContent(interceptor);
    assertEquals(2, parsed.size());
    assertEquals("testToken", parsed.get("idToken"));
    assertEquals(new BigDecimal(3600), parsed.get("validDuration"));
    checkUrl(interceptor, AUTH_BASE_URL + ":createSessionCookie");
  }

  @Test
  public void testCreateSessionCookieError() {
    TenantAwareFirebaseAuth.Builder builder = builderForTokenVerification(
        MockTokenVerifier.fromException(AUTH_EXCEPTION));
    TestResponseInterceptor interceptor = setUserManager(builder, CREATE_COOKIE_RESPONSE);
    TenantAwareFirebaseAuth auth = builder.build();

    try {
      auth.createSessionCookie("testToken", COOKIE_OPTIONS);
      fail("No error thrown for invalid ID token");
    } catch (FirebaseAuthException e) {
      assertEquals(ErrorCode.INVALID_ARGUMENT, e.getErrorCode());
    }

    assertNull(interceptor.getResponse());
  }

  @Test
  public void testVerifySessionCookie() throws FirebaseAuthException {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromUid("uid");
    TenantAwareFirebaseAuth.Builder builder = builderForTokenVerification(tokenVerifier);
    setUserManager(builder);
    TenantAwareFirebaseAuth auth = builder.build();

    FirebaseToken token = auth.verifySessionCookie("cookie");

    assertEquals("uid", token.getUid());
    assertEquals("cookie", tokenVerifier.getLastTokenString());
  }

  @Test
  public void testVerifySessionCookieFailure() {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromException(AUTH_EXCEPTION);
    TenantAwareFirebaseAuth.Builder builder = builderForTokenVerification(tokenVerifier);
    setUserManager(builder);
    TenantAwareFirebaseAuth auth = builder.build();

    try {
      auth.verifySessionCookie("cookie");
      fail("No error thrown for invalid token");
    } catch (FirebaseAuthException authException) {
      assertEquals(ErrorCode.INVALID_ARGUMENT, authException.getErrorCode());
      assertEquals("cookie", tokenVerifier.getLastTokenString());
    }
  }

  @Test
  public void testVerifySessionCookieAsync() throws Exception {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromUid("uid");
    TenantAwareFirebaseAuth.Builder builder = builderForTokenVerification(tokenVerifier);
    setUserManager(builder);
    TenantAwareFirebaseAuth auth = builder.build();

    FirebaseToken firebaseToken = auth.verifySessionCookieAsync("cookie").get();

    assertEquals("uid", firebaseToken.getUid());
    assertEquals("cookie", tokenVerifier.getLastTokenString());
  }

  @Test
  public void testVerifySessionCookieAsyncFailure() throws InterruptedException {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromException(AUTH_EXCEPTION);
    TenantAwareFirebaseAuth.Builder builder = builderForTokenVerification(tokenVerifier);
    setUserManager(builder);
    TenantAwareFirebaseAuth auth = builder.build();

    try {
      auth.verifySessionCookieAsync("cookie").get();
      fail("No error thrown for invalid token");
    } catch (ExecutionException e) {
      FirebaseAuthException authException = (FirebaseAuthException) e.getCause();
      assertEquals(ErrorCode.INVALID_ARGUMENT, authException.getErrorCode());
      assertEquals("cookie", tokenVerifier.getLastTokenString());
    }
  }

  @Test
  public void testVerifySessionCookieWithCheckRevoked() throws FirebaseAuthException {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromUid("uid");
    TenantAwareFirebaseAuth.Builder builder = builderForTokenVerification(tokenVerifier);
    TestResponseInterceptor interceptor = setUserManager(
        builder, TestUtils.loadResource("getUser.json"));
    TenantAwareFirebaseAuth auth = builder.build();

    FirebaseToken token = auth.verifySessionCookie("cookie", true);

    assertEquals("uid", token.getUid());
    assertEquals("cookie", tokenVerifier.getLastTokenString());
    checkUrl(interceptor, AUTH_BASE_URL + "/accounts:lookup");
  }

  @Test
  public void testVerifySessionCookieWithCheckRevokedFailure() {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromException(AUTH_EXCEPTION);
    TenantAwareFirebaseAuth.Builder builder = builderForTokenVerification(tokenVerifier);
    setUserManager(builder);
    TenantAwareFirebaseAuth auth = builder.build();

    try {
      auth.verifySessionCookie("cookie", true);
      fail("No error thrown for invalid token");
    } catch (FirebaseAuthException e) {
      assertEquals(ErrorCode.INVALID_ARGUMENT, e.getErrorCode());
      assertEquals("cookie", tokenVerifier.getLastTokenString());
    }
  }

  @Test
  public void testVerifySessionCookieWithCheckRevokedAsyncFailure() throws InterruptedException {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromException(AUTH_EXCEPTION);
    TenantAwareFirebaseAuth.Builder builder = builderForTokenVerification(tokenVerifier);
    setUserManager(builder);
    TenantAwareFirebaseAuth auth = builder.build();

    try {
      auth.verifySessionCookieAsync("cookie", true).get();
      fail("No error thrown for invalid token");
    } catch (ExecutionException e) {
      FirebaseAuthException authException = (FirebaseAuthException) e.getCause();
      assertEquals(ErrorCode.INVALID_ARGUMENT, authException.getErrorCode());
      assertEquals("cookie", tokenVerifier.getLastTokenString());
    }
  }

  private static TenantAwareFirebaseAuth.Builder builderForTokenVerification(
      MockTokenVerifier verifier) {
    return TenantAwareFirebaseAuth.builder()
        .setTenantId(TENANT_ID)
        .setIdTokenVerifier(Suppliers.ofInstance(verifier))
        .setCookieVerifier(Suppliers.ofInstance(verifier));
  }

  private static TestResponseInterceptor setUserManager(TenantAwareFirebaseAuth.Builder builder) {
    return setUserManager(builder, "{}");
  }

  private static TestResponseInterceptor setUserManager(
      TenantAwareFirebaseAuth.Builder builder, String response) {
    FirebaseApp app = initializeAppWithResponse(response);
    builder.setFirebaseApp(app);
    return FirebaseAuthTest.setUserManager(builder, app, TENANT_ID);
  }

  private static FirebaseApp initializeAppWithResponse(String response) {
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(new MockLowLevelHttpResponse().setContent(response))
        .build();
    return FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("token"))
        .setHttpTransport(transport)
        .setProjectId("test-project-id")
        .build());
  }

  private static GenericJson parseRequestContent(TestResponseInterceptor interceptor)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    interceptor.getResponse().getRequest().getContent().writeTo(out);
    return Utils.getDefaultJsonFactory().fromString(
        new String(out.toByteArray()), GenericJson.class);
  }

  private static void checkUrl(TestResponseInterceptor interceptor, String url) {
    HttpRequest request = interceptor.getResponse().getRequest();
    assertEquals(HttpMethods.POST, request.getRequestMethod());
    assertEquals(url, request.getUrl().getRawPath());
  }
}
