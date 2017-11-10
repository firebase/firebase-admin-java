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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;
import com.google.firebase.testing.ServiceAccount;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class DataSnapshotTest {

  private static FirebaseApp testApp;
  private static DatabaseConfig config;

  @BeforeClass
  public static void setUpClass() throws IOException {
    testApp = FirebaseApp.initializeApp(
        new FirebaseOptions.Builder()
            .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
            .setDatabaseUrl("https://admin-java-sdk.firebaseio.com")
            .build());
    // Obtain a new DatabaseConfig instance for testing. Since we are not connecting to an
    // actual Firebase database, it is necessary to use a stand-in DatabaseConfig here.
    config = TestHelpers.newTestConfig(testApp);
  }

  @AfterClass
  public static void tearDownClass() throws InterruptedException {
    // Tear down and clean up the test DatabaseConfig.
    TestHelpers.interruptConfig(config);
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  private DataSnapshot snapFor(Object data) {
    Node node = NodeUtilities.NodeFromJSON(data);
    DatabaseReference ref = new DatabaseReference("https://test.firebaseio.com", config);
    return new DataSnapshot(ref, IndexedNode.from(node));
  }

  @Test
  public void testBasicIteration() {
    DataSnapshot snap1 = snapFor(null);

    assertFalse(snap1.hasChildren());
    assertFalse(snap1.getChildren().iterator().hasNext());

    DataSnapshot snap2 = snapFor(1L);
    assertFalse(snap2.hasChildren());
    assertFalse(snap2.getChildren().iterator().hasNext());

    DataSnapshot snap3 = snapFor(MapBuilder.of("a", 1L, "b", 2L));
    assertTrue(snap3.hasChildren());
    Iterator<DataSnapshot> iter = snap3.getChildren().iterator();
    assertTrue(iter.hasNext());

    String[] children = new String[] {null, null};
    int i = 0;
    for (DataSnapshot child : snap3.getChildren()) {
      children[i] = child.getKey();
      i++;
    }
    assertArrayEquals(children, new String[] {"a", "b"});
  }

  @Test
  public void testExists() {
    DataSnapshot snap;

    snap = snapFor(new HashMap<>());
    assertFalse(snap.exists());

    snap = snapFor(MapBuilder.of(".priority", 1));
    assertFalse(snap.exists());

    snap = snapFor(null);
    assertFalse(snap.exists());

    snap = snapFor(true);
    assertTrue(snap.exists());

    snap = snapFor(5);
    assertTrue(snap.exists());

    snap = snapFor(MapBuilder.of("x", 5));
    assertTrue(snap.exists());
  }

  @Test
  public void testChild() {
    DataSnapshot snapshot = snapFor(ImmutableMap.of("foo", "bar"));
    assertTrue(snapshot.hasChild("foo"));
    assertFalse(snapshot.hasChild("bar"));
    assertEquals(1, snapshot.getChildrenCount());

    DataSnapshot child = snapshot.child("foo");
    assertNotNull(child);
    assertEquals("foo", child.getKey());
    assertTrue(child.exists());

    child = snapshot.child("bar");
    assertNotNull(child);
    assertEquals("bar", child.getKey());
    assertFalse(child.exists());

    Iterator<DataSnapshot> iter = snapshot.getChildren().iterator();
    int count = 0;
    while (iter.hasNext()) {
      DataSnapshot next = iter.next();
      count++;
      assertNotNull(next);
      try {
        iter.remove();
        fail("No error thrown for remove() operation");
      } catch (UnsupportedOperationException expected) {
        // expected
      }
    }
    assertEquals(1, count);
  }

  @Test
  public void testGetValue() {
    DataSnapshot snapshot = snapFor("foo");
    assertEquals("foo", snapshot.getValue(String.class));

    snapshot = snapFor(9);
    assertEquals(9, (int) snapshot.getValue(Integer.class));

    snapshot = snapFor(9.9);
    assertEquals(9.9, snapshot.getValue(Double.class), 0.0001);

    snapshot = snapFor(true);
    assertTrue(snapshot.getValue(Boolean.class));

    ImmutableMap<String, String> map = ImmutableMap.of("foo", "bar");
    snapshot = snapFor(map);
    GenericTypeIndicator<Map<String, String>> generic =
        new GenericTypeIndicator<Map<String, String>>() {};
    assertEquals(map, snapshot.getValue(generic));
  }

  @Test
  public void testGetPriority() {
    DataSnapshot snapshot = snapFor(ImmutableMap.of(".value", "foo", ".priority", 10L));
    assertEquals(10.0, snapshot.getPriority());

    snapshot = snapFor(ImmutableMap.of(".value", "foo", ".priority", "p"));
    assertEquals("p", snapshot.getPriority());

    snapshot = snapFor("foo");
    assertNull(snapshot.getPriority());
  }

  @Test
  public void testToString() {
    DataSnapshot snapshot = snapFor(ImmutableMap.of("foo", "bar"));
    assertEquals("DataSnapshot { key = null, value = {foo=bar} }", snapshot.toString());

    DatabaseReference reference = new DatabaseReference("https://test.firebaseio.com/test", config);
    Node node = NodeUtilities.NodeFromJSON("foo");
    snapshot = new DataSnapshot(reference, IndexedNode.from(node));
    assertEquals("DataSnapshot { key = test, value = foo }", snapshot.toString());
  }
}
