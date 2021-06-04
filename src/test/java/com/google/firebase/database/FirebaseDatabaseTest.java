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

package com.google.firebase.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.database.util.EmulatorHelper;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

public class FirebaseDatabaseTest {

  private static final FirebaseOptions firebaseOptions =
      FirebaseOptions.builder()
          .setCredentials(TestUtils.getCertCredential(ServiceAccount.EDITOR.asStream()))
          .setDatabaseUrl("https://firebase-db-test.firebaseio.com")
          .build();
  private static final FirebaseOptions firebaseOptionsWithoutDatabaseUrl =
      FirebaseOptions.builder()
          .setCredentials(TestUtils.getCertCredential(ServiceAccount.EDITOR.asStream()))
          .build();

  @Test
  public void testGetInstance() {
    FirebaseApp.initializeApp(firebaseOptions);
    try {
      FirebaseDatabase defaultDatabase = FirebaseDatabase.getInstance();
      assertNotNull(defaultDatabase);
      assertSame(defaultDatabase, FirebaseDatabase.getInstance());
      assertSame(FirebaseApp.getInstance(), defaultDatabase.getApp());
    } finally {
      TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    }
  }

  @Test
  public void testGetInstanceForUrl() {
    FirebaseApp.initializeApp(firebaseOptions);
    try {
      String url = "https://firebase-db-test2.firebaseio.com";
      FirebaseDatabase otherDatabase = FirebaseDatabase.getInstance(url);
      assertNotNull(otherDatabase);
      assertNotSame(otherDatabase, FirebaseDatabase.getInstance());
    } finally {
      TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    }
  }

  @Test
  public void testInvalidUrl() {
    FirebaseApp.initializeApp(firebaseOptions);
    try {
      String[] urls = new String[]{
          null, "", "https://firebase-db-test.firebaseio.com/foo"
      };
      for (String url : urls) {
        try {
          FirebaseDatabase.getInstance(url);
          fail("No error thrown for URL: " + url);
        } catch (DatabaseException expected) {
          // expected
        }
      }
    } finally {
      TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    }
  }

  @Test
  public void testGetInstanceForApp() {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testGetInstanceForApp");
    try {
      FirebaseDatabase db = FirebaseDatabase.getInstance(app);
      assertNotNull(db);
      assertSame(db, FirebaseDatabase.getInstance(app));
    } finally {
      TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    }
  }

  @Test
  public void testReference() {
    FirebaseApp.initializeApp(firebaseOptions);
    try {
      FirebaseDatabase defaultDatabase = FirebaseDatabase.getInstance();
      DatabaseReference reference = defaultDatabase.getReference();
      assertNotNull(reference);
      assertNull(reference.getKey());
      assertNull(reference.getParent());

      reference = defaultDatabase.getReference("foo");
      assertNotNull(reference);
      assertEquals("foo", reference.getKey());
      assertNull(reference.getParent().getKey());

      reference = defaultDatabase.getReference("foo/bar");
      assertNotNull(reference);
      assertEquals("bar", reference.getKey());
      assertEquals("foo", reference.getParent().getKey());
    } finally {
      TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    }
  }

  @Test
  public void testReferenceFromUrl() {
    FirebaseApp.initializeApp(firebaseOptions);
    try {
      FirebaseDatabase defaultDatabase = FirebaseDatabase.getInstance();
      DatabaseReference reference = defaultDatabase.getReferenceFromUrl(
          "https://firebase-db-test.firebaseio.com/foo/bar");
      assertNotNull(reference);
      assertEquals("bar", reference.getKey());
      assertEquals("foo", reference.getParent().getKey());
      try {
        defaultDatabase.getReferenceFromUrl(null);
        fail("No error thrown for null URL");
      } catch (NullPointerException expected) {
        // expected
      }

      try {
        defaultDatabase.getReferenceFromUrl("https://other-db-test.firebaseio.com/foo/bar");
        fail("No error thrown for invalid URL");
      } catch (DatabaseException expected) {
        // expected
      }
    } finally {
      TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    }
  }

  @Test
  public void testAppDelete() {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testAppDelete");
    FirebaseDatabase db = FirebaseDatabase.getInstance(app);
    assertNotNull(db);
    app.delete();

    try {
      db.getReference();
      fail("No error thrown when calling method on database after delete");
    } catch (IllegalStateException expected) {
      // ignore
    }

    try {
      FirebaseDatabase.getInstance(app);
      fail("No error thrown when getting db instance after deleting app");
    } catch (IllegalStateException expected) {
      // ignore
    }
  }

  @Test
  public void testInitAfterAppDelete() {
    try {
      FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testInitAfterAppDelete");
      FirebaseDatabase db1 = FirebaseDatabase.getInstance(app);
      assertNotNull(db1);
      app.delete();

      app = FirebaseApp.initializeApp(firebaseOptions, "testInitAfterAppDelete");
      FirebaseDatabase db2 = FirebaseDatabase.getInstance(app);
      assertNotNull(db2);
      assertNotSame(db1, db2);
    } finally {
      TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    }
  }

  @Test
  public void testDbUrlIsEmulatorUrlWhenSettingOptionsManually() throws IOException {

    List<CustomTestCase> testCases = ImmutableList.of(
        // cases where the env var is ignored because the supplied DB URL is a valid emulator URL
        new CustomTestCase("http://my-custom-hosted-emulator.com:80?ns=dummy-ns", "",
            "http://my-custom-hosted-emulator.com:80", "dummy-ns"),
        new CustomTestCase("http://localhost:9000?ns=test-ns", null,
            "http://localhost:9000", "test-ns"),

        // cases where the supplied DB URL is not an emulator URL, so we extract ns from it
        // and append it to the emulator URL from env var(if it is valid)
        new CustomTestCase("https://valid-namespace.firebaseio.com", "localhost:8080",
            "http://localhost:8080", "valid-namespace"),
        new CustomTestCase("https://test.firebaseio.com?ns=valid-namespace", "localhost:90",
            "http://localhost:90", "valid-namespace")
    );

    for (CustomTestCase tc : testCases) {
      try {
        FirebaseApp app = FirebaseApp.initializeApp(firebaseOptionsWithoutDatabaseUrl);
        TestUtils.setEnvironmentVariables(
            ImmutableMap.of(EmulatorHelper.FIREBASE_RTDB_EMULATOR_HOST_ENV_VAR,
                Strings.nullToEmpty(tc.envVariableUrl)));
        FirebaseDatabase instance = FirebaseDatabase.getInstance(app, tc.rootDbUrl);
        assertEquals(tc.expectedEmulatorRootUrl,
            instance.getReference().repo.getRepoInfo().toString());
        assertEquals(tc.namespace, instance.getReference().repo.getRepoInfo().namespace);
        // clean up after
        app.delete();
      } finally {
        TestUtils.unsetEnvironmentVariables(
            ImmutableSet.of(EmulatorHelper.FIREBASE_RTDB_EMULATOR_HOST_ENV_VAR));
      }
    }
  }

  @Test
  public void testDbUrlIsEmulatorUrlForDbRefWithPath() throws IOException {

    List<CustomTestCase> testCases = ImmutableList.of(
        new CustomTestCase("http://my-custom-hosted-emulator.com:80?ns=dummy-ns",
            "http://my-custom-hosted-emulator.com:80?ns=dummy-ns", "",
            "http://my-custom-hosted-emulator.com:80", "dummy-ns", "/"),
        new CustomTestCase("http://localhost:9000?ns=test-ns",
            "http://localhost:9000/a/b/c/d?ns=test-ns", null,
            "http://localhost:9000", "test-ns", "/a/b/c/d"),
        new CustomTestCase("http://localhost:9000?ns=test-ns",
            "https://valid-namespace.firebaseio.com/a/b/c/d?ns=test-ns", "localhost:9000",
            "http://localhost:9000", "test-ns", "/a/b/c/d"),
        new CustomTestCase("https://valid-namespace.firebaseio.com",
            "http://valid-namespace.firebaseio.com/a/b/c/d", "localhost:8080",
            "http://localhost:8080", "valid-namespace", "/a/b/c/d")
    );

    for (CustomTestCase tc : testCases) {
      try {
        FirebaseApp app = FirebaseApp.initializeApp(firebaseOptionsWithoutDatabaseUrl);
        TestUtils.setEnvironmentVariables(
            ImmutableMap.of(EmulatorHelper.FIREBASE_RTDB_EMULATOR_HOST_ENV_VAR,
                Strings.nullToEmpty(tc.envVariableUrl)));
        FirebaseDatabase instance = FirebaseDatabase.getInstance(app, tc.rootDbUrl);
        DatabaseReference dbRef = instance.getReferenceFromUrl(tc.pathUrl);
        assertEquals(tc.expectedEmulatorRootUrl, dbRef.repo.getRepoInfo().toString());
        assertEquals(tc.namespace, dbRef.repo.getRepoInfo().namespace);
        assertEquals(tc.path, dbRef.path.toString());
        // clean up after
        app.delete();

      } finally {
        TestUtils.unsetEnvironmentVariables(
            ImmutableSet.of(EmulatorHelper.FIREBASE_RTDB_EMULATOR_HOST_ENV_VAR));
      }
    }
  }

  private static class CustomTestCase {

    private String rootDbUrl;
    private String pathUrl;
    private String envVariableUrl;
    private String expectedEmulatorRootUrl;
    private String namespace;
    private String path;

    private CustomTestCase(String rootDbUrl, String envVariableUrl,
        String expectedEmulatorRootUrl, String namespace) {
      this(rootDbUrl, null, envVariableUrl, expectedEmulatorRootUrl, namespace, null);
    }

    private CustomTestCase(String rootDbUrl, String pathUrl, String envVariableUrl,
        String expectedEmulatorRootUrl, String namespace, String path) {
      this.rootDbUrl = rootDbUrl;
      this.pathUrl = pathUrl;
      this.envVariableUrl = envVariableUrl;
      this.expectedEmulatorRootUrl = expectedEmulatorRootUrl;
      this.namespace = namespace;
      this.path = path;
    }
  }
}
