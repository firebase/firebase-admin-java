/*
 * Copyright 2022 Google LLC
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import org.junit.After;
import org.junit.Test;

public class FirebaseAppCheckTest {

  private static final FirebaseOptions TEST_OPTIONS = FirebaseOptions.builder()
          .setCredentials(new MockGoogleCredentials("test-token"))
          .setProjectId("test-project")
          .build();

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGetInstance() {
    FirebaseApp.initializeApp(TEST_OPTIONS);

    FirebaseAppCheck appCheck = FirebaseAppCheck.getInstance();

    assertSame(appCheck, FirebaseAppCheck.getInstance());
  }

  @Test
  public void testGetInstanceByApp() {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS, "custom-app");

    FirebaseAppCheck appCheck = FirebaseAppCheck.getInstance(app);

    assertSame(appCheck, FirebaseAppCheck.getInstance(app));
  }

  @Test
  public void testDefaultAppCheckClient() {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS, "custom-app");
    FirebaseAppCheck appCheck = FirebaseAppCheck.getInstance(app);

    FirebaseAppCheckClient client = appCheck.getAppCheckClient();

    assertTrue(client instanceof FirebaseAppCheckClientImpl);
    assertSame(client, appCheck.getAppCheckClient());
  }

  @Test
  public void testAppDelete() {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS, "custom-app");
    FirebaseAppCheck appCheck = FirebaseAppCheck.getInstance(app);
    assertNotNull(appCheck);

    app.delete();

    try {
      FirebaseAppCheck.getInstance(app);
      fail("No error thrown when getting app check instance after deleting app");
    } catch (IllegalStateException expected) {
      // expected
    }
  }

  @Test
  public void testAppCheckClientWithoutProjectId() {
    FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(new MockGoogleCredentials("test-token"))
            .build();
    FirebaseApp.initializeApp(options);

    try {
      FirebaseAppCheck.getInstance();
      fail("No error thrown for missing project ID");
    } catch (IllegalArgumentException expected) {
      String message = "Project ID is required to access App Check service. Use a service "
              + "account credential or set the project ID explicitly via FirebaseOptions. "
              + "Alternatively you can also set the project ID via the GOOGLE_CLOUD_PROJECT "
              + "environment variable.";
      assertEquals(message, expected.getMessage());
    }
  }
}
