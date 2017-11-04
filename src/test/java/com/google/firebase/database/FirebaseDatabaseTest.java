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

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.testing.ServiceAccount;

import com.google.firebase.testing.TestUtils;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class FirebaseDatabaseTest {
  
  private static FirebaseOptions firebaseOptions =
      new FirebaseOptions.Builder()
          .setCredentials(TestUtils.getCertCredential(ServiceAccount.EDITOR.asStream()))
          .setDatabaseUrl("https://firebase-db-test.firebaseio.com")
          .build();

  @BeforeClass
  public static void setupClass() {
    FirebaseApp.initializeApp(firebaseOptions);
  }

  @AfterClass
  public static void tearDownClass() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGetInstance() throws ExecutionException, InterruptedException {
    FirebaseDatabase defaultDatabase = FirebaseDatabase.getInstance();
    assertNotNull(defaultDatabase);
    assertSame(defaultDatabase, FirebaseDatabase.getInstance());
    assertSame(FirebaseApp.getInstance(), defaultDatabase.getApp());
  }

  @Test
  public void testGetInstanceForUrl() throws ExecutionException, InterruptedException {
    String url = "https://firebase-db-test2.firebaseio.com";
    FirebaseDatabase otherDatabase = FirebaseDatabase.getInstance(url);
    assertNotNull(otherDatabase);
    assertNotSame(otherDatabase, FirebaseDatabase.getInstance());
  }

  @Test
  public void testInvalidUrl() throws ExecutionException, InterruptedException {
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
  }

  @Test
  public void testGetInstanceForApp() throws ExecutionException, InterruptedException {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testGetInstanceForApp");
    FirebaseDatabase db = FirebaseDatabase.getInstance(app);
    assertNotNull(db);
    assertSame(db, FirebaseDatabase.getInstance(app));
  }

  @Test
  public void testReference() {
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
  }

  @Test
  public void testReferenceFromUrl() {
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
  }

  @Test
  public void testAppDelete() throws ExecutionException, InterruptedException {
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
  public void testInitAfterAppDelete() throws ExecutionException, InterruptedException,
      TimeoutException {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testInitAfterAppDelete");
    FirebaseDatabase db1 = FirebaseDatabase.getInstance(app);
    assertNotNull(db1);
    app.delete();

    app = FirebaseApp.initializeApp(firebaseOptions, "testInitAfterAppDelete");
    FirebaseDatabase db2 = FirebaseDatabase.getInstance(app);
    assertNotNull(db2);
    assertNotSame(db1, db2);
  }
}
