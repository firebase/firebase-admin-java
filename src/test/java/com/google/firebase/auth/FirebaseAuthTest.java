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

package com.google.firebase.auth;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.core.ApiFuture;
import com.google.common.base.Defaults;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

public class FirebaseAuthTest {

  private static final FirebaseOptions firebaseOptions = FirebaseOptions.builder()
      .setCredentials(TestUtils.getCertCredential(ServiceAccount.EDITOR.asStream()))
      .build();

  private static final FirebaseAuthException testException = new FirebaseAuthException(
      ErrorCode.INVALID_ARGUMENT, "Test error message", null, null, null);
  private static final long VALID_SINCE = 1494364393;
  private static final String TEST_USER = "testUser";

  @After
  public void cleanup() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGetInstance() {
    FirebaseApp.initializeApp(firebaseOptions);
    FirebaseAuth defaultAuth = FirebaseAuth.getInstance();
    assertNotNull(defaultAuth);
    assertSame(defaultAuth, FirebaseAuth.getInstance());
  }

  @Test
  public void testGetInstanceForApp() {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testGetInstanceForApp");
    FirebaseAuth auth = FirebaseAuth.getInstance(app);
    assertNotNull(auth);
    assertSame(auth, FirebaseAuth.getInstance(app));
  }

  @Test
  public void testAppDelete() {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testAppDelete");
    FirebaseAuth auth = FirebaseAuth.getInstance(app);
    assertNotNull(auth);
    app.delete();
    try {
      FirebaseAuth.getInstance(app);
      fail("No error thrown when getting auth instance after deleting app");
    } catch (IllegalStateException expected) {
      // ignore
    }
  }

  @Test
  public void testInvokeAfterAppDelete() throws Exception {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testInvokeAfterAppDelete");
    FirebaseAuth auth = FirebaseAuth.getInstance(app);
    assertNotNull(auth);
    app.delete();

    for (Method method : auth.getClass().getDeclaredMethods()) {
      int modifiers = method.getModifiers();
      if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)) {
        continue;
      }

      List<Object> parameters = new ArrayList<>(method.getParameterTypes().length);
      for (Class<?> parameterType : method.getParameterTypes()) {
        parameters.add(Defaults.defaultValue(parameterType));
      }
      try {
        method.invoke(auth, parameters.toArray());
        fail("No error thrown when invoking auth after deleting app; method: " + method.getName());
      } catch (InvocationTargetException expected) {
        String message = "FirebaseAuth instance is no longer alive. This happens when "
            + "the parent FirebaseApp instance has been deleted.";
        Throwable cause = expected.getCause();
        assertTrue(cause instanceof IllegalStateException);
        assertEquals(message, cause.getMessage());
      }
    }
  }

  @Test
  public void testInitAfterAppDelete() throws ExecutionException, InterruptedException,
      TimeoutException {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testInitAfterAppDelete");
    FirebaseAuth auth1 = FirebaseAuth.getInstance(app);
    assertNotNull(auth1);
    app.delete();

    app = FirebaseApp.initializeApp(firebaseOptions, "testInitAfterAppDelete");
    FirebaseAuth auth2 = FirebaseAuth.getInstance(app);
    assertNotNull(auth2);
    assertNotSame(auth1, auth2);

    ApiFuture<String> future = auth2.createCustomTokenAsync("foo");
    assertNotNull(future);
    assertNotNull(future.get(TestUtils.TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
  }

  @Test
  public void testProjectIdNotRequiredAtInitialization() {
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials())
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "testProjectIdRequired");
    assertNotNull(FirebaseAuth.getInstance(app));
  }

  @Test(expected = NullPointerException.class)
  public void testAuthExceptionNullErrorCode() {
    new FirebaseAuthException(null, "test", null, null, null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testAuthExceptionNullMessage() {
    new FirebaseAuthException(ErrorCode.INTERNAL, null, null, null, null);
  }

  @Test
  public void testDefaultIdTokenVerifier() {
    FirebaseApp.initializeApp(firebaseOptions);

    FirebaseTokenVerifier tokenVerifier = FirebaseAuth.getInstance()
        .getIdTokenVerifier(false);

    assertTrue(tokenVerifier instanceof FirebaseTokenVerifierImpl);
    String shortName = ((FirebaseTokenVerifierImpl) tokenVerifier).getShortName();
    assertEquals("ID token", shortName);
  }

  @Test
  public void testIdTokenVerifierInitializedOnDemand() throws Exception {
    FirebaseTokenVerifier tokenVerifier = MockTokenVerifier.fromResult(
        getFirebaseToken("idTokenUser"));
    CountingSupplier<FirebaseTokenVerifier> countingSupplier = new CountingSupplier<>(
        Suppliers.ofInstance(tokenVerifier));

    FirebaseAuth auth = getAuthForIdTokenVerification(countingSupplier);
    assertEquals(0, countingSupplier.getCount());

    auth.verifyIdToken("idToken");
    auth.verifyIdToken("idToken");

    assertEquals(1, countingSupplier.getCount());
  }

  @Test
  public void testVerifyIdTokenWithNull() {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromResult(null);
    tokenVerifier.lastTokenString = "_init_";
    FirebaseAuth auth = getAuthForIdTokenVerification(tokenVerifier);

    try {
      auth.verifyIdTokenAsync(null);
      fail("No error thrown for null id token");
    } catch (IllegalArgumentException expected) {
      assertEquals("_init_", tokenVerifier.getLastTokenString());
    }
  }

  @Test
  public void testVerifyIdTokenWithEmptyString() {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromResult(null);
    tokenVerifier.lastTokenString = "_init_";
    FirebaseAuth auth = getAuthForIdTokenVerification(tokenVerifier);

    try {
      auth.verifyIdTokenAsync("");
      fail("No error thrown for null id token");
    } catch (IllegalArgumentException expected) {
      assertEquals("_init_", tokenVerifier.getLastTokenString());
    }

  }

  @Test
  public void testVerifyIdToken() throws Exception {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromResult(
        getFirebaseToken("testUser"));
    FirebaseAuth auth = getAuthForIdTokenVerification(tokenVerifier);

    FirebaseToken firebaseToken = auth.verifyIdToken("idtoken");

    assertEquals("testUser", firebaseToken.getUid());
    assertEquals("idtoken", tokenVerifier.getLastTokenString());
  }

  @Test
  public void testVerifyIdTokenWithRevocationCheck() throws Exception {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromResult(
        getFirebaseToken(VALID_SINCE + 1000));
    FirebaseAuth auth = getAuthForIdTokenVerificationWithRevocationCheck(tokenVerifier);

    FirebaseToken firebaseToken = auth.verifyIdToken("idtoken", true);

    assertEquals("testUser", firebaseToken.getUid());
    assertEquals("idtoken", tokenVerifier.getLastTokenString());
  }

  @Test
  public void testVerifyIdTokenWithRevocationCheckFailure() {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromResult(
        getFirebaseToken(VALID_SINCE - 1000));
    FirebaseAuth auth = getAuthForIdTokenVerificationWithRevocationCheck(tokenVerifier);

    try {
      auth.verifyIdToken("idtoken", true);
      fail("No error thrown for revoked ID token");
    } catch (FirebaseAuthException e) {
      assertEquals(ErrorCode.INVALID_ARGUMENT, e.getErrorCode());
      assertEquals("Firebase id token is revoked.", e.getMessage());
      assertNull(e.getCause());
      assertNull(e.getHttpResponse());
      assertEquals(AuthErrorCode.REVOKED_ID_TOKEN, e.getAuthErrorCode());
    }

    assertEquals("idtoken", tokenVerifier.getLastTokenString());
  }

  @Test
  public void testVerifyIdTokenFailure() {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromException();
    FirebaseAuth auth = getAuthForIdTokenVerification(tokenVerifier);

    try {
      auth.verifyIdToken("idtoken");
      fail("No error thrown for invalid token");
    } catch (FirebaseAuthException authException) {
      assertSame(testException, authException);
    }
  }

  @Test
  public void testVerifyIdTokenAsync() throws Exception {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromResult(
        getFirebaseToken("testUser"));
    FirebaseAuth auth = getAuthForIdTokenVerification(tokenVerifier);

    FirebaseToken firebaseToken = auth.verifyIdTokenAsync("idtoken").get();

    assertEquals("testUser", firebaseToken.getUid());
    assertEquals("idtoken", tokenVerifier.getLastTokenString());
  }

  @Test
  public void testVerifyIdTokenAsyncFailure() throws InterruptedException {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromException();
    FirebaseAuth auth = getAuthForIdTokenVerification(tokenVerifier);

    try {
      auth.verifyIdTokenAsync("idtoken").get();
      fail("No error thrown for invalid token");
    } catch (ExecutionException e) {
      FirebaseAuthException authException = (FirebaseAuthException) e.getCause();
      assertSame(testException, authException);
    }
  }

  @Test
  public void testVerifyIdTokenWithCheckRevokedAsyncFailure() throws InterruptedException {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromException();
    FirebaseAuth auth = getAuthForIdTokenVerification(tokenVerifier);

    try {
      auth.verifyIdTokenAsync("idtoken", true).get();
      fail("No error thrown for invalid token");
    } catch (ExecutionException e) {
      FirebaseAuthException authException = (FirebaseAuthException) e.getCause();
      assertSame(testException, authException);
    }
  }

  @Test
  public void testDefaultSessionCookieVerifier() {
    FirebaseApp.initializeApp(firebaseOptions);

    FirebaseTokenVerifier tokenVerifier = FirebaseAuth.getInstance()
        .getSessionCookieVerifier(false);

    assertTrue(tokenVerifier instanceof FirebaseTokenVerifierImpl);
    String shortName = ((FirebaseTokenVerifierImpl) tokenVerifier).getShortName();
    assertEquals("session cookie", shortName);
  }

  @Test
  public void testSessionCookieVerifierInitializedOnDemand() throws Exception {
    FirebaseTokenVerifier tokenVerifier = MockTokenVerifier.fromResult(
        getFirebaseToken("cookieUser"));
    CountingSupplier<FirebaseTokenVerifier> countingSupplier = new CountingSupplier<>(
        Suppliers.ofInstance(tokenVerifier));
    FirebaseAuth auth = getAuthForSessionCookieVerification(countingSupplier);

    assertEquals(0, countingSupplier.getCount());

    auth.verifySessionCookie("sessionCookie");
    auth.verifySessionCookie("sessionCookie");

    assertEquals(1, countingSupplier.getCount());
  }

  @Test
  public void testVerifySessionCookieWithNull() {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromResult(null);
    tokenVerifier.lastTokenString = "_init_";
    FirebaseAuth auth = getAuthForSessionCookieVerification(tokenVerifier);

    try {
      auth.verifySessionCookieAsync(null);
      fail("No error thrown for null id token");
    } catch (IllegalArgumentException expected) {
      assertEquals("_init_", tokenVerifier.getLastTokenString());
    }
  }

  @Test
  public void testVerifySessionCookieWithEmptyString() {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromResult(null);
    tokenVerifier.lastTokenString = "_init_";
    FirebaseAuth auth = getAuthForSessionCookieVerification(tokenVerifier);

    try {
      auth.verifySessionCookieAsync("");
      fail("No error thrown for null id token");
    } catch (IllegalArgumentException expected) {
      assertEquals("_init_", tokenVerifier.getLastTokenString());
    }

  }

  @Test
  public void testVerifySessionCookie() throws Exception {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromResult(
        getFirebaseToken("testUser"));
    FirebaseAuth auth = getAuthForSessionCookieVerification(tokenVerifier);

    FirebaseToken firebaseToken = auth.verifySessionCookie("idtoken");

    assertEquals("testUser", firebaseToken.getUid());
    assertEquals("idtoken", tokenVerifier.getLastTokenString());
  }

  @Test
  public void testVerifySessionCookieFailure() {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromException();
    FirebaseAuth auth = getAuthForSessionCookieVerification(tokenVerifier);

    try {
      auth.verifySessionCookie("idtoken");
      fail("No error thrown for invalid token");
    } catch (FirebaseAuthException authException) {
      assertSame(testException, authException);
    }
  }

  @Test
  public void testVerifySessionCookieWithRevocationCheck() throws Exception {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromResult(
        getFirebaseToken(VALID_SINCE + 1000));
    FirebaseAuth auth = getAuthForSessionCookieVerificationWithRevocationCheck(tokenVerifier);

    FirebaseToken firebaseToken = auth.verifySessionCookie("cookie", true);

    assertEquals("testUser", firebaseToken.getUid());
    assertEquals("cookie", tokenVerifier.getLastTokenString());
  }

  @Test
  public void testVerifySessionCookieWithRevocationCheckFailure() {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromResult(
        getFirebaseToken(VALID_SINCE - 1000));
    FirebaseAuth auth = getAuthForSessionCookieVerificationWithRevocationCheck(tokenVerifier);

    try {
      auth.verifySessionCookie("cookie", true);
      fail("No error thrown for revoked session cookie");
    } catch (FirebaseAuthException e) {
      assertEquals(ErrorCode.INVALID_ARGUMENT, e.getErrorCode());
      assertEquals("Firebase session cookie is revoked.", e.getMessage());
      assertNull(e.getCause());
      assertNull(e.getHttpResponse());
      assertEquals(AuthErrorCode.REVOKED_SESSION_COOKIE, e.getAuthErrorCode());
    }

    assertEquals("cookie", tokenVerifier.getLastTokenString());
  }

  @Test
  public void testVerifySessionCookieAsync() throws Exception {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromResult(
        getFirebaseToken("testUser"));
    FirebaseAuth auth = getAuthForSessionCookieVerification(tokenVerifier);

    FirebaseToken firebaseToken = auth.verifySessionCookieAsync("idtoken").get();

    assertEquals("testUser", firebaseToken.getUid());
    assertEquals("idtoken", tokenVerifier.getLastTokenString());
  }

  @Test
  public void testVerifySessionCookieAsyncFailure() throws InterruptedException {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromException();
    FirebaseAuth auth = getAuthForSessionCookieVerification(tokenVerifier);

    try {
      auth.verifySessionCookieAsync("idtoken").get();
      fail("No error thrown for invalid token");
    } catch (ExecutionException e) {
      FirebaseAuthException authException = (FirebaseAuthException) e.getCause();
      assertSame(testException, authException);
    }
  }

  @Test
  public void testVerifySessionCookieWithCheckRevokedAsyncFailure() throws InterruptedException {
    MockTokenVerifier tokenVerifier = MockTokenVerifier.fromException();
    FirebaseAuth auth = getAuthForSessionCookieVerification(tokenVerifier);

    try {
      auth.verifySessionCookieAsync("idtoken", true).get();
      fail("No error thrown for invalid token");
    } catch (ExecutionException e) {
      FirebaseAuthException authException = (FirebaseAuthException) e.getCause();
      assertSame(testException, authException);
    }
  }

  private FirebaseToken getFirebaseToken(String subject) {
    return new FirebaseToken(ImmutableMap.<String, Object>of("sub", subject));
  }

  private FirebaseToken getFirebaseToken(long issuedAt) {
    return new FirebaseToken(ImmutableMap.<String, Object>of("sub", TEST_USER, "iat", issuedAt));
  }

  FirebaseAuth getAuthForIdTokenVerificationWithRevocationCheck(
      FirebaseTokenVerifier tokenVerifier) {
    FirebaseApp app = getFirebaseAppForUserRetrieval();
    return getAuthForIdTokenVerification(app, Suppliers.ofInstance(tokenVerifier));
  }

  private FirebaseAuth getAuthForIdTokenVerification(FirebaseTokenVerifier tokenVerifier) {
    return getAuthForIdTokenVerification(Suppliers.ofInstance(tokenVerifier));
  }

  private FirebaseAuth getAuthForIdTokenVerification(
      Supplier<? extends FirebaseTokenVerifier> tokenVerifierSupplier) {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions);
    return getAuthForIdTokenVerification(app, tokenVerifierSupplier);
  }

  private FirebaseAuth getAuthForIdTokenVerification(
      FirebaseApp app,
      Supplier<? extends FirebaseTokenVerifier> tokenVerifierSupplier) {
    FirebaseUserManager userManager = new FirebaseUserManager(app);
    return FirebaseAuth.builder()
        .setFirebaseApp(app)
        .setIdTokenVerifier(tokenVerifierSupplier)
        .setUserManager(Suppliers.ofInstance(userManager))
        .build();
  }

  FirebaseAuth getAuthForSessionCookieVerificationWithRevocationCheck(
      FirebaseTokenVerifier tokenVerifier) {
    FirebaseApp app = getFirebaseAppForUserRetrieval();
    return getAuthForSessionCookieVerification(app, Suppliers.ofInstance(tokenVerifier));
  }

  private FirebaseAuth getAuthForSessionCookieVerification(FirebaseTokenVerifier tokenVerifier) {
    return getAuthForSessionCookieVerification(Suppliers.ofInstance(tokenVerifier));
  }

  private FirebaseAuth getAuthForSessionCookieVerification(
      Supplier<? extends FirebaseTokenVerifier> tokenVerifierSupplier) {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions);
    return getAuthForSessionCookieVerification(app, tokenVerifierSupplier);
  }

  private FirebaseAuth getAuthForSessionCookieVerification(
      FirebaseApp app,
      Supplier<? extends FirebaseTokenVerifier> tokenVerifierSupplier) {
    FirebaseUserManager userManager = new FirebaseUserManager(app);
    return FirebaseAuth.builder()
        .setFirebaseApp(app)
        .setCookieVerifier(tokenVerifierSupplier)
        .setUserManager(Suppliers.ofInstance(userManager))
        .build();
  }

  private FirebaseApp getFirebaseAppForUserRetrieval() {
    String getUserResponse = TestUtils.loadResource("getUser.json");
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(new MockLowLevelHttpResponse().setContent(getUserResponse))
        .build();
    return FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setHttpTransport(transport)
        .setProjectId("test-project-id")
        .build());
  }

  private static class MockTokenVerifier implements FirebaseTokenVerifier {

    private String lastTokenString;

    private FirebaseToken result;
    private FirebaseAuthException exception;

    private MockTokenVerifier(FirebaseToken result, FirebaseAuthException exception) {
      this.result = result;
      this.exception = exception;
    }

    @Override
    public FirebaseToken verifyToken(String token) throws FirebaseAuthException {
      lastTokenString = token;
      if (exception != null) {
        throw exception;
      }
      return result;
    }

    String getLastTokenString() {
      return this.lastTokenString;
    }

    static MockTokenVerifier fromResult(FirebaseToken result) {
      return new MockTokenVerifier(result, null);
    }

    static MockTokenVerifier fromException() {
      return new MockTokenVerifier(null, testException);
    }
  }

  private static class CountingSupplier<T> implements Supplier<T> {

    private final AtomicInteger counter = new AtomicInteger(0);
    private final Supplier<T> supplier;

    CountingSupplier(Supplier<T> supplier) {
      this.supplier = checkNotNull(supplier);
    }

    @Override
    public T get() {
      counter.incrementAndGet();
      return supplier.get();
    }

    int getCount() {
      return counter.get();
    }
  }
}
