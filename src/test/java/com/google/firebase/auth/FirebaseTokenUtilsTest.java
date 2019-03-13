/*
 * Copyright  2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
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

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.api.client.auth.openidconnect.IdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.FixedClock;
import com.google.api.client.util.Clock;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.Iterables;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class FirebaseTokenUtilsTest {

  private static final Clock CLOCK = new FixedClock(2002000L * 1000);
  private static final String TEST_PROJECT_ID = "test-project-id";
  private static final GoogleCredentials MOCK_CREDENTIALS = GoogleCredentials.create(null);

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testCreateIdTokenVerifier() {
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .setProjectId(TEST_PROJECT_ID)
        .build());

    FirebaseTokenVerifierImpl idTokenVerifier = FirebaseTokenUtils.createIdTokenVerifier(
        app, CLOCK);

    assertEquals("verifyIdToken()", idTokenVerifier.getMethod());
    assertEquals("ID token", idTokenVerifier.getShortName());
    assertEquals("an ID token", idTokenVerifier.getArticledShortName());
    assertEquals("https://firebase.google.com/docs/auth/admin/verify-id-tokens",
        idTokenVerifier.getDocUrl());
    verifyPublicKeysManager(idTokenVerifier.getPublicKeysManager(),
        "https://www.googleapis.com/robot/v1/metadata/x509/"
            + "securetoken@system.gserviceaccount.com");
    verifyJwtVerifier(idTokenVerifier.getIdTokenVerifier(),
        "https://securetoken.google.com/test-project-id");
  }

  @Test
  public void testCreateIdTokenVerifierWithoutProjectId() {
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .build());

    thrown.expectMessage("Must initialize FirebaseApp with a project ID to call verifyIdToken()");
    FirebaseTokenUtils.createIdTokenVerifier(app, CLOCK);
  }

  @Test
  public void testSessionCookieVerifier() {
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .setProjectId(TEST_PROJECT_ID)
        .build());

    FirebaseTokenVerifierImpl cookieVerifier = FirebaseTokenUtils.createSessionCookieVerifier(
        app, CLOCK);

    assertEquals("verifySessionCookie()", cookieVerifier.getMethod());
    assertEquals("session cookie", cookieVerifier.getShortName());
    assertEquals("a session cookie", cookieVerifier.getArticledShortName());
    assertEquals("https://firebase.google.com/docs/auth/admin/manage-cookies",
        cookieVerifier.getDocUrl());
    verifyPublicKeysManager(cookieVerifier.getPublicKeysManager(),
        "https://www.googleapis.com/identitytoolkit/v3/relyingparty/publicKeys");
    verifyJwtVerifier(cookieVerifier.getIdTokenVerifier(),
        "https://session.firebase.google.com/test-project-id");
  }

  @Test
  public void testCreateSessionCookieVerifierWithoutProjectId() {
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .build());

    thrown.expectMessage("Must initialize FirebaseApp with a project ID to call "
        + "verifySessionCookie()");
    FirebaseTokenUtils.createSessionCookieVerifier(app, CLOCK);
  }

  private void verifyPublicKeysManager(GooglePublicKeysManager publicKeysManager, String certUrl) {
    assertNotNull(publicKeysManager);
    assertEquals(certUrl, publicKeysManager.getPublicCertsEncodedUrl());
    assertSame(CLOCK, publicKeysManager.getClock());
    assertTrue(publicKeysManager.getJsonFactory() instanceof GsonFactory);
  }

  private void verifyJwtVerifier(IdTokenVerifier jwtVerifier, String issuer) {
    assertNotNull(jwtVerifier);
    assertEquals(issuer, jwtVerifier.getIssuer());
    assertEquals(TEST_PROJECT_ID, Iterables.getOnlyElement(jwtVerifier.getAudience()));
    assertSame(CLOCK, jwtVerifier.getClock());
  }
}
