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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.EventRecord;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.Query;
import com.google.firebase.database.TestFailure;
import com.google.firebase.database.TestHelpers;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.ValueExpectationHelper;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.database.future.WriteFuture;
import com.google.firebase.testing.IntegrationTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class QueryTestIT {

  private static FirebaseApp masterApp;

  @BeforeClass
  public static void setUpClass() throws IOException {
    masterApp = IntegrationTestUtils.ensureDefaultApp();
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
  public void testCreateBasicQueries() {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    // Just make sure they don't throw anything
    ref.limitToLast(10);
    ref.startAt("199").limitToLast(10);
    ref.startAt("199", "test").limitToLast(10);
    ref.endAt(199).limitToLast(1);
    ref.startAt(50, "test").endAt(100, "tree");
    ref.startAt(4).endAt(10);
    ref.startAt(null).endAt(10);
    ref.orderByChild("child");
    ref.orderByChild("child/deep/path");
    ref.orderByValue();
    ref.orderByPriority();
  }

  @Test
  public void testInvalidPathsToOrderBy() {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    List<String> badKeys = ImmutableList.<String>builder()
        .add("$child/foo", "$childfoo", "$/foo", "$child/foo/bar", "$child/.foo", ".priority",
            "$priority", "$key", ".key", "$child/.priority")
        .build();
    for (String key : badKeys) {
      try {
        ref.orderByChild(key);
        fail("Should throw");
      } catch (DatabaseException | IllegalArgumentException e) { // ignore
      }
    }
  }

  @Test
  public void testInvalidQueries() {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    try {
      ref.orderByKey().orderByPriority();
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().orderByValue();
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().orderByChild("foo");
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByValue().orderByPriority();
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByValue().orderByKey();
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByValue().orderByValue();
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByValue().orderByChild("foo");
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByChild("foo").orderByPriority();
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByChild("foo").orderByKey();
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByChild("foo").orderByValue();
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().startAt(1);
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().startAt(null);
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().endAt(null);
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().equalTo(null);
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().startAt("test", "test");
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().endAt(1);
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().endAt("test", "test");
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().orderByPriority();
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByPriority().orderByKey();
      fail("Should throw");
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByPriority().orderByValue();
      fail("Should throw");
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByPriority().orderByPriority();
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.limitToLast(1).limitToLast(1);
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.limitToFirst(1).limitToLast(1);
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.limitToLast(1).limitToFirst(1);
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.equalTo(true).endAt("test", "test");
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.equalTo(true).startAt("test", "test");
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.equalTo(true).equalTo("test", "test");
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.equalTo("test").equalTo(true);
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByChild("foo").orderByKey();
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.limitToFirst(5).limitToLast(10);
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.startAt(5).equalTo(10);
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByPriority().startAt(false);
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByPriority().endAt(true);
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByPriority().equalTo(true);
      fail("Should throw");
    } catch (DatabaseException | IllegalArgumentException e) { // ignore
    }
  }

  @Test
  public void testInvalidKeysInStartAtOrEndAtQueries() {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    List<String> badKeys = ImmutableList.of(".test", "test.", "fo$o", "[what", "ever]", "ha#sh",
        "/thing", "th/ing", "thing/");
    for (String key : badKeys) {
      try {
        ref.startAt(null, key);
        fail("Should throw");
      } catch (DatabaseException e) { // ignore

      }

      try {
        ref.endAt(null, key);
        fail("Should throw");
      } catch (DatabaseException e) { // ignore

      }
    }
  }

  @Test
  public void testRemoveListener()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final Semaphore semaphore = new Semaphore(0);
    ValueEventListener listener = ref.limitToLast(5)
        .addValueEventListener(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            semaphore.release();
          }

          @Override
          public void onCancelled(DatabaseError error) {
          }
        });

    ref.setValueAsync(MapBuilder.of("a", 5, "b", 6));
    TestHelpers.waitFor(semaphore, 1);
    ref.limitToLast(5).removeEventListener(listener);
    new WriteFuture(ref, MapBuilder.of("a", 6, "b", 5)).timedGet();
    TestHelpers.waitForQueue(ref);

    assertEquals(0, semaphore.availablePermits());
  }

  @Test
  public void testLimitQuery()
      throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ref.setValueAsync(new MapBuilder().put("a", 1).put("b", 2).put("c", 3).put("d", 4)
        .put("e", 5).put("f", 6).build());

    DataSnapshot snap = new ReadFuture(ref.limitToLast(5)).timedGet().get(0).getSnapshot();

    assertEquals(5, snap.getChildrenCount());
    assertFalse(snap.hasChild("a"));
    assertTrue(snap.hasChild("b"));
    assertTrue(snap.hasChild("f"));
  }

  @Test
  public void testLimitQueryServerResponse()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    for (int i = 0; i < 9; ++i) {
      ref.push().setValueAsync(i);
    }
    new WriteFuture(ref.push(), 9).timedGet();

    DataSnapshot snap = TestHelpers.getSnap(ref.limitToLast(5));

    long i = 5;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(i, child.getValue());
      i++;
    }

    assertEquals(10L, i);
  }

  @Test
  public void testMultipleLimitQueries() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ValueExpectationHelper expectations = new ValueExpectationHelper();
    expectations.add(ref.limitToLast(1), MapBuilder.of("c", 3L));
    expectations.add(ref.endAt(null).limitToLast(1), MapBuilder.of("c", 3L));
    expectations.add(ref.limitToLast(2), MapBuilder.of("b", 2L, "c", 3L));
    expectations.add(ref.limitToLast(3),
        MapBuilder.of("a", 1L, "b", 2L, "c", 3L));
    expectations.add(ref.limitToLast(4),
        MapBuilder.of("a", 1L, "b", 2L, "c", 3L));

    ref.setValueAsync(MapBuilder.of("a", 1L, "b", 2L, "c", 3L));

    expectations.waitForEvents();
  }

  @Test
  public void testMultipleLimitQueriesWithStartAt() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ValueExpectationHelper expectations = new ValueExpectationHelper();
    expectations.add(ref.startAt(null).limitToFirst(1),
        MapBuilder.of("a", 1L));
    expectations.add(ref.startAt(null, "c").limitToFirst(1),
        MapBuilder.of("c", 3L));
    expectations.add(ref.startAt(null, "b").limitToFirst(1),
        MapBuilder.of("b", 2L));
    expectations.add(ref.startAt(null, "b").limitToFirst(2),
        MapBuilder.of("b", 2L, "c", 3L));
    expectations.add(ref.startAt(null, "b").limitToFirst(3),
        MapBuilder.of("b", 2L, "c", 3L));

    ref.setValueAsync(MapBuilder.of("a", 1, "b", 2, "c", 3));
    expectations.waitForEvents();
  }

  @Test
  public void testMultipleLimitQueriesWithStartAtUsingServerData()
      throws InterruptedException, TestFailure, ExecutionException, TimeoutException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    // TODO: this test has race conditions. The listens are added sequentially, so we get a
    // lot of partial data back from the server. This all correct, and we
    // end up in the correct state, but it's still kinda weird. Consider having
    // ValueExpectationHelper deal with initial state.

    new WriteFuture(ref, MapBuilder.of("a", 1L, "b", 2L, "c", 3L)).timedGet();

    ValueExpectationHelper expectations = new ValueExpectationHelper();
    expectations.add(ref.startAt(null).limitToFirst(1), MapBuilder.of("a", 1L));
    expectations.add(ref.startAt(null, "c").limitToFirst(1),
        MapBuilder.of("c", 3L));
    expectations.add(ref.startAt(null, "b").limitToFirst(1),
        MapBuilder.of("b", 2L));
    expectations.add(ref.startAt(null, "b").limitToFirst(2),
        MapBuilder.of("b", 2L, "c", 3L));
    expectations.add(ref.startAt(null, "b").limitToFirst(3),
        MapBuilder.of("b", 2L, "c", 3L));

    expectations.waitForEvents();
  }

  @Test
  public void testLimitQueryChildEvents()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<String> added = new ArrayList<>();
    final List<String> removed = new ArrayList<>();

    ref.limitToLast(2).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        added.add(snapshot.getKey());
      }

      @Override
      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
        // no-op
      }

      @Override
      public void onChildRemoved(DataSnapshot snapshot) {
        removed.add(snapshot.getKey());
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        // no-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    new WriteFuture(ref, MapBuilder.of("a", 1, "b", 2,"c", 3)).timedGet();
    TestHelpers.assertDeepEquals(ImmutableList.of("b", "c"), added);
    assertTrue(removed.isEmpty());

    added.clear();
    new WriteFuture(ref.child("d"), 4).timedGet();
    assertEquals(1, added.size());
    assertEquals("d", added.get(0));
    assertEquals(1, removed.size());
    assertEquals("b", removed.get(0));
  }

  @Test
  public void testLimitQueryChildEventsUsingServerData()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<String> added = new ArrayList<>();
    final List<String> removed = new ArrayList<>();

    new WriteFuture(ref, MapBuilder.of("a", 1,"b", 2,"c", 3)).timedGet();
    final Semaphore semaphore = new Semaphore(0);
    ref.limitToLast(2).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        added.add(snapshot.getKey());
        semaphore.release(1);
      }

      @Override
      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
        // no-op
      }

      @Override
      public void onChildRemoved(DataSnapshot snapshot) {
        removed.add(snapshot.getKey());
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        // no-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    TestHelpers.waitFor(semaphore, 2);
    TestHelpers.assertDeepEquals(ImmutableList.of("b", "c"), added);
    assertTrue(removed.isEmpty());
    added.clear();

    new WriteFuture(ref.child("d"), 4).timedGet();
    assertEquals(1, added.size());
    assertEquals("d", added.get(0));
    assertEquals(1, removed.size());
    assertEquals("b", removed.get(0));
  }

  @Test
  public void testLimitQueryChildEventsWithStartAt()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<String> added = new ArrayList<>();
    final List<String> removed = new ArrayList<>();

    ref.startAt(null, "a").limitToFirst(2).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        added.add(snapshot.getKey());
      }

      @Override
      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
        // no-op
      }

      @Override
      public void onChildRemoved(DataSnapshot snapshot) {
        removed.add(snapshot.getKey());
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        // no-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    new WriteFuture(ref, MapBuilder.of("a", 1,"b", 2,"c", 3)).timedGet();
    TestHelpers.assertDeepEquals(ImmutableList.of("a", "b"), added);
    assertTrue(removed.isEmpty());
    added.clear();

    new WriteFuture(ref.child("aa"), 4).timedGet();
    assertEquals(1, added.size());
    assertEquals("aa", added.get(0));
    assertEquals(1, removed.size());
    assertEquals("b", removed.get(0));
  }

  @Test
  public void testLimitQueryChildEventsWithStartAtUsingServerData()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<String> added = new ArrayList<>();
    final List<String> removed = new ArrayList<>();

    new WriteFuture(ref, MapBuilder.of("a", 1,"b", 2,"c", 3)).timedGet();
    final Semaphore semaphore = new Semaphore(0);
    ref.startAt(null, "a").limitToFirst(2).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        added.add(snapshot.getKey());
        semaphore.release(1);
      }

      @Override
      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
        // no-op
      }

      @Override
      public void onChildRemoved(DataSnapshot snapshot) {
        removed.add(snapshot.getKey());
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        // no-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    TestHelpers.waitFor(semaphore, 2);
    TestHelpers.assertDeepEquals(ImmutableList.of("a", "b"), added);
    assertTrue(removed.isEmpty());

    added.clear();
    new WriteFuture(ref.child("aa"), 4).timedGet();
    assertEquals(1, added.size());
    assertEquals("aa", added.get(0));
    assertEquals(1, removed.size());
    assertEquals("b", removed.get(0));
  }

  @Test
  public void testLimitQueryUnderLimitChildEventsWithStartAt()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<String> added = new ArrayList<>();
    final List<String> removed = new ArrayList<>();

    ref.startAt(null, "a").limitToFirst(2).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        added.add(snapshot.getKey());
      }

      @Override
      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
        // no-op
      }

      @Override
      public void onChildRemoved(DataSnapshot snapshot) {
        removed.add(snapshot.getKey());
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        // no-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    new WriteFuture(ref, MapBuilder.of("c", 3)).timedGet();
    TestHelpers.assertDeepEquals(ImmutableList.of("c"), added);
    assertTrue(removed.isEmpty());
    added.clear();

    new WriteFuture(ref.child("b"), 4).timedGet();
    TestHelpers.assertDeepEquals(ImmutableList.of("b"), added);
    assertTrue(removed.isEmpty());
  }

  @Test
  public void testLimitQueryUnderLimitChildEventsWithStartAtUsingServerData()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    new WriteFuture(ref, MapBuilder.of("c", 3)).timedGet();

    final List<String> added = new ArrayList<>();
    final List<String> removed = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);
    ref.startAt(null, "a").limitToFirst(2).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        added.add(snapshot.getKey());
        semaphore.release(1);
      }

      @Override
      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
        // no-op
      }

      @Override
      public void onChildRemoved(DataSnapshot snapshot) {
        removed.add(snapshot.getKey());
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        // no-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    TestHelpers.waitFor(semaphore);
    TestHelpers.assertDeepEquals(ImmutableList.of("c"), added);
    assertTrue(removed.isEmpty());
    added.clear();

    new WriteFuture(ref.child("b"), 4).timedGet();
    TestHelpers.assertDeepEquals(ImmutableList.of("b"), added);
    assertTrue(removed.isEmpty());
  }

  @Test
  public void testSetLimitChildAddedAndChildRemovedWhenAnElementIsRemoved()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<String> added = new ArrayList<>();
    final List<String> removed = new ArrayList<>();

    ref.limitToLast(2).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        added.add(snapshot.getKey());
      }

      @Override
      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
        // no-op
      }

      @Override
      public void onChildRemoved(DataSnapshot snapshot) {
        removed.add(snapshot.getKey());
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        // no-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    new WriteFuture(ref, MapBuilder.of("a", 1,"b", 2,"c", 3)).timedGet();
    TestHelpers.assertDeepEquals(ImmutableList.of("b", "c"), added);
    assertTrue(removed.isEmpty());
    added.clear();

    new WriteFuture(ref.child("b"), null).timedGet();
    TestHelpers.assertDeepEquals(ImmutableList.of("a"), added);
    TestHelpers.assertDeepEquals(ImmutableList.of("b"), removed);
  }

  @Test
  public void testLimitQueryChildEventsWithNodeDeleteUsingServerData()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    new WriteFuture(ref, MapBuilder.of("a", 1,"b", 2,"c", 3)).timedGet();
    final List<String> added = new ArrayList<>();
    final List<String> removed = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);

    ref.limitToLast(2).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        added.add(snapshot.getKey());
        semaphore.release(1);
      }

      @Override
      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
        // no-op
      }

      @Override
      public void onChildRemoved(DataSnapshot snapshot) {
        removed.add(snapshot.getKey());
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        // no-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    TestHelpers.waitFor(semaphore, 2);
    TestHelpers.assertDeepEquals(ImmutableList.of("b", "c"), added);
    assertTrue(removed.isEmpty());

    added.clear();
    new WriteFuture(ref.child("b"), null).timedGet();
    TestHelpers.assertDeepEquals(ImmutableList.of("a"), added);
    TestHelpers.assertDeepEquals(ImmutableList.of("b"), removed);
  }

  @Test
  public void testSetLimitChildRemovedWhenAllElementsRemoved()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<String> added = new ArrayList<>();
    final List<String> removed = new ArrayList<>();

    ref.limitToLast(2).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        added.add(snapshot.getKey());
      }

      @Override
      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
        // no-op
      }

      @Override
      public void onChildRemoved(DataSnapshot snapshot) {
        removed.add(snapshot.getKey());
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        // no-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    new WriteFuture(ref, MapBuilder.of("b", 2,"c", 3)).timedGet();
    TestHelpers.assertDeepEquals(ImmutableList.of("b", "c"), added);
    assertTrue(removed.isEmpty());
    added.clear();

    new WriteFuture(ref.child("b"), null).timedGet();
    assertTrue(added.isEmpty());
    TestHelpers.assertDeepEquals(ImmutableList.of("b"), removed);

    new WriteFuture(ref.child("c"), null).timedGet();
    assertTrue(added.isEmpty());
    TestHelpers.assertDeepEquals(ImmutableList.of("b", "c"), removed);
  }

  @Test
  public void testSetLimitChildRemovedWhenAllElementsRemovedUsingServerData()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    new WriteFuture(ref, MapBuilder.of("b", 2,"c", 3)).timedGet();
    final List<String> added = new ArrayList<>();
    final List<String> removed = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);
    final ChildEventListener listener = ref.limitToLast(2)
        .addChildEventListener(new ChildEventListener() {
          @Override
          public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
            added.add(snapshot.getKey());
            semaphore.release(1);
          }

          @Override
          public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
            // no-op
          }

          @Override
          public void onChildRemoved(DataSnapshot snapshot) {
            removed.add(snapshot.getKey());
          }

          @Override
          public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
            // no-op
          }

          @Override
          public void onCancelled(DatabaseError error) {
          }
        });

    TestHelpers.waitFor(semaphore, 2);
    TestHelpers.assertDeepEquals(ImmutableList.of("b", "c"), added);
    assertTrue(removed.isEmpty());

    added.clear();
    new WriteFuture(ref.child("b"), null).timedGet();
    assertTrue(added.isEmpty());
    TestHelpers.assertDeepEquals(ImmutableList.of("b"), removed);
    new WriteFuture(ref.child("c"), null).timedGet();
    assertTrue(added.isEmpty());
    TestHelpers.assertDeepEquals(ImmutableList.of("b", "c"), removed);
    ref.limitToLast(2).removeEventListener(listener);
  }

  @Test
  public void testStartAtEndAtWithPriority() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ValueExpectationHelper helper = new ValueExpectationHelper();
    helper.add(ref.startAt("w").endAt("y"),
        MapBuilder.of("b", 2L, "c", 3L, "d", 4L));
    helper.add(ref.startAt("w").endAt("w"), MapBuilder.of("d", 4L));
    helper.add(ref.startAt("a").endAt("c"), null);

    ref.setValueAsync(
        new MapBuilder()
            .put("a", MapBuilder.of(".value", 1, ".priority", "z"))
            .put("b", MapBuilder.of(".value", 2, ".priority", "y"))
            .put("c", MapBuilder.of(".value", 3, ".priority", "x"))
            .put("d", MapBuilder.of(".value", 4, ".priority", "w")).build());

    helper.waitForEvents();
  }

  @Test
  public void testStartAtEndAtWithPriorityUsingServerData() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ref.setValueAsync(
        new MapBuilder()
            .put("a", MapBuilder.of(".value", 1, ".priority", "z"))
            .put("b", MapBuilder.of(".value", 2, ".priority", "y"))
            .put("c", MapBuilder.of(".value", 3, ".priority", "x"))
            .put("d", MapBuilder.of(".value", 4, ".priority", "w")).build());

    ValueExpectationHelper helper = new ValueExpectationHelper();
    helper.add(ref.startAt("w").endAt("y"),
        MapBuilder.of("b", 2L, "c", 3L, "d", 4L));
    helper.add(ref.startAt("w").endAt("w"), MapBuilder.of("d", 4L));
    helper.add(ref.startAt("a").endAt("c"), null);

    helper.waitForEvents();
  }

  @Test
  public void testStartAtEndAtWithPriorityAndName() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ValueExpectationHelper helper = new ValueExpectationHelper();
    helper.add(ref.startAt(1, "a").endAt(2, "d"),
        new MapBuilder().put("a", 1L).put("b", 2L).put("c", 3L).put("d", 4L).build());
    helper.add(ref.startAt(1, "b").endAt(2, "c"),
        MapBuilder.of("b", 2L, "c", 3L));
    helper.add(ref.startAt(1, "c").endAt(2),
        MapBuilder.of("c", 3L, "d", 4L));

    ref.setValueAsync(
        new MapBuilder()
            .put("a", MapBuilder.of(".value", 1, ".priority", 1))
            .put("b", MapBuilder.of(".value", 2, ".priority", 1))
            .put("c", MapBuilder.of(".value", 3, ".priority", 2))
            .put("d", MapBuilder.of(".value", 4, ".priority", 2)).build());

    helper.waitForEvents();
  }

  @Test
  public void testStartAtEndAtWithPriorityAndName2() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ValueExpectationHelper helper = new ValueExpectationHelper();
    helper.add(ref.startAt(1, "c").endAt(2, "b"),
        new MapBuilder().put("a", 1L).put("b", 2L).put("c", 3L).put("d", 4L).build());
    helper.add(ref.startAt(1, "d").endAt(2, "a"),
        MapBuilder.of("d", 4L, "a", 1L));
    helper.add(ref.startAt(1, "e").endAt(2),
        MapBuilder.of("a", 1L, "b", 2L));

    ref.setValueAsync(
        new MapBuilder()
            .put("c", MapBuilder.of(".value", 3, ".priority", 1))
            .put("d", MapBuilder.of(".value", 4, ".priority", 1))
            .put("a", MapBuilder.of(".value", 1, ".priority", 2))
            .put("b", MapBuilder.of(".value", 2, ".priority", 2)).build());

    helper.waitForEvents();
  }

  @Test
  public void testStartAtEndAtWithPriorityAndNameUsingServerData() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ref.setValueAsync(
        new MapBuilder()
            .put("a", MapBuilder.of(".value", 1, ".priority", 1))
            .put("b", MapBuilder.of(".value", 2, ".priority", 1))
            .put("c", MapBuilder.of(".value", 3, ".priority", 2))
            .put("d", MapBuilder.of(".value", 4, ".priority", 2)).build());

    ValueExpectationHelper helper = new ValueExpectationHelper();
    helper.add(ref.startAt(1, "a").endAt(2, "d"),
        new MapBuilder().put("a", 1L).put("b", 2L).put("c", 3L).put("d", 4L).build());
    helper.add(ref.startAt(1, "b").endAt(2, "c"),
        MapBuilder.of("b", 2L, "c", 3L));
    helper.add(ref.startAt(1, "c").endAt(2),
        MapBuilder.of("c", 3L, "d", 4L));

    helper.waitForEvents();
  }

  @Test
  public void testStartAtEndAtWithPriorityAndNameUsingServerData2() throws
      InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ref.setValueAsync(
        new MapBuilder()
            .put("c", MapBuilder.of(".value", 3, ".priority", 1))
            .put("d", MapBuilder.of(".value", 4, ".priority", 1))
            .put("a", MapBuilder.of(".value", 1, ".priority", 2))
            .put("b", MapBuilder.of(".value", 2, ".priority", 2)).build());

    ValueExpectationHelper helper = new ValueExpectationHelper();
    helper.add(ref.startAt(1, "c").endAt(2, "b"),
        new MapBuilder().put("a", 1L).put("b", 2L).put("c", 3L).put("d", 4L).build());
    helper.add(ref.startAt(1, "d").endAt(2, "a"),
        MapBuilder.of("d", 4L, "a", 1L));
    helper.add(ref.startAt(1, "e").endAt(2),
        MapBuilder.of("a", 1L, "b", 2L));

    helper.waitForEvents();
  }

  @Test
  public void testPrevNameWithLimit()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<String> names = new ArrayList<>();
    ref.limitToLast(2).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        names.add(snapshot.getKey() + " " + previousChildName);
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
        // No-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    ref.child("a").setValueAsync(1);
    ref.child("c").setValueAsync(3);
    ref.child("b").setValueAsync(2);
    new WriteFuture(ref.child("d"), 4).timedGet();

    TestHelpers.assertDeepEquals(ImmutableList.of("a null", "c a", "b null", "d c"), names);
  }

  // NOTE: skipping server data test here, it really doesn't test anything
  // extra

  @Test
  public void testSetLimitWithMoveNodes()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<String> names = new ArrayList<>();
    ref.limitToLast(2).addChildEventListener(new ChildEventListener() {
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
        names.add(snapshot.getKey() + " " + previousChildName);
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    ref.child("a").setValueAsync("a", 10);
    ref.child("b").setValueAsync("b", 20);
    ref.child("c").setValueAsync("c", 30);
    ref.child("d").setValueAsync("d", 40);

    // Start moving things
    ref.child("c").setPriorityAsync(50);
    ref.child("c").setPriorityAsync(35);
    new WriteFuture(ref.child("b"), "b", 33).timedGet();

    TestHelpers.assertDeepEquals(ImmutableList.of("c d", "c null"), names);
  }

  // NOTE: skipping server data version of the above test, it doesn't really
  // test anything new

  // NOTE: skipping numeric priority test, the same functionality is tested
  // above

  // NOTE: skipping local add / remove test w/ limits. Tested above

  @Test
  public void testSetLimitWithAddNodesRemotely()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final List<String> events = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);

    ReadFuture future = new ReadFuture(reader);
    final ChildEventListener listener = reader.limitToLast(2)
        .addChildEventListener(new ChildEventListener() {
          @Override
          public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
            events.add(snapshot.getValue().toString() + " added");
            semaphore.release(1);
          }

          @Override
          public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
            // No-op
          }

          @Override
          public void onChildRemoved(DataSnapshot snapshot) {
            events.add(snapshot.getValue().toString() + " removed");
          }

          @Override
          public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
            // No-op
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        });
    // Wait for initial load
    future.timedGet();

    for (int i = 0; i < 4; ++i) {
      writer.push().setValueAsync(i);
    }
    new WriteFuture(writer.push(), 4).timedGet();
    List<String> expected = ImmutableList.<String>builder().add(
        "0 added", "1 added", "0 removed", "2 added", "1 removed",
        "3 added", "2 removed", "4 added").build();
    // Make sure we wait for all the events
    TestHelpers.waitFor(semaphore, 5);
    TestHelpers.assertDeepEquals(expected, events);
    reader.limitToLast(2).removeEventListener(listener);
  }

  @Test
  public void testAttachingListener() {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ValueEventListener listener = ref.limitToLast(1)
        .addValueEventListener(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            // No-op
          }

          @Override
          public void onCancelled(DatabaseError error) {
          }
        });

    assertNotNull(listener);
  }

  @Test
  public void testLimitOnUnsyncedNode()
      throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    // This will timeout if value never fires
    assertEquals(1, new ReadFuture(ref.limitToLast(1)).timedGet().size());
  }

  @Test
  public void testFilteringOnlyNullPriorities() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ref.setValueAsync(
        new MapBuilder().put("a", new MapBuilder().put(".priority", null).put(".value", 0).build())
            .put("b", new MapBuilder().put(".priority", null).put(".value", 1).build())
            .put("c", new MapBuilder().put(".priority", "2").put(".value", 2).build())
            .put("d", new MapBuilder().put(".priority", 3).put(".value", 3).build())
            .put("e", new MapBuilder().put(".priority", "hi").put(".value", 4).build()).build());

    DataSnapshot snap = TestHelpers.getSnap(ref.endAt(null));
    Map<String, Object> expected = MapBuilder.of("a", 0L,"b", 1L);
    Object result = snap.getValue();
    TestHelpers.assertDeepEquals(expected, result);
  }

  @Test
  public void testNullPrioritiesIncludedInEndAt() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ref.setValueAsync(
        new MapBuilder()
            .put("a", MapBuilder.of(".priority", null, ".value", 0))
            .put("b", MapBuilder.of(".priority", null, ".value", 1))
            .put("c", MapBuilder.of(".priority", 2, ".value", 2))
            .put("d", MapBuilder.of(".priority", 3, ".value", 3))
            .put("e", MapBuilder.of(".priority", "hi", ".value", 4)).build());

    DataSnapshot snap = TestHelpers.getSnap(ref.endAt(2));
    Map<String, Object> expected = MapBuilder.of("a", 0L, "b", 1L, "c", 2L);
    Object result = snap.getValue();
    TestHelpers.assertDeepEquals(expected, result);
  }

  @Test
  public void testNullPrioritiesIncludedInStartAt() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ref.setValueAsync(
        new MapBuilder()
            .put("a", new MapBuilder().put(".priority", null).put(".value", 0).build())
            .put("b", new MapBuilder().put(".priority", null).put(".value", 1).build())
            .put("c", new MapBuilder().put(".priority", 2).put(".value", 2).build())
            .put("d", new MapBuilder().put(".priority", 3).put(".value", 3).build())
            .put("e", new MapBuilder().put(".priority", "hi").put(".value", 4).build()).build());

    DataSnapshot snap = TestHelpers.getSnap(ref.startAt(2));
    Object result = snap.getValue();
    Map<String, Object> expected = MapBuilder.of("c", 2L, "d", 3L, "e", 4L);
    TestHelpers.assertDeepEquals(expected, result);
  }

  @Test
  public void testLimitWithMixOfNullAndNonNullPrioritiesUsingServerData() throws
      InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    Map<String, Object> toSet = new MapBuilder()
        .put("Vikrum",
            new MapBuilder().put(".priority", 1000.0).put("score", 1000L).put("name", "Vikrum")
                .build())
        .put("Mike",
            new MapBuilder().put(".priority", 500.0).put("score", 500L).put("name", "Mike").build())
        .put("Andrew",
            new MapBuilder().put(".priority", 50.0).put("score", 50L).put("name", "Andrew").build())
        .put("James",
            new MapBuilder().put(".priority", 7.0).put("score", 7L).put("name", "James").build())
        .put("Sally",
            new MapBuilder().put(".priority", -7.0).put("score", -7L).put("name", "Sally").build())
        .put("Fred", new MapBuilder().put("score", 0.0).put("name", "Fred").build()).build();
    ref.setValueAsync(toSet);
    final Semaphore semaphore = new Semaphore(0);
    final List<String> names = new ArrayList<>();
    ref.limitToLast(5).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        names.add(snapshot.getKey());
        semaphore.release(1);
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
        // No-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    TestHelpers.waitFor(semaphore, 5);
    TestHelpers.assertDeepEquals(ImmutableList.of("Sally", "James", "Andrew", "Mike", "Vikrum"),
        names);
  }

  @Test
  public void testLimitOnNodeWithPriority() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    final Semaphore semaphore = new Semaphore(0);

    final Map<String, Object> data = MapBuilder.of("a", "blah", ".priority", "priority");

    ref.setValue(data, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        ref.limitToLast(2).addListenerForSingleValueEvent(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            Map<String, Object> expected = MapBuilder.of("a", "blah");
            TestHelpers.assertDeepEquals(expected, snapshot.getValue(true));
            semaphore.release();
          }

          @Override
          public void onCancelled(DatabaseError error) {
          }
        });
      }
    });

    TestHelpers.waitFor(semaphore);
  }

  @Test
  public void testLimitWithMixOfNullAndNonNullPriorities() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    Map<String, Object> toSet = new MapBuilder()
        .put("Vikrum",
            new MapBuilder().put(".priority", 1000.0).put("score", 1000L).put("name", "Vikrum")
                .build())
        .put("Mike",
            new MapBuilder().put(".priority", 500.0).put("score", 500L).put("name", "Mike").build())
        .put("Andrew",
            new MapBuilder().put(".priority", 50.0).put("score", 50L).put("name", "Andrew").build())
        .put("James",
            new MapBuilder().put(".priority", 7.0).put("score", 7L).put("name", "James").build())
        .put("Sally",
            new MapBuilder().put(".priority", -7.0).put("score", -7L).put("name", "Sally").build())
        .put("Fred", new MapBuilder().put("score", 0.0).put("name", "Fred").build()).build();

    final Semaphore semaphore = new Semaphore(0);
    final List<String> names = new ArrayList<>();
    final ChildEventListener listener = ref.limitToLast(5)
        .addChildEventListener(new ChildEventListener() {
          @Override
          public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
            names.add(snapshot.getKey());
            semaphore.release(1);
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
            // No-op
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        });
    ref.setValueAsync(toSet);
    TestHelpers.waitFor(semaphore, 5);
    TestHelpers.assertDeepEquals(ImmutableList.of("Sally", "James", "Andrew", "Mike", "Vikrum"),
        names);
    ref.limitToLast(5).removeEventListener(listener);
  }

  // NOTE: skipping tests for js context argument

  @Test
  public void testDeletingEntireQueryWindow()
      throws InterruptedException, TestFailure, TimeoutException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final ReadFuture readFuture = ReadFuture.untilCount(ref.limitToLast(2), 3);

    // wait for null event
    TestHelpers.waitForRoundtrip(ref);

    ref.setValueAsync(
        MapBuilder.of(
            "a", MapBuilder.of(".value", 1, ".priority", 1),
            "b", MapBuilder.of(".value", 2, ".priority", 2),
            "c", MapBuilder.of(".value", 3, ".priority", 3)));

    ref.updateChildrenAsync(new MapBuilder().put("b", null).put("c", null).build());
    List<EventRecord> events = readFuture.timedGet();
    DataSnapshot snap = events.get(1).getSnapshot();

    Map<String, Object> expected = MapBuilder.of("b", 2L, "c", 3L);
    Object result = snap.getValue();
    TestHelpers.assertDeepEquals(expected, result);

    // The original set is still outstanding (synchronous API), so we have a
    // full cache to re-window against
    snap = events.get(2).getSnapshot();
    result = snap.getValue();
    TestHelpers.assertDeepEquals(MapBuilder.of("a", 1L), result);
  }

  @Test
  public void testOutOfViewQueryOnAChild()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final ReadFuture parentFuture = ReadFuture.untilCountAfterNull(ref.limitToLast(1), 2);

    final List<DataSnapshot> childSnaps = new ArrayList<>();
    ref.child("a").addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        childSnaps.add(snapshot);
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    new WriteFuture(ref, MapBuilder.of("a", 1, "b", 2)).timedGet();
    assertEquals(1L, childSnaps.get(0).getValue());
    ref.updateChildrenAsync(MapBuilder.of("c", 3));
    List<EventRecord> events = parentFuture.timedGet();
    DataSnapshot snap = events.get(0).getSnapshot();
    Object result = snap.getValue();

    Map<String, Object> expected = MapBuilder.of("b", 2L);
    TestHelpers.assertDeepEquals(expected, result);

    snap = events.get(1).getSnapshot();
    result = snap.getValue();

    expected = MapBuilder.of("c", 3L);
    TestHelpers.assertDeepEquals(expected, result);
    assertEquals(1, childSnaps.size());
    assertEquals(1L, childSnaps.get(0).getValue());
  }

  @Test
  public void testChildQueryGoingOutOfViewOfTheParent()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final ReadFuture parentFuture = ReadFuture.untilCountAfterNull(ref.limitToLast(1), 3);
    final List<DataSnapshot> childSnaps = new ArrayList<>();
    final ValueEventListener listener = ref.child("a").addValueEventListener(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            childSnaps.add(snapshot);
          }
      
          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        });

    new WriteFuture(ref, MapBuilder.of("a", 1)).timedGet();
    assertEquals(1L, childSnaps.get(0).getValue());
    new WriteFuture(ref.child("b"), 2).timedGet();
    assertEquals(1, childSnaps.size());
    new WriteFuture(ref.child("b"), null).timedGet();
    List<EventRecord> events = parentFuture.timedGet();
    assertEquals(1, childSnaps.size());

    Object result;
    Map<String, Object> expected;

    result = events.get(0).getSnapshot().getValue();
    expected = MapBuilder.of("a", 1L);
    TestHelpers.assertDeepEquals(expected, result);

    result = events.get(1).getSnapshot().getValue();
    expected = MapBuilder.of("b", 2L);
    TestHelpers.assertDeepEquals(expected, result);

    result = events.get(0).getSnapshot().getValue();
    expected = MapBuilder.of("a", 1L);
    TestHelpers.assertDeepEquals(expected, result);
    ref.child("a").removeEventListener(listener);
  }

  @Test
  public void testDivergingViews()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<DataSnapshot> cSnaps = new ArrayList<>();
    final List<DataSnapshot> dSnaps = new ArrayList<>();

    ref.endAt(null, "c").limitToLast(1).addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        if (snapshot.getValue() != null) {
          cSnaps.add(snapshot);
        }
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    ref.endAt(null, "d").limitToLast(1).addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        if (snapshot.getValue() != null) {
          dSnaps.add(snapshot);
        }
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    new WriteFuture(ref, MapBuilder.of("a", 1, "b", 2, "c", 3)).timedGet();
    assertEquals(1, cSnaps.size());
    final Map<String, Object> cExpected = MapBuilder.of("c", 3L);
    TestHelpers.assertDeepEquals(cExpected, cSnaps.get(0).getValue());

    new WriteFuture(ref.child("d"), 4).timedGet();
    assertEquals(1, cSnaps.size());

    assertEquals(2, dSnaps.size());
    TestHelpers.assertDeepEquals(cExpected, dSnaps.get(0).getValue());

    TestHelpers.assertDeepEquals(MapBuilder.of("d", 4L), dSnaps.get(1).getValue());
  }

  @Test
  public void testRemovingQueriedElement()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<Long> values = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);
    ref.limitToLast(1).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        Long val = (Long) snapshot.getValue();
        values.add(val);
        if (val == 1L) {
          semaphore.release(1);
        }
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
        // No-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    ref.setValueAsync(MapBuilder.of("a", 1, "b", 2));
    ref.child("b").removeValueAsync();
    TestHelpers.waitFor(semaphore);
    TestHelpers.assertDeepEquals(ImmutableList.of(2L, 1L), values);
  }

  @Test
  public void testStartAtLimit() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ref.setValueAsync(MapBuilder.of("a", 1, "b", 2));
    DataSnapshot snap = TestHelpers.getSnap(ref.limitToFirst(1));

    assertEquals(1L, snap.child("a").getValue());
  }

  @Test
  public void testStartAtLimitWhenChildIsRemoved() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ref.setValueAsync(MapBuilder.of("a", 1, "b", 2));
    final List<Long> values = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);
    ref.limitToFirst(1).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        Long val = (Long) snapshot.getValue();
        values.add(val);
        semaphore.release(1);
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
        // No-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    // Wait for first value
    TestHelpers.waitFor(semaphore);
    assertEquals((Long) 1L, values.get(0));
    ref.child("a").removeValueAsync();
    TestHelpers.waitFor(semaphore);
    assertEquals((Long) 2L, values.get(1));
  }

  @Test
  public void testStartAtWithTwoArguments()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    new WriteFuture(ref,
        MapBuilder.of(
            "Walker", MapBuilder.of("name", "Walker", "score", 20, ".priority", 20),
            "Michael", MapBuilder.of("name", "Michael", "score", 100, ".priority", 100)))
        .timedGet();

    DataSnapshot snap = TestHelpers.getSnap(ref.startAt(20, "Walker").limitToFirst(2));
    List<String> expected = ImmutableList.of("Walker", "Michael");
    int i = 0;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(expected.get(i), child.getKey());
      i++;
    }
    assertEquals(2, i);
  }

  @Test
  public void testMultipleQueriesOnTheSameNode()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    new WriteFuture(ref, new MapBuilder().put("a", 1).put("b", 2).put("c", 3).put("d", 4)
        .put("e", 5).put("f", 6).build()).timedGet();

    final AtomicInteger limit2Called = new AtomicInteger(0);
    final Semaphore semaphore = new Semaphore(0);
    ref.limitToLast(2).addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        // Should only be called once
        if (limit2Called.incrementAndGet() == 1) {
          semaphore.release(1);
        }
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    // Skipping nested calls, no re-entrant APIs in Java

    TestHelpers.waitFor(semaphore);
    assertEquals(1, limit2Called.get());

    DataSnapshot snap = TestHelpers.getSnap(ref.limitToLast(1));
    TestHelpers.assertDeepEquals(MapBuilder.of("f", 6L), snap.getValue());
  }

  @Test
  public void testNodeWithDefaultListener()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    new WriteFuture(ref, new MapBuilder().put("a", 1).put("b", 2).put("c", 3).put("d", 4)
        .put("e", 5).put("f", 6).build()).timedGet();

    final AtomicInteger onCalled = new AtomicInteger(0);
    final Semaphore semaphore = new Semaphore(0);
    ref.addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        // Should only be called once
        if (onCalled.incrementAndGet() == 1) {
          semaphore.release(1);
        }
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    TestHelpers.waitFor(semaphore);
    assertEquals(1, onCalled.get());

    DataSnapshot snap = TestHelpers.getSnap(ref.limitToLast(1));
    TestHelpers.assertDeepEquals(MapBuilder.of("f", 6L), snap.getValue());
  }

  @Test
  public void testNodeWithDefaultListenerAndNonCompleteLimit()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    new WriteFuture(ref, MapBuilder.of("a", 1, "b", 2, "c", 3)).timedGet();

    final AtomicInteger onCalled = new AtomicInteger(0);
    final Semaphore semaphore = new Semaphore(0);
    ref.addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        // Should only be called once
        if (onCalled.incrementAndGet() == 1) {
          semaphore.release(1);
        }
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    TestHelpers.waitFor(semaphore);
    assertEquals(1, onCalled.get());

    DataSnapshot snap = TestHelpers.getSnap(ref.limitToLast(5));
    TestHelpers.assertDeepEquals(MapBuilder.of("a", 1L, "b", 2L, "c", 3L), snap.getValue());
  }

  @Test
  public void testRemoteRemoveEvent()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    final DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    Map<String, Object> expected = new MapBuilder()
        .put("a", "a").put("b", "b").put("c", "c").put("d", "d").put("e", "e").build();

    new WriteFuture(writer, expected).timedGet();

    List<EventRecord> events = new ReadFuture(reader.limitToLast(5),
        new ReadFuture.CompletionCondition() {
          @Override
          public boolean isComplete(List<EventRecord> events) {
            if (events.size() == 1) {
              writer.child("c").removeValueAsync();
            }
            return events.size() == 2;
          }
        }).timedGet();

    TestHelpers.assertDeepEquals(expected, events.get(0).getSnapshot().getValue());
    TestHelpers.assertDeepEquals(
        new MapBuilder().put("a", "a").put("b", "b").put("d", "d").put("e", "e").build(),
        events.get(1).getSnapshot().getValue());
  }

  @Test
  public void testEndAtWithTwoArgumentsAndLimit()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    Map<String, Object> toSet = new MapBuilder().put("a", "a")
        .put("b", "b").put("c", "c").put("d", "d").put("e", "e").put("f", "f")
        .put("g", "g").put("h", "h").build();

    new WriteFuture(ref, toSet).timedGet();

    DataSnapshot snap = TestHelpers.getSnap(ref.endAt(null, "f").limitToLast(5));
    Map<String, Object> expected = new MapBuilder().put("b", "b")
        .put("c", "c").put("d", "d").put("e", "e").put("f", "f").build();

    TestHelpers.assertDeepEquals(expected, snap.getValue());
  }

  @Test
  public void testComplexUpdateQueryRoot()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    Map<String, Object> toSet = new MapBuilder().put("a", 1).put("b", 2).put("c", 3).put("d", 4)
        .put("e", 5).build();

    new WriteFuture(writer, toSet).timedGet();
    final Semaphore semaphore = new Semaphore(0);
    ReadFuture future = new ReadFuture(reader.limitToFirst(4),
        new ReadFuture.CompletionCondition() {
          @Override
          public boolean isComplete(List<EventRecord> events) {
            if (events.size() == 1) {
              semaphore.release(1);
            }
            return events.size() == 2;
          }
        });

    TestHelpers.waitFor(semaphore);
    Map<String, Object> update = new MapBuilder().put("b", null).put("c", "a").put("cc", "new")
        .put("cd", "new2").put("d", "gone").build();
    writer.updateChildrenAsync(update);
    List<EventRecord> events = future.timedGet();

    Map<String, Object> expected = new MapBuilder().put("a", 1L).put("b", 2L).put("c", 3L)
        .put("d", 4L).build();
    Object result = events.get(0).getSnapshot().getValue();
    TestHelpers.assertDeepEquals(expected, result);

    expected = new MapBuilder().put("a", 1L).put("c", "a").put("cc", "new")
        .put("cd", "new2").build();
    result = events.get(1).getSnapshot().getValue();
    TestHelpers.assertDeepEquals(expected, result);
  }

  @Test
  public void testUpdateQueryRoot()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    Map<String, Object> toSet = MapBuilder.of("bar", "a", "baz", "b", "bam", "c");

    new WriteFuture(writer, toSet).timedGet();
    final Semaphore semaphore = new Semaphore(0);
    ReadFuture future = new ReadFuture(reader.limitToLast(10),
        new ReadFuture.CompletionCondition() {
          @Override
          public boolean isComplete(List<EventRecord> events) {
            if (events.size() == 1) {
              semaphore.release(1);
            }
            return events.size() == 2;
          }
        });

    TestHelpers.waitFor(semaphore);
    Map<String, Object> update = new MapBuilder().put("bar", "d").put("bam", null).put("bat", "e")
        .build();
    writer.updateChildrenAsync(update);
    List<EventRecord> events = future.timedGet();

    Map<String, Object> expected = MapBuilder.of("bar", "a", "baz", "b", "bam",
        "c");
    Object result = events.get(0).getSnapshot().getValue();
    TestHelpers.assertDeepEquals(expected, result);

    expected = MapBuilder.of("bar", "d", "baz", "b", "bat", "e");
    result = events.get(1).getSnapshot().getValue();
    TestHelpers.assertDeepEquals(expected, result);
  }

  @Test
  public void testChildAddedWithLimit()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    writer.child("a").setValueAsync(1);
    writer.child("b").setValueAsync("b");
    final Map<String, Object> deepObject = MapBuilder.of(
        "deep", "path",
        "of", MapBuilder.of("stuff", true));
    new WriteFuture(writer.child("c"), deepObject).timedGet();

    final Semaphore semaphore = new Semaphore(0);
    reader.limitToLast(3).addChildEventListener(new ChildEventListener() {
      int count = 0;

      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        if (count == 0) {
          assertEquals("a", snapshot.getKey());
          assertEquals(1L, snapshot.getValue());
        } else if (count == 1) {
          assertEquals("b", snapshot.getKey());
          assertEquals("b", snapshot.getValue());
        } else if (count == 2) {
          assertEquals("c", snapshot.getKey());
          TestHelpers.assertDeepEquals(deepObject, snapshot.getValue());
        } else {
          fail("Too many events");
        }
        count++;
        semaphore.release(1);
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
        // No-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    TestHelpers.waitFor(semaphore, 3);
  }

  @Test
  public void testChildChangedWithLimit()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    new WriteFuture(writer, MapBuilder.of("a", "something", "b", "we'll", "c", "overwrite"))
            .timedGet();
    final Map<String, Object> deepObject = MapBuilder.of(
        "deep", "path",
        "of", MapBuilder.of("stuff", true));

    final Semaphore semaphore = new Semaphore(0);
    final AtomicBoolean loaded = new AtomicBoolean(false);
    reader.limitToLast(3).addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        if (loaded.compareAndSet(false, true)) {
          semaphore.release(1);
        }
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    // Wait for the read to be initialized
    TestHelpers.waitFor(semaphore);

    reader.limitToLast(3).addChildEventListener(new ChildEventListener() {
      int count = 0;

      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        // No-op
      }

      @Override
      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
        if (count == 0) {
          assertEquals("a", snapshot.getKey());
          assertEquals(1L, snapshot.getValue());
        } else if (count == 1) {
          assertEquals("b", snapshot.getKey());
          assertEquals("b", snapshot.getValue());
        } else if (count == 2) {
          assertEquals("c", snapshot.getKey());
          TestHelpers.assertDeepEquals(deepObject, snapshot.getValue());
        } else {
          fail("Too many events");
        }
        count++;
        semaphore.release(1);
      }

      @Override
      public void onChildRemoved(DataSnapshot snapshot) {
        // No-op
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        // No-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    writer.child("a").setValueAsync(1);
    writer.child("b").setValueAsync("b");
    writer.child("c").setValueAsync(deepObject);

    TestHelpers.waitFor(semaphore, 3);
  }

  @Test
  public void testChildRemovedWithLimit()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    writer.child("a").setValueAsync(1);
    writer.child("b").setValueAsync("b");
    final Map<String, Object> deepObject = MapBuilder.of(
        "deep", "path",
        "of", MapBuilder.of("stuff", true));
    new WriteFuture(writer.child("c"), deepObject).timedGet();

    final Semaphore semaphore = new Semaphore(0);
    final AtomicBoolean loaded = new AtomicBoolean(false);
    reader.limitToLast(3).addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        if (loaded.compareAndSet(false, true)) {
          semaphore.release(1);
        }
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    // Wait for the read to be initialized
    TestHelpers.waitFor(semaphore);

    reader.limitToLast(3).addChildEventListener(new ChildEventListener() {
      int count = 0;

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
        if (count == 0) {
          assertEquals("a", snapshot.getKey());
        } else if (count == 1) {
          assertEquals("b", snapshot.getKey());
        } else if (count == 2) {
          assertEquals("c", snapshot.getKey());
        } else {
          fail("Too many events");
        }
        count++;
        semaphore.release(1);
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        // No-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    writer.child("a").removeValueAsync();
    writer.child("b").removeValueAsync();
    writer.child("c").removeValueAsync();

    TestHelpers.waitFor(semaphore, 3);
  }

  @Test
  public void testChildRemovedWhenParentRemoved()
      throws InterruptedException, TestFailure, ExecutionException, TimeoutException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    writer.child("a").setValueAsync(1);
    writer.child("b").setValueAsync("b");
    final Map<String, Object> deepObject = MapBuilder.of(
        "deep", "path",
        "of", MapBuilder.of("stuff", true));
    new WriteFuture(writer.child("c"), deepObject).timedGet();

    final Semaphore semaphore = new Semaphore(0);
    final AtomicBoolean loaded = new AtomicBoolean(false);
    reader.limitToLast(3).addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        if (loaded.compareAndSet(false, true)) {
          semaphore.release(1);
        }
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    // Wait for the read to be initialized
    TestHelpers.waitFor(semaphore);

    reader.limitToLast(3).addChildEventListener(new ChildEventListener() {
      int count = 0;

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
        if (count == 0) {
          assertEquals("a", snapshot.getKey());
        } else if (count == 1) {
          assertEquals("b", snapshot.getKey());
        } else if (count == 2) {
          assertEquals("c", snapshot.getKey());
        } else {
          fail("Too many events");
        }
        count++;
        semaphore.release(1);
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        // No-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    writer.removeValueAsync();

    TestHelpers.waitFor(semaphore, 3);
  }

  @Test
  public void testChildRemovedWhenParentSetToScalar()
      throws InterruptedException, TestFailure, ExecutionException, TimeoutException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    writer.child("a").setValueAsync(1);
    writer.child("b").setValueAsync("b");
    final Map<String, Object> deepObject = MapBuilder.of(
        "deep", "path",
        "of", MapBuilder.of("stuff", true));
    new WriteFuture(writer.child("c"), deepObject).timedGet();

    final Semaphore semaphore = new Semaphore(0);
    final AtomicBoolean loaded = new AtomicBoolean(false);
    reader.limitToLast(3).addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        if (loaded.compareAndSet(false, true)) {
          semaphore.release(1);
        }
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    // Wait for the read to be initialized
    TestHelpers.waitFor(semaphore);

    reader.limitToLast(3).addChildEventListener(new ChildEventListener() {
      int count = 0;

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
        if (count == 0) {
          assertEquals("a", snapshot.getKey());
        } else if (count == 1) {
          assertEquals("b", snapshot.getKey());
        } else if (count == 2) {
          assertEquals("c", snapshot.getKey());
        } else {
          fail("Too many events");
        }
        count++;
        semaphore.release(1);
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        // No-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    writer.setValueAsync("scalar");

    TestHelpers.waitFor(semaphore, 3);
  }

  @Test
  public void testMultipleQueries()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    Map<String, Object> toSet = new MapBuilder().put("a", 1).put("b", 2).put("c", 3)
        .put("d", 4).build();
    new WriteFuture(writer, toSet).timedGet();

    TestHelpers.getSnap(reader);

    final Semaphore semaphore = new Semaphore(0);
    final AtomicInteger queryAddedCount = new AtomicInteger(0);
    reader.startAt(null, "d").addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        queryAddedCount.incrementAndGet();
        semaphore.release(1);
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
        // No-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    final AtomicInteger defaultAddedCount = new AtomicInteger(0);
    reader.addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        defaultAddedCount.incrementAndGet();
        semaphore.release(1);
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
        // No-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    TestHelpers.waitFor(semaphore, 5);
    assertEquals(1, queryAddedCount.get());
    assertEquals(4, defaultAddedCount.get());
  }

  @Test
  public void testStartAtEndAtQueriesWithPriorityChanges()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final List<String> addedFirst = new ArrayList<>();
    final List<String> removedFirst = new ArrayList<>();
    final List<String> addedSecond = new ArrayList<>();
    final List<String> removedSecond = new ArrayList<>();

    ref.startAt(0).endAt(10).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        addedFirst.add(snapshot.getKey());
      }

      @Override
      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
        // No-op
      }

      @Override
      public void onChildRemoved(DataSnapshot snapshot) {
        removedFirst.add(snapshot.getKey());
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        // No-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    ref.startAt(10).endAt(20).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        addedSecond.add(snapshot.getKey());
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
        removedSecond.add(snapshot.getKey());
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    ref.child("a").setValueAsync("a", 5);
    ref.child("a").setValueAsync("a", 15);
    ref.child("a").setValueAsync("a", 10);
    new WriteFuture(ref.child("a"), "a", 5).timedGet();

    assertEquals(2, addedFirst.size());
    assertEquals("a", addedFirst.get(0));
    assertEquals("a", addedFirst.get(1));

    assertEquals(1, removedFirst.size());
    assertEquals("a", removedFirst.get(0));

    assertEquals(1, addedSecond.size());
    assertEquals("a", addedSecond.get(0));

    assertEquals(1, removedSecond.size());
    assertEquals("a", removedSecond.get(0));
  }

  @Test
  public void testDivergingQueries()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    final DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final Map<String, Object> toSet = MapBuilder.of(
        "a", MapBuilder.of("b", 1L, "c", 2L),
        "e", 3L);

    new WriteFuture(writer, toSet).timedGet();

    final AtomicBoolean childCalled = new AtomicBoolean(false);
    final AtomicLong childValue = new AtomicLong(0);
    reader.child("a/b").addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        if (snapshot.getValue() != null) {
          childCalled.compareAndSet(false, true);
          childValue.compareAndSet(0L, snapshot.getValue(Long.class));
        }
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    new ReadFuture(reader.limitToLast(2), new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        if (events.size() == 1) {
          TestHelpers.assertDeepEquals(toSet, events.get(0).getSnapshot().getValue());
          try {
            writer.child("d").setValueAsync(4);
          } catch (DatabaseException e) { // ignore
            fail("Should not fail");
          }
          return false;
        } else {
          Map<String, Object> expected = MapBuilder.of("d", 4L, "e", 3L);
          TestHelpers.assertDeepEquals(expected, events.get(1).getSnapshot().getValue());
          return true;
        }
      }
    }).timedGet();
    assertTrue(childCalled.get());
    assertEquals(1L, childValue.longValue());
  }

  @Test
  public void testCacheInvalidation()
      throws InterruptedException, TestFailure, TimeoutException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    final AtomicBoolean startChecking = new AtomicBoolean(false);
    final Semaphore ready = new Semaphore(0);
    final ReadFuture future = new ReadFuture(reader.limitToLast(2), 
        new ReadFuture.CompletionCondition() {
          @Override
          public boolean isComplete(List<EventRecord> events) {
            DataSnapshot snap = events.get(events.size() - 1).getSnapshot();
            Object result = snap.getValue();
            if (startChecking.compareAndSet(false, true) && result == null) {
              ready.release(1);
              return false;
            }
            // We already initialized the location, and now the remove has
            // happened
            // so that
            // we have no more data
            return startChecking.get() && result == null;
          }
        });

    TestHelpers.waitFor(ready);
    for (int i = 0; i < 4; ++i) {
      writer.child("k" + i).setValueAsync(i);
    }

    writer.removeValueAsync();
    future.timedGet();
  }

  @Test
  public void testIntegerKeys1()
      throws InterruptedException, TestFailure, TimeoutException {
    final DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    final Semaphore done = new Semaphore(0);
    ref.setValue(
        new MapBuilder().put("1", true).put("50", true).put("550", true).put("6", true)
            .put("600", true).put("70", true).put("8", true).put("80", true).build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            ref.startAt(null, "80").addListenerForSingleValueEvent(new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                Map<String, Object> expected = MapBuilder.of(
                    "80", true, "550", true, "600", true);
                TestHelpers.assertDeepEquals(expected, snapshot.getValue());
                done.release();
              }

              @Override
              public void onCancelled(DatabaseError error) {
              }
            });
          }
        });

    TestHelpers.waitFor(done);
  }

  @Test
  public void testIntegerKeys2()
      throws InterruptedException, TestFailure, TimeoutException {
    final DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    final Semaphore done = new Semaphore(0);
    ref.setValue(
        new MapBuilder().put("1", true).put("50", true).put("550", true).put("6", true)
            .put("600", true).put("70", true).put("8", true).put("80", true).build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            ref.endAt(null, "50").addListenerForSingleValueEvent(new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                Map<String, Object> expected = new MapBuilder().put("1", true)
                    .put("6", true).put("8", true).put("50", true).build();
                TestHelpers.assertDeepEquals(expected, snapshot.getValue());
                done.release();
              }

              @Override
              public void onCancelled(DatabaseError error) {
              }
            });
          }
        });

    TestHelpers.waitFor(done);
  }

  @Test
  public void testIntegerKeys3()
      throws InterruptedException, TestFailure, TimeoutException {
    final DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    final Semaphore done = new Semaphore(0);
    ref.setValue(
        new MapBuilder().put("1", true).put("50", true).put("550", true).put("6", true)
            .put("600", true).put("70", true).put("8", true).put("80", true).build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            ref.startAt(null, "50").endAt(null, "80")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    Map<String, Object> expected = MapBuilder.of(
                        "50", true, "70", true, "80", true);
                    TestHelpers.assertDeepEquals(expected, snapshot.getValue());
                    done.release();
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {
                  }
                });
          }
        });

    TestHelpers.waitFor(done);
  }

  @Test
  public void testMoveOutsideOfWindowIntoWindow()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    final DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    Map<String, Object> initialValue = MapBuilder.of(
        "a", MapBuilder.of(".priority", 1L, ".value", "a"),
        "b", MapBuilder.of(".priority", 2L, ".value", "b"),
        "c", MapBuilder.of(".priority", 3L, ".value", "c"));
    new WriteFuture(ref, initialValue).timedGet();
    final Query query = ref.limitToLast(2);
    final Semaphore ready = new Semaphore(0);
    final AtomicBoolean loaded = new AtomicBoolean(false);

    query.addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        if (loaded.compareAndSet(false, true)) {
          assertEquals(2, snapshot.getChildrenCount());
          assertTrue(snapshot.hasChild("b"));
          assertTrue(snapshot.hasChild("c"));
          ready.release(1);
        }
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    TestHelpers.waitFor(ready);

    ref.child("a").setPriority(4L, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        ready.release(1);
      }
    });

    TestHelpers.waitFor(ready);
  }

  @Test
  public void testEmptyLimitWithBadHash() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ref.getRepo().setHijackHash(true);

    DataSnapshot snap = TestHelpers.getSnap(ref.limitToLast(1));
    assertNull(snap.getValue());

    ref.getRepo().setHijackHash(false);
  }

  @Test
  public void testAddingQueries()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    final DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    new WriteFuture(ref.child("0"), "test1").timedGet();

    ChildEventListener childEventListener = new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot dataSnapshot, String s) {
      }

      @Override
      public void onChildChanged(DataSnapshot dataSnapshot, String s) {
      }

      @Override
      public void onChildRemoved(DataSnapshot dataSnapshot) {
      }

      @Override
      public void onChildMoved(DataSnapshot dataSnapshot, String s) {
      }

      @Override
      public void onCancelled(DatabaseError firebaseError) {
      }
    };
    ref.startAt("a").endAt("b").addChildEventListener(childEventListener);

    DataSnapshot snapshot = new ReadFuture(ref).timedGet().get(0).getSnapshot();
    assertEquals(ImmutableList.of("test1"), snapshot.getValue());
  }

  @Test
  public void testEqualToQuery() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ValueExpectationHelper expectations = new ValueExpectationHelper();
    expectations.add(ref.equalTo(1), MapBuilder.of("a", "vala"));
    expectations.add(ref.equalTo(2), MapBuilder.of("b", "valb"));
    expectations.add(ref.equalTo("abc"), MapBuilder.of("z", "valz"));
    expectations.add(ref.equalTo(2, "no_key"), null);
    expectations.add(ref.equalTo(2, "b"), MapBuilder.of("b", "valb"));

    ref.child("a").setValueAsync("vala", 1);
    ref.child("b").setValueAsync("valb", 2);
    ref.child("c").setValueAsync("valc", 3);
    ref.child("z").setValueAsync("valz", "abc");

    expectations.waitForEvents();
  }

  @Test
  public void testRemoveListenerOnDefaultQuery()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    new WriteFuture(ref.child("a"), "foo", 100).timedGet();

    final Semaphore semaphore = new Semaphore(0);
    final DataSnapshot[] snapshotHolder = new DataSnapshot[1];

    ValueEventListener listener = ref.startAt(99).addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        snapshotHolder[0] = snapshot;
        semaphore.release();
      }

      @Override
      public void onCancelled(DatabaseError error) {
        fail("Unexpected error: " + error);
      }
    });

    TestHelpers.waitFor(semaphore);
    Map<String, Object> expected = MapBuilder.of("a", "foo");
    TestHelpers.assertDeepEquals(expected, snapshotHolder[0].getValue());

    ref.removeEventListener(listener);

    new WriteFuture(ref.child("a"), "bar", 100).timedGet();
    // the listener is removed the value should have not changed
    TestHelpers.assertDeepEquals(expected, snapshotHolder[0].getValue());
  }

  @Test
  public void testFallbackForOrderBy() throws InterruptedException {
    final DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    final Semaphore done = new Semaphore(0);

    Map<String, Object> initial = MapBuilder.of(
        "a", MapBuilder.of("foo", 3),
        "b", MapBuilder.of("foo", 1),
        "c", MapBuilder.of("foo", 2));

    ref.setValue(initial, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        done.release();
      }
    });

    TestHelpers.waitFor(done);

    final List<String> children = new ArrayList<>();
    ref.orderByChild("foo").addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        children.add(snapshot.getKey());
        if (children.size() == 3) {
          done.release();
        }
      }

      @Override
      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
      }

      @Override
      public void onChildRemoved(DataSnapshot snapshot) {
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    TestHelpers.waitFor(done);
    TestHelpers.assertDeepEquals(ImmutableList.of("b", "c", "a"), children);
  }

  @Test
  public void testSnapshotChildrenOrdering()
      throws ExecutionException, TimeoutException, TestFailure, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    final Semaphore semaphore = new Semaphore(0);

    final Map<String, Object> list = MapBuilder.of(
        "a", MapBuilder.of(
            "thisvaluefirst", MapBuilder.of(".value", true, ".priority", 1),
            "name", MapBuilder.of(".value", "Michael", ".priority", 2),
            "thisvaluelast", MapBuilder.of(".value", true, ".priority", 3)),
        "b", MapBuilder.of(
            "thisvaluefirst", new MapBuilder().put(".value", true).put(".priority", null).build(),
            "name", MapBuilder.of(".value", "Rob", ".priority", 2),
            "thisvaluelast", MapBuilder.of(".value", true, ".priority", 3)),
        "c", MapBuilder.of(
            "thisvaluefirst", MapBuilder.of(".value", true, ".priority", 1),
            "name", MapBuilder.of(".value", "Jonny", ".priority", 2),
            "thisvaluelast", MapBuilder.of(".value", true, ".priority", "somestring")));

    new WriteFuture(writer, list).timedGet();

    reader.orderByChild("name").addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        List<String> expectedKeys = ImmutableList.of("thisvaluefirst", "name", "thisvaluelast");

        // Validate that snap.child() resets order to default for child
        // snaps
        List<String> orderedKeys = new ArrayList<>();
        for (DataSnapshot childSnap : snapshot.child("b").getChildren()) {
          orderedKeys.add(childSnap.getKey());
        }
        assertEquals(expectedKeys, orderedKeys);

        // Validate that snap.forEach() resets ordering to default for
        // child snaps
        List<Object> orderedNames = new ArrayList<>();
        for (DataSnapshot childSnap : snapshot.getChildren()) {
          orderedNames.add(childSnap.child("name").getValue());
          orderedKeys.clear();
          for (DataSnapshot grandchildSnap : childSnap.getChildren()) {
            orderedKeys.add(grandchildSnap.getKey());
          }
          assertEquals(expectedKeys, orderedKeys);
        }

        assertEquals(ImmutableList.of("Jonny", "Michael", "Rob"), orderedNames);
        semaphore.release();
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });
    TestHelpers.waitFor(semaphore);
  }

  @Test
  public void testAddingListensForTheSamePathDoesNotCheckFail() throws Throwable {
    // This bug manifests itself if there's a hierarchy of query listener,
    // default listener and
    // one-time listener underneath. During one-time listener registration,
    // sync-tree traversal
    // stopped as soon as it found a complete server cache (this is the case
    // for not indexed query
    // view). The problem is that the same traversal was looking for a
    // ancestor default view, and
    // the early exit prevented from finding the default listener above the
    // one-time listener. Event
    // removal code path wasn't removing the listener because it stopped as
    // soon as it found the
    // default view. This left the zombie one-time listener and check failed
    // on the second attempt
    // to create a listener for the same path (asana#61028598952586).

    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    final Semaphore semaphore = new Semaphore(0);

    ValueEventListener dummyListen = new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        semaphore.release();
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    };

    ref.child("child").setValueAsync(TestHelpers.fromJsonString("{\"name\": \"John\"}"));

    ref.orderByChild("name").equalTo("John").addValueEventListener(dummyListen);
    ref.child("child").addValueEventListener(dummyListen);
    TestHelpers.waitFor(semaphore, 2);

    ref.child("child").child("favoriteToy").addListenerForSingleValueEvent(dummyListen);
    TestHelpers.waitFor(semaphore, 1);

    ref.child("child").child("favoriteToy").addListenerForSingleValueEvent(dummyListen);
    TestHelpers.waitFor(semaphore, 1);

    ref.removeEventListener(dummyListen);
    ref.child("child").removeEventListener(dummyListen);
  }
}
