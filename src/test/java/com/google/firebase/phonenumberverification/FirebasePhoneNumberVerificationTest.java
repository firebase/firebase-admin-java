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

package com.google.firebase.phonenumberverification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.internal.FirebaseProcessEnvironment;
import com.google.firebase.phonenumberverification.internal.FirebasePhoneNumberVerificationTokenVerifier;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class FirebasePhoneNumberVerificationTest {
  private static final FirebaseOptions firebaseOptions = FirebaseOptions.builder()
      .setCredentials(TestUtils.getCertCredential(ServiceAccount.OWNER.asStream()))
      .build();

  @Mock
  private FirebasePhoneNumberVerificationTokenVerifier mockVerifier;

  private FirebasePhoneNumberVerification firebasePhoneNumberVerification;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);

    FirebaseApp.initializeApp(firebaseOptions);
    firebasePhoneNumberVerification = FirebasePhoneNumberVerification.getInstance();

    Field verifierField = FirebasePhoneNumberVerification.class.getDeclaredField("tokenVerifier");
    verifierField.setAccessible(true);
    verifierField.set(firebasePhoneNumberVerification, mockVerifier);
  }

  @After
  public void tearDown() {
    FirebaseProcessEnvironment.clearCache();
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGetInstance() {
    FirebasePhoneNumberVerification instance = FirebasePhoneNumberVerification.getInstance();
    assertNotNull(instance);
    assertSame(instance, FirebasePhoneNumberVerification.getInstance());
  }

  @Test
  public void testGetInstanceForApp() {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testGetInstanceForApp");
    FirebasePhoneNumberVerification instance = FirebasePhoneNumberVerification.getInstance(app);
    assertNotNull(instance);
    assertSame(instance, FirebasePhoneNumberVerification.getInstance(app));
  }

  @Test
  public void testVerifyToken_DelegatesToVerifier()
      throws FirebasePhoneNumberVerificationException {
    String testToken = "test.token";
    FirebasePhoneNumberVerificationToken expectedToken =
        mock(FirebasePhoneNumberVerificationToken.class);

    when(mockVerifier.verifyToken(testToken)).thenReturn(expectedToken);

    FirebasePhoneNumberVerificationToken result =
        firebasePhoneNumberVerification.verifyToken(testToken);

    assertEquals(expectedToken, result);
    verify(mockVerifier, times(1)).verifyToken(testToken);
  }

  @Test
  public void testVerifyToken_PropagatesException()
      throws FirebasePhoneNumberVerificationException {
    String testToken = "bad.token";
    FirebasePhoneNumberVerificationException error =
        new FirebasePhoneNumberVerificationException(
            FirebasePhoneNumberVerificationErrorCode.INVALID_TOKEN,
            "Bad token"
        );

    when(mockVerifier.verifyToken(testToken)).thenThrow(error);

    FirebasePhoneNumberVerificationException e =
        assertThrows(FirebasePhoneNumberVerificationException.class, () ->
            FirebasePhoneNumberVerification.getInstance().verifyToken(testToken)
        );
    assertEquals(FirebasePhoneNumberVerificationErrorCode.INVALID_TOKEN,
        e.getPhoneNumberVerificationErrorCode());
  }

  @Test
  public void testVerifyToken_PropagatesException_Service_Error()
      throws FirebasePhoneNumberVerificationException {
    String testToken = "SERVICE_ERROR";
    FirebasePhoneNumberVerificationException error =
        new FirebasePhoneNumberVerificationException(
            FirebasePhoneNumberVerificationErrorCode.SERVICE_ERROR,
            "SERVICE_ERROR"
        );

    when(mockVerifier.verifyToken(testToken)).thenThrow(error);

    FirebasePhoneNumberVerificationException e =
        assertThrows(FirebasePhoneNumberVerificationException.class, () ->
            FirebasePhoneNumberVerification.getInstance().verifyToken(testToken)
        );
    assertEquals(FirebasePhoneNumberVerificationErrorCode.SERVICE_ERROR,
        e.getPhoneNumberVerificationErrorCode());
  }

  @Test
  public void testVerifyToken_PropagatesException_Internal_Error()
      throws FirebasePhoneNumberVerificationException {
    String testToken = "INTERNAL";
    FirebasePhoneNumberVerificationException error =
        new FirebasePhoneNumberVerificationException(
            null,
            "INTERNAL"
        );

    when(mockVerifier.verifyToken(testToken)).thenThrow(error);

    FirebasePhoneNumberVerificationException e =
        assertThrows(FirebasePhoneNumberVerificationException.class, () ->
            FirebasePhoneNumberVerification.getInstance().verifyToken(testToken)
        );
    assertNull(e.getPhoneNumberVerificationErrorCode());
  }
}
