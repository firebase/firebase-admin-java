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

package com.google.firebase.appcheck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.appcheck.internal.AppCheckTokenVerifier;
import com.google.firebase.internal.FirebaseProcessEnvironment;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class FirebaseAppCheckTest {

  private static final FirebaseOptions firebaseOptions =
      FirebaseOptions.builder()
          .setCredentials(TestUtils.getCertCredential(ServiceAccount.OWNER.asStream()))
          .setProjectId("test-project-id")
          .build();

  @Mock private AppCheckTokenVerifier mockVerifier;

  private FirebaseAppCheck firebaseAppCheck;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);

    FirebaseApp.initializeApp(firebaseOptions);
    firebaseAppCheck = FirebaseAppCheck.getInstance();

    Field verifierField = FirebaseAppCheck.class.getDeclaredField("tokenVerifier");
    verifierField.setAccessible(true);
    verifierField.set(firebaseAppCheck, mockVerifier);
  }

  @After
  public void tearDown() {
    FirebaseProcessEnvironment.clearCache();
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGetInstance() {
    FirebaseAppCheck instance = FirebaseAppCheck.getInstance();
    assertNotNull(instance);
    assertSame(instance, FirebaseAppCheck.getInstance());
  }

  @Test
  public void testGetInstanceForApp() {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testGetInstanceForApp");
    FirebaseAppCheck instance = FirebaseAppCheck.getInstance(app);
    assertNotNull(instance);
    assertSame(instance, FirebaseAppCheck.getInstance(app));
  }

  @Test
  public void testVerifyToken_DelegatesToVerifier() throws FirebaseAppCheckException {
    String testToken = "test.token";
    DecodedAppCheckToken decodedToken =
        new DecodedAppCheckToken(ImmutableMap.<String, Object>of("sub", "app-id"));
    VerifyAppCheckTokenResponse expectedResponse =
        new VerifyAppCheckTokenResponse("app-id", decodedToken, null);

    when(mockVerifier.verifyToken(testToken, null)).thenReturn(expectedResponse);

    VerifyAppCheckTokenResponse result = firebaseAppCheck.verifyToken(testToken);

    assertEquals(expectedResponse, result);
    verify(mockVerifier, times(1)).verifyToken(testToken, null);
  }

  @Test
  public void testVerifyTokenWithOptions_DelegatesToVerifier() throws FirebaseAppCheckException {
    String testToken = "test.token";
    VerifyAppCheckTokenOptions options =
        VerifyAppCheckTokenOptions.builder().setConsume(true).build();
    DecodedAppCheckToken decodedToken =
        new DecodedAppCheckToken(ImmutableMap.<String, Object>of("sub", "app-id"));
    VerifyAppCheckTokenResponse expectedResponse =
        new VerifyAppCheckTokenResponse("app-id", decodedToken, true);

    when(mockVerifier.verifyToken(testToken, options)).thenReturn(expectedResponse);

    VerifyAppCheckTokenResponse result = firebaseAppCheck.verifyToken(testToken, options);

    assertEquals(expectedResponse, result);
    verify(mockVerifier, times(1)).verifyToken(testToken, options);
  }

  @Test
  public void testVerifyTokenAsync_DelegatesToVerifier() throws Exception {
    String testToken = "test.token";
    DecodedAppCheckToken decodedToken =
        new DecodedAppCheckToken(ImmutableMap.<String, Object>of("sub", "app-id"));
    VerifyAppCheckTokenResponse expectedResponse =
        new VerifyAppCheckTokenResponse("app-id", decodedToken, null);

    when(mockVerifier.verifyToken(testToken, null)).thenReturn(expectedResponse);

    ApiFuture<VerifyAppCheckTokenResponse> future = firebaseAppCheck.verifyTokenAsync(testToken);

    assertEquals(expectedResponse, future.get());
    verify(mockVerifier, times(1)).verifyToken(testToken, null);
  }

  @Test
  public void testVerifyTokenAsyncWithOptions_DelegatesToVerifier() throws Exception {
    String testToken = "test.token";
    VerifyAppCheckTokenOptions options =
        VerifyAppCheckTokenOptions.builder().setConsume(true).build();
    DecodedAppCheckToken decodedToken =
        new DecodedAppCheckToken(ImmutableMap.<String, Object>of("sub", "app-id"));
    VerifyAppCheckTokenResponse expectedResponse =
        new VerifyAppCheckTokenResponse("app-id", decodedToken, true);

    when(mockVerifier.verifyToken(testToken, options)).thenReturn(expectedResponse);

    ApiFuture<VerifyAppCheckTokenResponse> future =
        firebaseAppCheck.verifyTokenAsync(testToken, options);

    assertEquals(expectedResponse, future.get());
    verify(mockVerifier, times(1)).verifyToken(testToken, options);
  }

  @Test(expected = NullPointerException.class)
  public void testNullApp_ThrowsException() {
    new FirebaseAppCheck(null, mockVerifier);
  }

  @Test(expected = NullPointerException.class)
  public void testNullVerifier_ThrowsException() {
    FirebaseApp app = FirebaseApp.getInstance();
    new FirebaseAppCheck(app, null);
  }
}
