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

package com.google.firebase.database.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.TestFailure;
import com.google.firebase.database.TestHelpers;
import com.google.firebase.database.core.view.Event;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.database.future.WriteFuture;
import com.google.firebase.testing.IntegrationTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class OrderTestIT {
  
  private static FirebaseApp masterApp;

  @BeforeClass
  public static void setUpClass() throws TestFailure, TimeoutException, InterruptedException {
    masterApp = IntegrationTestUtils.ensureDefaultApp();
    // Make sure we're connected before any of these tests run
    DatabaseReference ref = FirebaseDatabase.getInstance(masterApp).getReference();
    ReadFuture.untilEquals(ref.child(".info/connected"), true).timedGet();
  }

  @Before
  public void prepareApp() {
    TestHelpers.wrapForErrorHandling(masterApp);
  }

  @After
  public void checkAndCleanupApp() {
    TestHelpers.assertAndUnwrapErrorHandlers(masterApp);
  }

  @Test
  public void testPushAndEnumerate()
      throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    for (int i = 0; i < 10; ++i) {
      ref.push().setValueAsync(i);
    }

    DataSnapshot snap = new ReadFuture(ref).timedGet().get(0).getSnapshot();

    long i = 0;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(i, child.getValue());
      i++;
    }

    assertEquals(10L, i);
  }

  @Test
  public void testPushWriteAndEnumerate()
      throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    List<DatabaseReference> paths = new ArrayList<>(20);
    // Generate children quickly to try to get a few in the same millisecond
    for (int i = 0; i < 20; ++i) {
      paths.add(ref.push());
    }

    for (int i = 0; i < paths.size(); ++i) {
      paths.get(i).setValueAsync(i);
    }

    DataSnapshot snap = new ReadFuture(ref).timedGet().get(0).getSnapshot();

    long i = 0;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(i, child.getValue());
      i++;
    }

    assertEquals(20L, i);
  }

  @Test
  public void testReconnectAndRead()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);

    final DatabaseReference writer = refs.get(0);
    final DatabaseReference reader = refs.get(1);

    for (int i = 0; i < 9; ++i) {
      writer.push().setValueAsync(i);
    }
    new WriteFuture(writer.push(), 9).timedGet();

    DataSnapshot snap = TestHelpers.getSnap(writer);
    long i = 0;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(i, child.getValue());
      i++;
    }
    assertEquals(10L, i);

    snap = TestHelpers.getSnap(reader);
    i = 0;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(i, child.getValue());
      i++;
    }
    assertEquals(10L, i);
  }

  @Test
  public void testReconnectAndReadWithPriority()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);

    final DatabaseReference writer = refs.get(0);
    final DatabaseReference reader = refs.get(1);

    for (int i = 0; i < 9; ++i) {
      writer.push().setValueAsync(i, 10 - i);
    }
    new WriteFuture(writer.push(), 9, 1).timedGet();

    DataSnapshot snap = TestHelpers.getSnap(writer);
    long i = 9;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(i, child.getValue());
      i--;
    }
    assertEquals(-1L, i);

    snap = TestHelpers.getSnap(reader);
    i = 9;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(i, child.getValue());
      i--;
    }
    assertEquals(-1L, i);
  }

  @Test
  public void testExponentialPriority()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);

    final DatabaseReference writer = refs.get(0);
    final DatabaseReference reader = refs.get(1);

    for (int i = 0; i < 9; ++i) {
      writer.push().setValueAsync(i, 111111111111111111111111111111.0 / Math.pow(10, i));
    }
    new WriteFuture(writer.push(), 9, 111111111111111111111111111111.0 / Math.pow(10, 9))
        .timedGet();

    DataSnapshot snap = TestHelpers.getSnap(writer);
    long i = 9;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(i, child.getValue());
      i--;
    }
    assertEquals(-1L, i);

    snap = TestHelpers.getSnap(reader);
    i = 9;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(i, child.getValue());
      i--;
    }
    assertEquals(-1L, i);
  }

  @Test
  public void testEnumerateNodesWithoutValues() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ref.child("foo");
    ref.child("bar").setValueAsync("test");

    DataSnapshot snap = TestHelpers.getSnap(ref);
    int i = 0;
    for (DataSnapshot child : snap.getChildren()) {
      i++;
      assertEquals("bar", child.getKey());
    }
    assertEquals(1, i);
  }

  @Test
  public void testChildEventsOnPriorityChanges() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final EventHelper helper =
        new EventHelper()
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "a")
            .addValueExpectation(ref)
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "b")
            .addValueExpectation(ref)
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "c")
            .addValueExpectation(ref)
            .addChildExpectation(ref, Event.EventType.CHILD_MOVED, "a")
            .addChildExpectation(ref, Event.EventType.CHILD_CHANGED, "a")
            .addValueExpectation(ref)
            .startListening(true);

    ref.child("a").setValueAsync("first", 1);
    ref.child("b").setValueAsync("second", 5);
    ref.child("c").setValueAsync("third", 10);
    ref.child("a").setPriorityAsync(15);

    assertTrue(helper.waitForEvents());
    helper.cleanup();
  }

  @Test
  public void testResetPriorityToNull() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ref.child("a").setValueAsync("a", 1);
    ref.child("b").setValueAsync("b", 2);

    TestHelpers.waitForRoundtrip(ref);
    EventHelper helper =
        new EventHelper()
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "a")
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "b")
            .addValueExpectation(ref)
            .startListening();

    assertTrue(helper.waitForEvents());

    helper
        .addChildExpectation(ref, Event.EventType.CHILD_MOVED, "b")
        .addChildExpectation(ref, Event.EventType.CHILD_CHANGED, "b")
        .addValueExpectation(ref)
        .startListening();

    ref.child("b").setPriorityAsync(null);
    assertTrue(helper.waitForEvents());

    DataSnapshot snap = TestHelpers.getSnap(ref);
    assertNull(snap.child("b").getPriority());
    helper.cleanup();
  }

  @Test
  public void testInsertingNodeUnderLeaf()
      throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ReadFuture readFuture = ReadFuture.untilCountAfterNull(ref, 2);

    ref.setValueAsync("a", 10);
    ref.child("deeper").setValueAsync("deeper");

    DataSnapshot snap = readFuture.timedGet().get(1).getSnapshot();
    assertEquals(10.0, snap.getPriority());
  }

  @Test
  public void testMixedPriorityTypes()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    final DatabaseReference writer = refs.get(0);
    final DatabaseReference reader = refs.get(1);

    writer.child("alpha42").setValueAsync(1, "zed");
    writer.child("noPriorityC").setValueAsync(1, null);
    writer.child("num41").setValueAsync(1, 500);
    writer.child("noPriorityB").setValueAsync(1, null);
    writer.child("num80").setValueAsync(1, 4000.1);
    writer.child("num50").setValueAsync(1, 4000);
    writer.child("num10").setValueAsync(1, 24);
    writer.child("alpha41").setValueAsync(1, "zed");
    writer.child("alpha20").setValueAsync(1, "horse");
    writer.child("num20").setValueAsync(1, 123);
    writer.child("num70").setValueAsync(1, 4000.01);
    writer.child("noPriorityA").setValueAsync(1, null);
    writer.child("alpha30").setValueAsync(1, "tree");
    writer.child("num30").setValueAsync(1, 300);
    writer.child("num60").setValueAsync(1, 4000.001);
    writer.child("alpha10").setValueAsync(1, "0horse");
    writer.child("num42").setValueAsync(1, 500);
    writer.child("alpha40").setValueAsync(1, "zed");
    new WriteFuture(writer.child("num40"), 1, 500).timedGet();

    List<String> expected = ImmutableList.<String>builder().add(
            "noPriorityA",
            "noPriorityB",
            "noPriorityC",
            "num10",
            "num20",
            "num30",
            "num40",
            "num41",
            "num42",
            "num50",
            "num60",
            "num70",
            "num80",
            "alpha10",
            "alpha20",
            "alpha30",
            "alpha40",
            "alpha41",
            "alpha42").build();

    List<String> actual = new ArrayList<>(expected.size());
    DataSnapshot snap = TestHelpers.getSnap(writer);
    for (DataSnapshot child : snap.getChildren()) {
      actual.add(child.getKey());
    }
    TestHelpers.assertDeepEquals(expected, actual);

    actual.clear();
    snap = TestHelpers.getSnap(reader);
    for (DataSnapshot child : snap.getChildren()) {
      actual.add(child.getKey());
    }
    TestHelpers.assertDeepEquals(expected, actual);
  }

  @Test
  public void testIntegerKeyOrder()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);

    writer.child("foo").setValueAsync(0);
    writer.child("bar").setValueAsync(0);
    writer.child("03").setValueAsync(0);
    writer.child("0").setValueAsync(0);
    writer.child("100").setValueAsync(0);
    writer.child("20").setValueAsync(0);
    writer.child("5").setValueAsync(0);
    writer.child("3").setValueAsync(0);
    writer.child("003").setValueAsync(0);
    new WriteFuture(writer.child("9"), 0).timedGet();

    List<String> expected = ImmutableList.of(
        "0", "3", "03", "003", "5", "9", "20", "100", "bar", "foo");
    List<String> actual = new ArrayList<>(expected.size());
    DataSnapshot snap = TestHelpers.getSnap(writer);
    for (DataSnapshot child : snap.getChildren()) {
      actual.add(child.getKey());
    }
    TestHelpers.assertDeepEquals(expected, actual);
  }

  @Test
  public void testLargeIntegerKeyOrder()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);

    writer.child("2000000000").setValueAsync(0);
    new WriteFuture(writer.child("-2000000000"), 0).timedGet();

    List<String> expected = ImmutableList.of("-2000000000", "2000000000");
    List<String> actual = new ArrayList<>(expected.size());
    DataSnapshot snap = TestHelpers.getSnap(writer);
    for (DataSnapshot child : snap.getChildren()) {
      actual.add(child.getKey());
    }
    TestHelpers.assertDeepEquals(expected, actual);
  }

  @Test
  public void testPrevNameOnChildAddedEvent() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<String> results = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);
    final ChildEventListener listener =
        ref.addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                results.add(snapshot.getKey());
                results.add(previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                fail("Should not happen");
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                fail("Should not happen");
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                fail("Should not happen");
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.setValueAsync(new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build());

    TestHelpers.waitFor(semaphore, 3);
    List<String> expected = Arrays.asList("a", null, "b", "a", "c", "b");
    TestHelpers.assertDeepEquals(expected, results);
    ref.removeEventListener(listener);
  }

  @Test
  public void testPrevNameOnAddingNewNodes() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<String> results = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);
    final ChildEventListener listener =
        ref.addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                results.add(snapshot.getKey());
                results.add(previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                fail("Should not happen");
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                fail("Should not happen");
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                fail("Should not happen");
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.setValueAsync(MapBuilder.of("b", 2, "c", 3, "d", 4));

    ref.child("a").setValueAsync(1);
    ref.child("e").setValueAsync(5);

    TestHelpers.waitFor(semaphore, 5);
    List<String> expected = Arrays.asList("b", null, "c", "b", "d", "c", "a", null, "e", "d");
    TestHelpers.assertDeepEquals(expected, results);
    ref.removeEventListener(listener);
  }

  @Test
  public void testPrevNameOnAddingNewNodesWithJSON() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<String> results = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);
    final ChildEventListener listener =
        ref.addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                results.add(snapshot.getKey());
                results.add(previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                fail("Should not happen");
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                fail("Should not happen");
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                fail("Should not happen");
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.setValueAsync(MapBuilder.of("b", 2, "c", 3, "d", 4));
    ref.setValueAsync(new MapBuilder().put("a", 1).put("b", 2).put("c", 3).put("d", 4).build());
    ref.setValueAsync(new MapBuilder().put("a", 1).put("b", 2).put("c", 3)
        .put("d", 4).put("e", 5).build());

    TestHelpers.waitFor(semaphore, 5);
    List<String> expected = Arrays.asList("b", null, "c", "b", "d", "c", "a", null, "e", "d");
    TestHelpers.assertDeepEquals(expected, results);
    ref.removeEventListener(listener);
  }

  @Test
  public void testPrevNameIOnMovingNodes() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<String> results = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);
    final ChildEventListener listener =
        ref.addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                results.add("CHANGED:" + snapshot.getKey() + "/" + previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                fail("Should not happen");
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                results.add("MOVED:" + snapshot.getKey() + "/" + previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.child("a").setValueAsync("a", 1);
    ref.child("b").setValueAsync("b", 2);
    ref.child("c").setValueAsync("c", 3);
    ref.child("d").setValueAsync("d", 4);

    ref.child("d").setPriorityAsync(0);

    ref.child("a").setPriorityAsync(4);

    ref.child("c").setPriorityAsync(0.5);

    TestHelpers.waitFor(semaphore, 6);

    List<String> expected = ImmutableList.of("MOVED:d/null", "CHANGED:d/null", "MOVED:a/c",
        "CHANGED:a/c", "MOVED:c/d", "CHANGED:c/d");
    TestHelpers.assertDeepEquals(expected, results);
    ref.removeEventListener(listener);
  }

  @Test
  public void testPrevNameOnWhenMovingNodesBySettingJson() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<String> results = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);
    final ChildEventListener listener =
        ref.addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                results.add("CHANGED:" + snapshot.getKey() + "/" + previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                fail("Should not happen");
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                results.add("MOVED:" + snapshot.getKey() + "/" + previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.setValueAsync(
        new MapBuilder()
            .put("a", new MapBuilder().put(".value", "a").put(".priority", 1).build())
            .put("b", new MapBuilder().put(".value", "b").put(".priority", 2).build())
            .put("c", new MapBuilder().put(".value", "c").put(".priority", 3).build())
            .put("d", new MapBuilder().put(".value", "d").put(".priority", 4).build())
            .build());

    ref.setValueAsync(
        new MapBuilder()
            .put("d", new MapBuilder().put(".value", "d").put(".priority", 0).build())
            .put("a", new MapBuilder().put(".value", "a").put(".priority", 1).build())
            .put("b", new MapBuilder().put(".value", "b").put(".priority", 2).build())
            .put("c", new MapBuilder().put(".value", "c").put(".priority", 3).build())
            .build());

    ref.setValueAsync(
        new MapBuilder()
            .put("d", new MapBuilder().put(".value", "d").put(".priority", 0).build())
            .put("b", new MapBuilder().put(".value", "b").put(".priority", 2).build())
            .put("c", new MapBuilder().put(".value", "c").put(".priority", 3).build())
            .put("a", new MapBuilder().put(".value", "a").put(".priority", 4).build())
            .build());

    ref.setValueAsync(
        new MapBuilder()
            .put("d", new MapBuilder().put(".value", "d").put(".priority", 0).build())
            .put("c", new MapBuilder().put(".value", "c").put(".priority", 0.5).build())
            .put("b", new MapBuilder().put(".value", "b").put(".priority", 2).build())
            .put("a", new MapBuilder().put(".value", "a").put(".priority", 4).build())
            .build());

    TestHelpers.waitFor(semaphore, 6);

    List<String> expected = ImmutableList.of("MOVED:d/null", "CHANGED:d/null", "MOVED:a/c",
        "CHANGED:a/c", "MOVED:c/d", "CHANGED:c/d");
    TestHelpers.assertDeepEquals(expected, results);
    ref.removeEventListener(listener);
  }

  @Test
  public void testCase595DeletingPrioritizedGrandChild()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final ChildEventListener listener =
        ref.addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                // No-op
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                fail("Should not happen");
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.child("test/foo").setValueAsync(42, "5");
    ref.child("test/f002").setValueAsync(42, "10");
    ref.child("test/foo").removeValueAsync();
    new WriteFuture(ref.child("test/foo2"), null).timedGet();
    // If child_moved has been raised, the test will have failed by now
    ref.removeEventListener(listener);
  }

  @Test
  public void testSetValuePriorityToZero()
      throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ReadFuture readFuture = new ReadFuture(ref);
    ref.setValueAsync("test", 0);

    DataSnapshot snap = readFuture.timedGet().get(0).getSnapshot();

    assertEquals(0.0, snap.getPriority());
  }

  @Test
  public void testSetObjectPriorityToZero()
      throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ReadFuture readFuture = new ReadFuture(ref);
    ref.setValueAsync(new MapBuilder().put("x", "test").put("y", 7).build(), 0);

    DataSnapshot snap = readFuture.timedGet().get(0).getSnapshot();

    assertEquals(0.0, snap.getPriority());
  }

  @Test
  public void testCase2003ChildMovedForPriorityChange1() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<String> results = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);
    final ChildEventListener listener =
        ref.addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                results.add("CHANGED:" + snapshot.getKey() + "/" + previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                fail("Should not happen");
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                results.add("MOVED:" + snapshot.getKey() + "/" + previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.setValueAsync(
        new MapBuilder()
            .put("a", new MapBuilder().put(".value", "a").put(".priority", 0).build())
            .put("b", new MapBuilder().put(".value", "b").put(".priority", 1).build())
            .put("c", new MapBuilder().put(".value", "c").put(".priority", 2).build())
            .put("d", new MapBuilder().put(".value", "d").put(".priority", 3).build())
            .build());

    ref.child("b").setPriorityAsync(1.5);
    TestHelpers.waitFor(semaphore, 2);

    assertEquals(2, results.size());
    assertEquals(ImmutableList.of("MOVED:b/a", "CHANGED:b/a"), results);
    ref.removeEventListener(listener);
  }

  @Test
  public void testCase2003ChildMovedForPriorityChange2() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<String> results = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);
    final ChildEventListener listener =
        ref.addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                results.add("CHANGED:" + snapshot.getKey() + "/" + previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                fail("Should not happen");
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                results.add("MOVED:" + snapshot.getKey() + "/" + previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onCancelled(DatabaseError error) {
                fail("Should not happen");
              }
            });

    ref.setValueAsync(
        new MapBuilder()
            .put("a", new MapBuilder().put(".value", "a").put(".priority", 0).build())
            .put("b", new MapBuilder().put(".value", "b").put(".priority", 1).build())
            .put("c", new MapBuilder().put(".value", "c").put(".priority", 2).build())
            .put("d", new MapBuilder().put(".value", "d").put(".priority", 3).build())
            .build());

    ref.setValueAsync(
        new MapBuilder()
            .put("a", new MapBuilder().put(".value", "a").put(".priority", 0).build())
            .put("b", new MapBuilder().put(".value", "b").put(".priority", 1.5).build())
            .put("c", new MapBuilder().put(".value", "c").put(".priority", 2).build())
            .put("d", new MapBuilder().put(".value", "d").put(".priority", 3).build())
            .build());

    TestHelpers.waitFor(semaphore, 2);

    assertEquals(2, results.size());
    assertEquals(ImmutableList.of("MOVED:b/a", "CHANGED:b/a"), results);
    ref.removeEventListener(listener);
  }
}
