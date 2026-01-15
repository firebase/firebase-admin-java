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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.fpnv.internal.FirebasePnvTokenVerifier;
import com.google.firebase.internal.FirebaseProcessEnvironment;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class FirebasePnvTest {
  private static final FirebaseOptions firebaseOptions = FirebaseOptions.builder()
      .setCredentials(TestUtils.getCertCredential(ServiceAccount.OWNER.asStream()))
      .build();

  @Mock
  private FirebasePnvTokenVerifier mockVerifier;

  private FirebasePnv firebasePnv;

  @Before
  public void setUp() throws Exception {
    // noinspection resource
    MockitoAnnotations.openMocks(this);

    // Initialize Fpnv
    FirebaseApp.initializeApp(firebaseOptions);
    firebasePnv = FirebasePnv.getInstance();

    // Inject the mock verifier
    Field verifierField = FirebasePnv.class.getDeclaredField("tokenVerifier");
    verifierField.setAccessible(true);
    verifierField.set(firebasePnv, mockVerifier);
  }

  @After
  public void tearDown() {
    FirebaseProcessEnvironment.clearCache();
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGetInstance() {
    FirebasePnv firebasePnv = FirebasePnv.getInstance();
    assertNotNull(firebasePnv);
    assertSame(firebasePnv, FirebasePnv.getInstance());
  }

  @Test
  public void testGetInstanceForApp() {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testGetInstanceForApp");
    FirebasePnv firebasePnv = FirebasePnv.getInstance(app);
    assertNotNull(firebasePnv);
    assertSame(firebasePnv, FirebasePnv.getInstance(app));
  }

  @Test
  public void testVerifyToken_DelegatesToVerifier() throws FirebasePnvException {
    String testToken = "test.fpnv.token";
    FirebasePnvToken expectedToken = mock(FirebasePnvToken.class);

    when(mockVerifier.verifyToken(testToken)).thenReturn(expectedToken);

    FirebasePnvToken result = firebasePnv.verifyToken(testToken);

    assertEquals(expectedToken, result);
    verify(mockVerifier, times(1)).verifyToken(testToken);
  }

  @Test
  public void testVerifyToken_PropagatesException() throws FirebasePnvException {
    String testToken = "bad.token";
    FirebasePnvException error = new FirebasePnvException(
        FirebasePnvErrorCode.INVALID_TOKEN,
        "Bad token"
    );

    when(mockVerifier.verifyToken(testToken)).thenThrow(error);

    FirebasePnvException e = assertThrows(FirebasePnvException.class, () ->
        FirebasePnv.getInstance().verifyToken(testToken)
    );
    assertEquals(FirebasePnvErrorCode.INVALID_TOKEN, e.getFpnvErrorCode());
  }
}
