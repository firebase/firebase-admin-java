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

import com.google.api.client.testing.http.FixedClock;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import org.junit.After;
import org.junit.Test;

public class FirebaseTokenUtilsTest {

  private static final FixedClock CLOCK = new FixedClock(2002000L * 1000);

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testVerifyIdToken() {
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.create(null))
        .setProjectId("test-project-id")
        .build());

    FirebaseTokenVerifier idTokenVerifier = FirebaseTokenUtils.createIdTokenVerifier(app, CLOCK);
    assertEquals("verifyIdToken()", idTokenVerifier.getMethod());
  }

  @Test
  public void testVerifySessionCookie() {
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.create(null))
        .setProjectId("test-project-id")
        .build());

    FirebaseTokenVerifier idTokenVerifier = FirebaseTokenUtils.createSessionCookieVerifier(
        app, CLOCK);
    assertEquals("verifySessionCookie()", idTokenVerifier.getMethod());
  }
}
