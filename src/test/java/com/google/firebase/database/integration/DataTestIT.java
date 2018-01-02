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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.cedarsoftware.util.DeepEquals;
import com.google.common.collect.ImmutableList;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.EventRecord;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.TestFailure;
import com.google.firebase.database.TestHelpers;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.RepoManager;
import com.google.firebase.database.core.view.Event;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.database.future.WriteFuture;
import com.google.firebase.testing.IntegrationTestUtils;
import com.google.firebase.testing.TestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class DataTestIT {

  private static FirebaseApp masterApp;

  @BeforeClass
  public static void setUpClass() {
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
  public void testBasicInstantiation() {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    assertTrue(ref != null);
  }

  @Test
  public void testWriteData() {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    // just make sure it doesn't throw
    ref.setValueAsync(42);
    assertTrue(true);
  }

  @Test
  public void testReadAndWrite() throws InterruptedException, TimeoutException, TestFailure {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    ReadFuture future = ReadFuture.untilNonNull(ref);

    ref.setValueAsync(42);
    List<EventRecord> events = future.timedGet();
    assertEquals(42L, events.get(events.size() - 1).getSnapshot().getValue());
  }

  @Test
  public void testValueReturnsJsonForNodesWithChildren()
      throws TimeoutException, InterruptedException, TestFailure {
    Map<String, Object> expected = new HashMap<>();
    Map<String, Object> innerExpected = new HashMap<>();
    innerExpected.put("bar", 5L);
    expected.put("foo", innerExpected);

    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ReadFuture future = ReadFuture.untilNonNull(ref);

    ref.setValueAsync(expected);
    List<EventRecord> events = future.timedGet();
    EventRecord eventRecord = events.get(events.size() - 1);
    Object result = eventRecord.getSnapshot().getValue();
    TestHelpers.assertDeepEquals(expected, result);
  }

  @Test
  public void testWriteAndWaitForServerConfirmation()
      throws TimeoutException, InterruptedException, TestFailure {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    ref.setValueAsync(42);

    ReadFuture future = new ReadFuture(ref);

    EventRecord eventRecord = future.timedGet().get(0);
    assertEquals(42L, eventRecord.getSnapshot().getValue());
  }

  @Test
  public void testWriteValueReconnectRead()
      throws ExecutionException, TimeoutException, InterruptedException, TestFailure {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    WriteFuture writeFuture = new WriteFuture(writer, 42);
    writeFuture.timedGet();

    ReadFuture future = new ReadFuture(reader);

    EventRecord eventRecord = future.timedGet().get(0);
    long result = (Long) eventRecord.getSnapshot().getValue();
    assertEquals(42L, result);
    assertEquals(42, (int) eventRecord.getSnapshot().getValue(Integer.class));
  }

  @Test
  public void testMultipleWriteValuesReconnectRead()
      throws ExecutionException, TimeoutException, InterruptedException, TestFailure {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    final DatabaseReference writer = refs.get(0);
    final DatabaseReference reader = refs.get(1);

    writer.child("a").child("b").child("c").setValueAsync(1);
    writer.child("a").child("d").child("e").setValueAsync(2);
    writer.child("a").child("d").child("f").setValueAsync(3);
    WriteFuture writeFuture = new WriteFuture(writer.child("g"), 4);
    writeFuture.timedGet();

    Map<String, Object> expected = new MapBuilder()
        .put("a",
            new MapBuilder().put("b", new MapBuilder().put("c", 1L).build())
                .put("d", new MapBuilder().put("e", 2L).put("f", 3L).build()).build())
        .put("g", 4L).build();
    ReadFuture readFuture = new ReadFuture(reader);

    GenericTypeIndicator<Map<String, Object>> t = new GenericTypeIndicator<Map<String, Object>>() {
    };
    Map<String, Object> result = readFuture.timedGet().get(0).getSnapshot().getValue(t);
    TestHelpers.assertDeepEquals(expected, result);
  }

  @Test
  public void testWriteLeafNodeOverwriteParentNodeWaitForEvents() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    EventHelper helper = new EventHelper().addValueExpectation(ref.child("a/aa"))
        .addChildExpectation(ref.child("a"), Event.EventType.CHILD_ADDED, "aa")
        .addValueExpectation(ref.child("a")).addValueExpectation(ref.child("a/aa"))
        .addChildExpectation(ref.child("a"), Event.EventType.CHILD_CHANGED, "aa")
        .addValueExpectation(ref.child("a")).startListening(true);

    ref.child("a/aa").setValueAsync(1);
    ref.child("a").setValueAsync(new MapBuilder().put("aa", 2).build());

    assertTrue(helper.waitForEvents());
    helper.cleanup();
  }

  @Test
  public void testWriteLeafNodeOverwriteAtParentMultipleTimesWaitForExpectedEvents()
      throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final AtomicInteger bbCount = new AtomicInteger(0);
    final ValueEventListener listener = ref.child("a/bb")
        .addValueEventListener(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            assertNull(snapshot.getValue());
            assertEquals(1, bbCount.incrementAndGet());
          }

          @Override
          public void onCancelled(DatabaseError error) {
          }
        });

    final EventHelper helper = new EventHelper().addValueExpectation(ref.child("a/aa"))
        .addChildExpectation(ref.child("a"), Event.EventType.CHILD_ADDED, "aa")
        .addValueExpectation(ref.child("a")).addValueExpectation(ref.child("a/aa"))
        .addChildExpectation(ref.child("a"), Event.EventType.CHILD_CHANGED, "aa")
        .addValueExpectation(ref.child("a")).addValueExpectation(ref.child("a/aa"))
        .addChildExpectation(ref.child("a"), Event.EventType.CHILD_CHANGED, "aa")
        .addValueExpectation(ref.child("a")).startListening(true);

    ref.child("a/aa").setValueAsync(1);
    ref.child("a").setValueAsync(MapBuilder.of("aa", 2));
    ref.child("a").setValueAsync(MapBuilder.of("aa", 3));
    ref.child("a").setValueAsync(MapBuilder.of("aa", 3));

    assertTrue(helper.waitForEvents());
    helper.cleanup();
    assertEquals(1, bbCount.get());
    ref.child("a/bb").removeEventListener(listener);
  }

  @Test
  public void testWriteParentNodeOverwriteAtLeafNodeWaitForEvents() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    EventHelper helper = new EventHelper().addValueExpectation(ref.child("a/aa"))
        .addChildExpectation(ref.child("a"), Event.EventType.CHILD_ADDED, "aa")
        .addValueExpectation(ref.child("a")).addValueExpectation(ref.child("a/aa"))
        .addChildExpectation(ref.child("a"), Event.EventType.CHILD_CHANGED, "aa")
        .addValueExpectation(ref.child("a")).startListening(true);

    ref.child("a").setValueAsync(new MapBuilder().put("aa", 2).build());
    ref.child("a/aa").setValueAsync(1);

    assertTrue(helper.waitForEvents());
    helper.cleanup();
  }

  @Test
  @Ignore
  public void testWriteLeafNodeRemoveParentWaitForEvents()
      throws InterruptedException, TimeoutException, TestFailure, ExecutionException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    EventHelper writeHelper = new EventHelper().addValueExpectation(writer.child("a/aa"))
        .addChildExpectation(writer.child("a"), Event.EventType.CHILD_ADDED, "aa")
        .addValueExpectation(writer.child("a"))
        .addChildExpectation(writer, Event.EventType.CHILD_ADDED, "a").addValueExpectation(writer)
        .startListening(true);

    WriteFuture w = new WriteFuture(writer.child("a/aa"), 42);
    assertTrue(writeHelper.waitForEvents());

    w.timedGet();
    EventHelper readHelper = new EventHelper().addValueExpectation(reader.child("a/aa"))
        .addChildExpectation(reader.child("a"), Event.EventType.CHILD_ADDED, "aa")
        .addValueExpectation(reader.child("a"))
        .addChildExpectation(reader, Event.EventType.CHILD_ADDED, "a").addValueExpectation(reader)
        .startListening();

    assertTrue(readHelper.waitForEvents());

    readHelper.addValueExpectation(reader.child("a/aa"))
        .addChildExpectation(reader.child("a"), Event.EventType.CHILD_REMOVED, "aa")
        .addValueExpectation(reader.child("a"))
        .addChildExpectation(reader, Event.EventType.CHILD_REMOVED, "a").addValueExpectation(reader)
        .startListening();

    writeHelper.addValueExpectation(reader.child("a/aa"))
        .addChildExpectation(reader.child("a"), Event.EventType.CHILD_REMOVED, "aa")
        .addValueExpectation(reader.child("a"))
        .addChildExpectation(reader, Event.EventType.CHILD_REMOVED, "a").addValueExpectation(reader)
        .startListening();

    writer.child("a").removeValueAsync();
    assertTrue(writeHelper.waitForEvents());
    assertTrue(readHelper.waitForEvents());
    writeHelper.cleanup();
    readHelper.cleanup();

    // Make sure we actually have null there now
    assertNull(TestHelpers.getSnap(reader).getValue());
    assertNull(TestHelpers.getSnap(writer).getValue());

    ReadFuture readFuture = ReadFuture.untilNonNull(reader);

    ReadFuture writeFuture = ReadFuture.untilNonNull(writer);

    writer.child("a/aa").setValueAsync(3.1415);

    List<EventRecord> readerEvents = readFuture.timedGet();
    List<EventRecord> writerEvents = writeFuture.timedGet();

    DataSnapshot readerSnap = readerEvents.get(readerEvents.size() - 1).getSnapshot();
    readerSnap = readerSnap.child("a/aa");
    assertEquals(3.1415, readerSnap.getValue());

    DataSnapshot writerSnap = writerEvents.get(writerEvents.size() - 1).getSnapshot();
    writerSnap = writerSnap.child("a/aa");
    assertEquals(3.1415, writerSnap.getValue());
  }

  @Test
  @Ignore
  public void testWriteLeafNodeRemoveLeafNodeWaitForEvents()
      throws InterruptedException, TimeoutException, TestFailure, ExecutionException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    EventHelper writeHelper = new EventHelper().addValueExpectation(writer.child("a/aa"))
        .addChildExpectation(writer.child("a"), Event.EventType.CHILD_ADDED, "aa")
        .addValueExpectation(writer.child("a"))
        .addChildExpectation(writer, Event.EventType.CHILD_ADDED, "a").addValueExpectation(writer)
        .startListening(true);

    WriteFuture w = new WriteFuture(writer.child("a/aa"), 42);
    assertTrue(writeHelper.waitForEvents());

    w.timedGet();
    EventHelper readHelper = new EventHelper().addValueExpectation(reader.child("a/aa"))
        .addChildExpectation(reader.child("a"), Event.EventType.CHILD_ADDED, "aa")
        .addValueExpectation(reader.child("a"))
        .addChildExpectation(reader, Event.EventType.CHILD_ADDED, "a").addValueExpectation(reader)
        .startListening();

    assertTrue(readHelper.waitForEvents());

    readHelper.addValueExpectation(reader.child("a/aa"))
        .addChildExpectation(reader.child("a"), Event.EventType.CHILD_REMOVED, "aa")
        .addValueExpectation(reader.child("a"))
        .addChildExpectation(reader, Event.EventType.CHILD_REMOVED, "a").addValueExpectation(reader)
        .startListening();

    writeHelper.addValueExpectation(reader.child("a/aa"))
        .addChildExpectation(reader.child("a"), Event.EventType.CHILD_REMOVED, "aa")
        .addValueExpectation(reader.child("a"))
        .addChildExpectation(reader, Event.EventType.CHILD_REMOVED, "a").addValueExpectation(reader)
        .startListening();

    writer.child("a/aa").removeValueAsync();
    assertTrue(writeHelper.waitForEvents());
    assertTrue(readHelper.waitForEvents());
    writeHelper.cleanup();
    readHelper.cleanup();

    DataSnapshot readerSnap = TestHelpers.getSnap(reader);
    assertNull(readerSnap.getValue());

    DataSnapshot writerSnap = TestHelpers.getSnap(writer);
    assertNull(writerSnap.getValue());

    readerSnap = TestHelpers.getSnap(reader.child("a/aa"));
    assertNull(readerSnap.getValue());

    writerSnap = TestHelpers.getSnap(writer.child("a/aa"));
    assertNull(writerSnap.getValue());

    ReadFuture readFuture = ReadFuture.untilNonNull(reader);
    ReadFuture writeFuture = ReadFuture.untilNonNull(writer);

    writer.child("a/aa").setValueAsync(3.1415);

    final List<EventRecord> readerEvents = readFuture.timedGet();
    final List<EventRecord> writerEvents = writeFuture.timedGet();

    readerSnap = readerEvents.get(readerEvents.size() - 1).getSnapshot();
    readerSnap = readerSnap.child("a/aa");
    assertEquals(3.1415, readerSnap.getValue());

    writerSnap = writerEvents.get(writerEvents.size() - 1).getSnapshot();
    writerSnap = writerSnap.child("a/aa");
    assertEquals(3.1415, writerSnap.getValue());
  }

  @Test
  public void testWriteMultipleLeafNodesRemoveOneLeafNodeWaitForEvents()
      throws InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    final EventHelper writeHelper = new EventHelper().addValueExpectation(writer.child("a/aa"))
        .addChildExpectation(writer.child("a"), Event.EventType.CHILD_ADDED, "aa")
        .addValueExpectation(writer.child("a"))
        .addChildExpectation(writer, Event.EventType.CHILD_ADDED, "a").addValueExpectation(writer)
        .addChildExpectation(writer.child("a"), Event.EventType.CHILD_ADDED, "bb")
        .addValueExpectation(writer.child("a"))
        .addChildExpectation(writer, Event.EventType.CHILD_CHANGED, "a").addValueExpectation(writer)
        .startListening(true);

    final EventHelper readHelper = new EventHelper().addValueExpectation(reader.child("a/aa"))
        .addChildExpectation(reader.child("a"), Event.EventType.CHILD_ADDED, "aa")
        .addValueExpectation(reader.child("a"))
        .addChildExpectation(reader, Event.EventType.CHILD_ADDED, "a").addValueExpectation(reader)
        .addChildExpectation(reader.child("a"), Event.EventType.CHILD_ADDED, "bb")
        .addValueExpectation(reader.child("a"))
        .addChildExpectation(reader, Event.EventType.CHILD_CHANGED, "a").addValueExpectation(reader)
        .startListening(true);

    writer.child("a/aa").setValueAsync(42);
    writer.child("a/bb").setValueAsync(24);

    assertTrue(writeHelper.waitForEvents());
    assertTrue(readHelper.waitForEvents());

    readHelper.addValueExpectation(reader.child("a/aa"))
        .addChildExpectation(reader.child("a"), Event.EventType.CHILD_REMOVED, "aa")
        .addValueExpectation(reader.child("a"))
        .addChildExpectation(reader, Event.EventType.CHILD_CHANGED, "a").addValueExpectation(reader)
        .startListening();

    writeHelper.addValueExpectation(writer.child("a/aa"))
        .addChildExpectation(writer.child("a"), Event.EventType.CHILD_REMOVED, "aa")
        .addValueExpectation(writer.child("a"))
        .addChildExpectation(writer, Event.EventType.CHILD_CHANGED, "a").addValueExpectation(writer)
        .startListening();

    writer.child("a/aa").removeValueAsync();
    assertTrue(writeHelper.waitForEvents());
    assertTrue(readHelper.waitForEvents());

    DataSnapshot readerSnap = TestHelpers.getSnap(reader);
    DataSnapshot writerSnap = TestHelpers.getSnap(writer);
    Map<String, Object> expected = new MapBuilder()
        .put("a", new MapBuilder().put("bb", 24L).build()).build();
    TestHelpers.assertDeepEquals(expected, readerSnap.getValue());
    TestHelpers.assertDeepEquals(expected, writerSnap.getValue());

    readHelper.cleanup();
    writeHelper.cleanup();
  }

  @Test
  public void testVerifyNodesStartingWithPeriod() {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    try {
      ref.child(".foo");
      fail("Should fail");
    } catch (DatabaseException e) {
      // No-op
    }

    try {
      ref.child("foo/.foo");
      fail("Should fail");
    } catch (DatabaseException e) {
      // No-op
    }
  }

  // NOTE: skipping test re: writing .keys and .length. Those features will
  // require a new client
  // anyways

  @Test
  public void testNumericKeysGetTurnedIntoArrays() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    ref.child("0").setValueAsync("alpha");
    ref.child("1").setValueAsync("bravo");
    ref.child("2").setValueAsync("charlie");
    ref.child("3").setValueAsync("delta");
    ref.child("4").setValueAsync("echo");

    DataSnapshot snap = TestHelpers.getSnap(ref);
    List<Object> expected = ImmutableList.of((Object) "alpha", "bravo", "charlie",
        "delta", "echo");
    TestHelpers.assertDeepEquals(expected, snap.getValue());
  }

  @Test
  public void testWriteFullJsonObjectsWithSetAndGetThemBack()
      throws TimeoutException, InterruptedException, TestFailure {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    Map<String, Object> expected = new MapBuilder()
        .put("a", new MapBuilder().put("aa", 5L).put("ab", 3L).build())
        .put("b",
            new MapBuilder().put("ba", "hey there!")
                .put("bb", MapBuilder.of("bba", false)).build())
        .put("c", ImmutableList.of(0L, new MapBuilder().put("c_1", 4L).build(), "hey", true, false,
            "dude"))
        .build();

    ReadFuture readFuture = ReadFuture.untilNonNull(ref);

    ref.setValueAsync(expected);
    List<EventRecord> events = readFuture.timedGet();
    Object result = events.get(events.size() - 1).getSnapshot().getValue();
    TestHelpers.assertDeepEquals(expected, result);
  }

  // NOTE: skipping test for value in callback. DataSnapshot is same instance in
  // callback as after
  // future is complete

  // NOTE: skipping test for passing a value to push. Not applicable.

  @Test
  public void testRemoveCallbackIsHit()
      throws ExecutionException, TimeoutException, InterruptedException, TestFailure {
    final DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    WriteFuture writeFuture = new WriteFuture(ref, 42);
    writeFuture.timedGet();

    ReadFuture readFuture = new ReadFuture(ref, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        return events.get(events.size() - 1).getSnapshot().getValue() == null;
      }
    });

    final Semaphore callbackHit = new Semaphore(0);
    ref.removeValue(new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference callbackRef) {
        assertEquals(ref, callbackRef);
        callbackHit.release(1);
      }
    });

    readFuture.timedGet();
    assertTrue(callbackHit.tryAcquire(1, TestUtils.TEST_TIMEOUT_MILLIS, MILLISECONDS));
    // We test the value in the completion condition
  }

  @Test
  public void testRemoveCallbackIsHitForAlreadyRemovedNodes() throws InterruptedException {
    final DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final Semaphore callbackHit = new Semaphore(0);
    ref.removeValue(new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference callbackRef) {
        assertEquals(ref, callbackRef);
        callbackHit.release(1);
      }
    });
    ref.removeValue(new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference callbackRef) {
        assertEquals(ref, callbackRef);
        callbackHit.release(1);
      }
    });

    assertTrue(
        callbackHit.tryAcquire(2, TestUtils.TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
  }

  @Test
  public void testUsingNumbersAsKeys()
      throws TimeoutException, InterruptedException, TestFailure {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    ref.child("3024").setValueAsync(5);
    ReadFuture future = new ReadFuture(ref);

    List<EventRecord> events = future.timedGet();
    assertFalse(events.get(0).getSnapshot().getValue() instanceof List);
  }

  @Test
  public void testOnceWithCallbackHitsServerToGetData()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 3);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    DatabaseReference reader2 = refs.get(2);

    final Semaphore semaphore = new Semaphore(0);
    reader.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        assertEquals(null, snapshot.getValue());
        semaphore.release(1);
      }

      @Override
      public void onCancelled(DatabaseError error) {
        fail("Shouldn't happen");
      }
    });

    TestHelpers.waitFor(semaphore);
    new WriteFuture(writer, 42).timedGet();

    reader2.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        assertEquals(42L, snapshot.getValue());
        semaphore.release(1);
      }

      @Override
      public void onCancelled(DatabaseError error) {
        fail("Shouldn't happen");
      }
    });

    TestHelpers.waitFor(semaphore);
  }

  // NOTE: skipping forEach abort test. Not relevant, we return an iterable that
  // can be stopped
  // any
  // time.

  @Test
  public void testSetAndThenListenForValueEvents()
      throws ExecutionException, TimeoutException, InterruptedException, TestFailure {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    new WriteFuture(ref, "cabbage").timedGet();
    EventRecord event = new ReadFuture(ref).timedGet().get(0);

    assertEquals("cabbage", event.getSnapshot().getValue());
  }

  @Test
  public void testHasChildren()
      throws TimeoutException, InterruptedException, TestFailure {
    final DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ref.setValueAsync(new MapBuilder()
        .put("one", 42)
        .put("two", new MapBuilder().put("a", 5).build())
        .put("three", new MapBuilder().put("a", 5).put("b", 6).build()).build());
    final AtomicBoolean removedTwo = new AtomicBoolean(false);
    ReadFuture readFuture = new ReadFuture(ref, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        if (removedTwo.compareAndSet(false, true)) {
          // removedTwo did equal false, now equals true
          try {
            ref.child("two").removeValueAsync();
          } catch (DatabaseException e) {
            fail("Should not fail");
          }
        }
        return events.size() == 2;
      }
    });

    List<EventRecord> events = readFuture.timedGet();
    DataSnapshot firstSnap = events.get(0).getSnapshot();
    assertEquals(3, firstSnap.getChildrenCount());
    assertEquals(0, firstSnap.child("one").getChildrenCount());
    assertEquals(1, firstSnap.child("two").getChildrenCount());
    assertEquals(2, firstSnap.child("three").getChildrenCount());
    assertEquals(0, firstSnap.child("four").getChildrenCount());

    DataSnapshot secondSnap = events.get(1).getSnapshot();
    assertEquals(2, secondSnap.getChildrenCount());
    assertEquals(0, secondSnap.child("two").getChildrenCount());
  }

  @Test
  public void testSetNodeWithChildrenToPrimitiveValueAndBack()
      throws ExecutionException, TimeoutException, InterruptedException, TestFailure {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference reader = refs.get(0);
    final DatabaseReference writer = refs.get(1);

    final Map<String, Object> json = new MapBuilder().put("a", 5L).put("b", 6L).build();
    final long primitive = 76L;

    new WriteFuture(writer, json).timedGet();

    final AtomicBoolean sawJson = new AtomicBoolean(false);
    final AtomicBoolean sawPrimitive = new AtomicBoolean(false);

    ReadFuture readFuture = new ReadFuture(reader, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        if (sawJson.compareAndSet(false, true)) {
          try {
            writer.setValueAsync(primitive);
          } catch (DatabaseException e) {
            fail("Shouldn't happen: " + e.toString());
          }
        } else {
          // Saw the json already
          if (sawPrimitive.compareAndSet(false, true)) {
            try {
              writer.setValueAsync(json);
            } catch (DatabaseException e) {
              fail("Shouldn't happen: " + e.toString());
            }
          }
        }
        return events.size() == 3;
      }
    });

    List<EventRecord> events = readFuture.timedGet();
    DataSnapshot readSnap = events.get(0).getSnapshot();
    assertTrue(readSnap.hasChildren());
    TestHelpers.assertDeepEquals(json, readSnap.getValue());

    readSnap = events.get(1).getSnapshot();
    assertFalse(readSnap.hasChildren());
    assertEquals(primitive, readSnap.getValue());

    readSnap = events.get(2).getSnapshot();
    assertTrue(readSnap.hasChildren());
    TestHelpers.assertDeepEquals(json, readSnap.getValue());
  }

  @Test
  public void testWriteLeafNodeRemoveItTryToAddChildToRemovedNode()
      throws ExecutionException, TimeoutException, InterruptedException, TestFailure {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    final DatabaseReference reader = refs.get(0);
    final DatabaseReference writer = refs.get(1);

    writer.setValueAsync(5);
    writer.removeValueAsync();
    new WriteFuture(writer.child("abc"), 5).timedGet();

    DataSnapshot snap = new ReadFuture(reader).timedGet().get(0).getSnapshot();

    assertEquals(5L, ((Map) snap.getValue()).get("abc"));
  }

  @Test
  public void testListenForValueThenWriteOnNodeWithExistingData()
      throws ExecutionException, TimeoutException, InterruptedException, TestFailure {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    new WriteFuture(writer, new MapBuilder().put("a", 5).put("b", 2).build()).timedGet();

    ReadFuture readFuture = new ReadFuture(reader);

    // Slight race condition. We're banking on this local set being processed
    // before the network catches up with the writer's broadcast.
    reader.child("a").setValueAsync(10);

    EventRecord event = readFuture.timedGet().get(0);
    assertEquals(10L, event.getSnapshot().child("a").getValue());
  }

  @Test
  public void testSetPriorityOnNonexistentNode() throws InterruptedException {
    final DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final Semaphore semaphore = new Semaphore(0);
    ref.setPriority(1, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference callbackRef) {
        assertEquals(ref, callbackRef);
        assertNotNull(error);
        semaphore.release(1);
      }
    });

    assertTrue(semaphore.tryAcquire(1, TestUtils.TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
  }

  @Test
  public void testSetPriority() throws InterruptedException {
    final DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ref.setValueAsync("hello!");
    final Semaphore semaphore = new Semaphore(0);
    ref.setPriority(10, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference callbackRef) {
        assertEquals(ref, callbackRef);
        assertNull(error);
        semaphore.release(1);
      }
    });

    assertTrue(semaphore.tryAcquire(1, TestUtils.TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
  }

  @Test
  public void testSetWithPriority()
      throws ExecutionException, TimeoutException, InterruptedException, TestFailure {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    final DatabaseReference ref1 = refs.get(0);
    final DatabaseReference ref2 = refs.get(1);

    ReadFuture readFuture = ReadFuture.untilNonNull(ref1);

    new WriteFuture(ref1, "hello", 5).timedGet();
    List<EventRecord> result = readFuture.timedGet();
    DataSnapshot snap = result.get(result.size() - 1).getSnapshot();
    assertEquals(5.0, snap.getPriority());
    assertEquals("hello", snap.getValue());

    result = ReadFuture.untilNonNull(ref2).timedGet();
    snap = result.get(result.size() - 1).getSnapshot();
    assertEquals(5.0, snap.getPriority());
    assertEquals("hello", snap.getValue());
  }

  // NOTE: skipped test of getPriority on snapshot. Tested above

  // NOTE: skipped test of immediately visible priority changes. Tested above

  @Test
  public void testSetOverwritesPriorityOfTopLevelNodesAndChildren()
      throws ExecutionException, TimeoutException, InterruptedException, TestFailure {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    final DatabaseReference ref1 = refs.get(0);
    final DatabaseReference ref2 = refs.get(1);

    ref1.setValueAsync(new MapBuilder().put("a", 5).build());
    ref1.setPriorityAsync(10);
    ref1.child("a").setPriorityAsync(18);
    new WriteFuture(ref1, new MapBuilder().put("a", 7).build()).timedGet();

    DataSnapshot snap = new ReadFuture(ref2).timedGet().get(0).getSnapshot();

    assertNull(snap.getPriority());
    assertNull(snap.child("a").getPriority());
  }

  @Test
  public void testSetWithPriorityOfLeafNodes()
      throws ExecutionException, TimeoutException, InterruptedException, TestFailure {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference ref1 = refs.get(0);
    DatabaseReference ref2 = refs.get(1);

    new WriteFuture(ref1, "testleaf", "992").timedGet();

    DataSnapshot snap = new ReadFuture(ref2).timedGet().get(0).getSnapshot();

    assertEquals("992", snap.getPriority());
  }

  @Test
  public void testSetPriorityOfAnObject()
      throws ExecutionException, TimeoutException, InterruptedException, TestFailure {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference ref1 = refs.get(0);
    DatabaseReference ref2 = refs.get(1);

    new WriteFuture(ref1, new MapBuilder().put("a", 5).build(), "991").timedGet();

    DataSnapshot snap = new ReadFuture(ref2).timedGet().get(0).getSnapshot();

    assertEquals("991", snap.getPriority());
  }

  // NOTE: skipping a test about following setPriority with set, it's tested
  // above

  @Test
  public void testGetPriority()
      throws TimeoutException, InterruptedException, TestFailure {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ReadFuture readFuture = ReadFuture.untilCountAfterNull(ref, 7);

    ref.setValueAsync("a");
    ref.setValueAsync("b", 5);
    ref.setValueAsync("c", "6");
    ref.setValueAsync("d", 7);
    ref.setValueAsync(new MapBuilder().put(".value", "e").put(".priority", 8).build());
    ref.setValueAsync(new MapBuilder().put(".value", "f").put(".priority", "8").build());
    ref.setValueAsync(new MapBuilder().put(".value", "g").put(".priority", null).build());

    List<EventRecord> events = readFuture.timedGet();
    assertNull(events.get(0).getSnapshot().getPriority());
    assertEquals(5.0, events.get(1).getSnapshot().getPriority());
    assertEquals("6", events.get(2).getSnapshot().getPriority());
    assertEquals(7.0, events.get(3).getSnapshot().getPriority());
    assertEquals(8.0, events.get(4).getSnapshot().getPriority());
    assertEquals("8", events.get(5).getSnapshot().getPriority());
    assertNull(events.get(6).getSnapshot().getPriority());
  }

  @Test
  public void testNormalizeDifferentIntegerAndDoubleValues()
      throws DatabaseException, InterruptedException, TimeoutException, TestFailure {
    final long intMaxPlusOne = 2147483648L;

    DatabaseReference node = IntegrationTestUtils.getRandomNode(masterApp);
    Object[] writtenValues = {
        intMaxPlusOne,
        (double) intMaxPlusOne,
        -intMaxPlusOne,
        (double) -intMaxPlusOne,
        Integer.MAX_VALUE,
        0L,
        0.0,
        -0.0f,
        0
    };

    Object[] readValues = {intMaxPlusOne, -intMaxPlusOne, (long) Integer.MAX_VALUE, 0L};
    ReadFuture readFuture = ReadFuture.untilCountAfterNull(node, readValues.length);
    for (Object value : writtenValues) {
      node.setValueAsync(value);
    }

    List<EventRecord> events = readFuture.timedGet();
    for (int i = 0; i < readValues.length; ++i) {
      assertEquals(readValues[i], events.get(i).getSnapshot().getValue());
    }
  }

  @Test
  public void testExportFormatIncludesPriorities()
      throws TimeoutException, InterruptedException, TestFailure {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    Map<String, Object> expected = new MapBuilder().put("foo",
        new MapBuilder()
            .put("bar", new MapBuilder().put(".priority", 7.0).put(".value", 5L).build())
            .put(".priority", "hi").build())
        .build();
    ReadFuture readFuture = ReadFuture.untilNonNull(ref);
    ref.setValueAsync(expected);
    DataSnapshot snap = readFuture.timedGet().get(0).getSnapshot();
    Object result = snap.getValue(true);
    TestHelpers.assertDeepEquals(expected, result);
  }

  @Test
  public void testPriorityIsOverwrittenByServerPriority()
      throws TimeoutException, InterruptedException, TestFailure {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference ref1 = refs.get(0);
    final DatabaseReference ref2 = refs.get(1);

    ReadFuture readFuture = ReadFuture.untilCountAfterNull(ref1, 2);

    ref1.setValueAsync("hi", 100);

    new ReadFuture(ref2, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        DataSnapshot snap = events.get(events.size() - 1).getSnapshot();
        Object priority = snap.getPriority();
        if (priority != null && priority.equals(100.0)) {
          try {
            ref2.setValueAsync("whatever");
          } catch (DatabaseException e) {
            fail("Shouldn't happen: " + e.toString());
          }
          return true;
        }
        return false;
      }
    }).timedGet();

    List<EventRecord> events = readFuture.timedGet();
    assertEquals(100.0, events.get(0).getSnapshot().getPriority());
    assertNull(events.get(1).getSnapshot().getPriority());
  }

  @Test
  public void testLargeNumericPriorities()
      throws TestFailure, TimeoutException, InterruptedException, ExecutionException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference ref1 = refs.get(0);
    DatabaseReference ref2 = refs.get(1);

    double priority = 1356721306842.0;

    new WriteFuture(ref1, 5, priority).timedGet();
    DataSnapshot snap = new ReadFuture(ref2).timedGet().get(0).getSnapshot();
    assertEquals(priority, snap.getPriority());
  }

  @Test
  public void testUrlEncodingAndDecoding()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {

    DatabaseReference ref = FirebaseDatabase.getInstance(masterApp)
        .getReference("/a%b&c@d/space: /non-ascii:ø");
    String result = ref.toString();
    String expected = IntegrationTestUtils.getDatabaseUrl()
        + "/a%25b%26c%40d/space%3A%20/non-ascii%3A%C3%B8";
    assertEquals(expected, result);

    String child = "" + new Random().nextInt(100000000);
    new WriteFuture(ref.child(child), "testdata").timedGet();
    DataSnapshot snap = TestHelpers.getSnap(ref.child(child));
    assertEquals("testdata", snap.getValue());
  }

  @Test
  public void testNameForRootAndNonRootLocations() throws DatabaseException {
    DatabaseReference ref = FirebaseDatabase.getInstance(masterApp).getReference();
    assertNull(ref.getKey());
    assertEquals("a", ref.child("a").getKey());
    assertEquals("c", ref.child("b/c").getKey());
  }

  @Test
  public void testNameAndRefForSnapshots()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {

    DatabaseReference ref = FirebaseDatabase.getInstance(masterApp).getReference();
    // Clear any data there
    new WriteFuture(ref, MapBuilder.of("foo", 10)).timedGet();

    DataSnapshot snap = TestHelpers.getSnap(ref);
    assertNull(snap.getKey());
    assertEquals(ref.toString(), snap.getRef().toString());
    DataSnapshot childSnap = snap.child("a");
    assertEquals("a", childSnap.getKey());
    assertEquals(ref.child("a").toString(), childSnap.getRef().toString());
    childSnap = childSnap.child("b/c");
    assertEquals("c", childSnap.getKey());
    assertEquals(ref.child("a/b/c").toString(), childSnap.getRef().toString());
  }

  @Test
  public void testParentForRootAndNonRootLocations() throws DatabaseException {
    DatabaseReference ref = FirebaseDatabase.getInstance(masterApp).getReference();
    assertNull(ref.getParent());
    DatabaseReference child = ref.child("a");
    assertEquals(ref, child.getParent());
    child = ref.child("a/b/c");
    assertEquals(ref, child.getParent().getParent().getParent());
  }

  @Test
  public void testRootForRootAndNonRootLocations() throws DatabaseException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    ref = ref.getRoot();
    assertEquals(IntegrationTestUtils.getDatabaseUrl(), ref.toString());
    ref = ref.getRoot(); // Should be a no-op
    assertEquals(IntegrationTestUtils.getDatabaseUrl(), ref.toString());
  }

  // NOTE: skip test about child accepting numbers. Not applicable in a
  // type-safe language

  // NOTE: skip test on numeric keys. We throw an exception if they aren't
  // strings

  @Test
  public void testSetChildAndListenAtTheRoot()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference ref1 = refs.get(0);
    DatabaseReference ref2 = refs.get(1);

    new WriteFuture(ref1.child("foo"), "hi").timedGet();
    DataSnapshot snap = new ReadFuture(ref2).timedGet().get(0).getSnapshot();
    Map<String, Object> expected = MapBuilder.of("foo", "hi");
    TestHelpers.assertDeepEquals(expected, snap.getValue());
  }

  @Test
  public void testInvalidPaths() throws InterruptedException {
    List<String> badPaths = ImmutableList.of(".test", "test.", "fo$o", "[what", "ever]", "ha#sh");
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    DatabaseReference root = FirebaseDatabase.getInstance(masterApp).getReference();
    DataSnapshot snap = TestHelpers.getSnap(ref);

    for (String path : badPaths) {
      try {
        ref.child(path);
        fail("Should not be a valid path: " + path);
      } catch (DatabaseException e) {
        // No-op, expected
      }

      try {
        root.child(path);
        fail("Should not be a valid path: " + path);
      } catch (DatabaseException e) {
        // No-op, expected
      }

      try {
        root.child(IntegrationTestUtils.getDatabaseUrl() + "/tests/" + path);
        fail("Should not be a valid path: " + path);
      } catch (DatabaseException e) {
        // No-op, expected
      }

      try {
        snap.child(path);
        fail("Should not be a valid path: " + path);
      } catch (DatabaseException e) {
        // No-op, expected
      }

      try {
        snap.hasChild(path);
        fail("Should not be a valid path: " + path);
      } catch (DatabaseException e) {
        // No-op, expected
      }
    }
  }

  @Test
  public void testInvalidKeys() throws DatabaseException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    List<String> badKeys = ImmutableList.of(".test", "test.", "fo$o", "[what", "ever]", "ha#sh",
        "/thing", "thi/ing", "thing/", "");

    List<Object> badObjects = new ArrayList<>();
    for (String key : badKeys) {
      badObjects.add(MapBuilder.of(key, "test"));
      badObjects.add(MapBuilder.of("deeper", MapBuilder.of(key, "test")));
    }

    // Skipping 'push' portion, that api doesn't exist in Java client

    for (Object badObject : badObjects) {
      try {
        ref.setValueAsync(badObject);
        fail("Should not be a valid object: " + badObject);
      } catch (DatabaseException e) {
        // No-op, expected
      }

      try {
        ref.onDisconnect().setValueAsync(badObject);
        fail("Should not be a valid object: " + badObject);
      } catch (DatabaseException e) {
        // No-op, expected
      }

      // TODO: Skipping transaction portion for now
    }
  }

  @Test
  public void testInvalidUpdates() throws DatabaseException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    List<Map<String, Object>> badUpdates = ImmutableList.of(
        new MapBuilder().put("/", "t").put("a", "t").build(),
        new MapBuilder().put("a", "t").put("a/b", "t").build(),
        new MapBuilder().put("/a", "t").put("a/b", "t").build(),
        new MapBuilder().put("/a/b", "t").put("a", "t").build(),
        new MapBuilder().put("/a/b", "t").put("/a/b/.priority", 1.0).build(),
        new MapBuilder().put("/a/b/.sv", "timestamp").build(),
        new MapBuilder().put("/a/b/.value", "t").build(),
        new MapBuilder().put("/a/b/.priority", MapBuilder.of("x", "y")).build());
    for (Map<String, Object> badUpdate : badUpdates) {
      try {
        ref.updateChildrenAsync(badUpdate);
        fail("Should not be a valid update: " + badUpdate);
      } catch (DatabaseException e) {
        // No-op, expected
      }

      try {
        ref.onDisconnect().updateChildrenAsync(badUpdate);
        fail("Should not be a valid object: " + badUpdate);
      } catch (DatabaseException e) {
        // No-op, expected
      }
    }
  }

  @Test
  public void testAsciiControlCharacters() throws DatabaseException {
    DatabaseReference node = IntegrationTestUtils.getRandomNode(masterApp);
    // Test all controls characters PLUS 0x7F (127).
    for (int i = 0; i <= 32; i++) {
      String ch = new String(Character.toChars(i < 32 ? i : 127));
      Map<String, Object> obj = TestHelpers.buildObjFromPath(new Path(ch), "test_value");
      try {
        node.setValueAsync(obj);
        fail("Ascii control character should not be allowed in path.");
      } catch (DatabaseException e) {
        // expected
      }
    }
  }

  @Test
  public void invalidDoubleValues()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference node = IntegrationTestUtils.getRandomNode(masterApp);
    Object[] invalidValues =
        new Object[] {
            Double.NEGATIVE_INFINITY,
            Double.POSITIVE_INFINITY,
            Double.NaN,
            Float.NEGATIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            Float.NaN
        };
    for (Object invalidValue : invalidValues) {
      try {
        node.setValueAsync(invalidValue);
        fail("NaN or Inf are not allowed as values.");
      } catch (DatabaseException expected) {
        assertEquals("Invalid value: Value cannot be NaN, Inf or -Inf.", expected.getMessage());
      }
    }
  }

  @Test
  @Ignore
  // TODO: Stop ignoring this test once JSON parsing has been fixed.
  public void testPathKeyLengthLimits() throws TestFailure, TimeoutException, InterruptedException {
    final int maxPathLengthBytes = 768;
    final int maxPathDepth = 32;
    final String fire = new String(Character.toChars(128293));
    final String base = new String(Character.toChars(26594));

    List<String> goodKeys = ImmutableList.of(
        TestHelpers.repeatedString("k", maxPathLengthBytes - 1),
        TestHelpers.repeatedString(fire, maxPathLengthBytes / 4 - 1),
        TestHelpers.repeatedString(base, maxPathLengthBytes / 3 - 1),
        TestHelpers.repeatedString("key/", maxPathDepth - 1) + "key");

    class BadGroup {

      private String expectedError;
      private List<String> keys;

      private BadGroup(String expectedError, List<String> keys) {
        this.expectedError = expectedError;
        this.keys = keys;
      }
    }

    List<BadGroup> badGroups = ImmutableList.of(
        new BadGroup("key path longer than 768 bytes",
            ImmutableList.of(TestHelpers.repeatedString("k", maxPathLengthBytes),
                TestHelpers.repeatedString(fire, maxPathLengthBytes / 4),
                TestHelpers.repeatedString(base, maxPathLengthBytes / 3),
                TestHelpers.repeatedString("j", maxPathLengthBytes / 2) + '/'
                    + TestHelpers.repeatedString("k", maxPathLengthBytes / 2))),
        new BadGroup("Path specified exceeds the maximum depth",
            Collections.singletonList(TestHelpers.repeatedString("key/", maxPathDepth) + "key")));

    DatabaseReference node = FirebaseDatabase.getInstance(masterApp).getReference();

    // Ensure "good keys" work from the root.
    for (String key : goodKeys) {
      Path path = new Path(key);
      Map<String, Object> obj = TestHelpers.buildObjFromPath(path, "test_value");
      node.setValueAsync(obj);
      ReadFuture future = ReadFuture.untilNonNull(node);
      assertEquals("test_value", TestHelpers.applyPath(future.waitForLastValue(), path));

      node.child(key).setValueAsync("another_value");
      future = ReadFuture.untilNonNull(node.child(key));
      assertEquals("another_value", future.waitForLastValue());

      node.updateChildrenAsync(obj);
      future = ReadFuture.untilNonNull(node);
      assertEquals("test_value", TestHelpers.applyPath(future.waitForLastValue(), path));
    }

    // Ensure "good keys" fail when created from child node (relative paths too
    // long).
    DatabaseReference nodeChild = IntegrationTestUtils.getRandomNode(masterApp);
    for (String key : goodKeys) {
      Map<String, Object> obj = TestHelpers.buildObjFromPath(new Path(key), "test_value");
      try {
        nodeChild.setValueAsync(obj);
        fail("Too-long path for setValue should throw exception.");
      } catch (DatabaseException e) {
        // expected
      }
      try {
        nodeChild.child(key).setValueAsync("another_value");
        fail("Too-long path before setValue should throw exception.");
      } catch (DatabaseException e) {
        // expected
      }
      try {
        nodeChild.updateChildrenAsync(obj);
        fail("Too-long path for updateChildren should throw exception.");
      } catch (DatabaseException e) {
        // expected
      }
      try {
        Map<String, Object> deepUpdate = MapBuilder.of(key, "test_value");
        nodeChild.updateChildrenAsync(deepUpdate);
        fail("Too-long path in deep update for updateChildren should throw exception.");
      } catch (DatabaseException e) {
        // expected
      }
    }

    for (BadGroup badGroup : badGroups) {
      for (String key : badGroup.keys) {
        Map<String, Object> obj = TestHelpers.buildObjFromPath(new Path(key), "test_value");
        try {
          node.setValueAsync(obj);
          fail("Expected setValueAsync(bad key) to throw exception: " + key);
        } catch (DatabaseException e) {
          TestHelpers.assertContains(e.getMessage(), badGroup.expectedError);
        }
        try {
          node.child(key).setValueAsync("another_value");
          fail("Expected child(\"" + key + "\").setValueAsync() to throw exception: " + key);
        } catch (DatabaseException e) {
          TestHelpers.assertContains(e.getMessage(), badGroup.expectedError);
        }
        try {
          node.updateChildrenAsync(obj);
          fail("Expected updateChildrenAsync(bad key) to throw exception: " + key);
        } catch (DatabaseException e) {
          TestHelpers.assertContains(e.getMessage(), badGroup.expectedError);
        }
        try {
          Map<String, Object> deepUpdate = MapBuilder.of(key, "test_value");
          node.updateChildrenAsync(deepUpdate);
          fail("Expected updateChildrean(bad deep update key) to throw exception: " + key);
        } catch (DatabaseException e) {
          TestHelpers.assertContains(e.getMessage(), badGroup.expectedError);
        }
        try {
          node.onDisconnect().setValueAsync(obj);
          fail("Expected onDisconnect.setValueAsync(bad key) to throw exception: " + key);
        } catch (DatabaseException e) {
          TestHelpers.assertContains(e.getMessage(), badGroup.expectedError);
        }
        try {
          node.onDisconnect().updateChildrenAsync(obj);
          fail("Expected onDisconnect.updateChildrenAsync(bad key) to throw exception: " + key);
        } catch (DatabaseException e) {
          TestHelpers.assertContains(e.getMessage(), badGroup.expectedError);
        }
        try {
          Map<String, Object> deepUpdate = MapBuilder.of(key, "test_value");
          node.onDisconnect().updateChildrenAsync(deepUpdate);
          fail("Expected onDisconnect.updateChildrenAsync(bad deep update key) to throw exception: "
              + key);
        } catch (DatabaseException e) {
          TestHelpers.assertContains(e.getMessage(), badGroup.expectedError);
        }
      }
    }
  }

  @Test
  public void testNamespaceCaseInsensitive()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {

    String child = "" + new Random().nextInt(100000000);
    DatabaseReference ref1 = FirebaseDatabase.getInstance(masterApp).getReference(child);

    String url = "https://" + IntegrationTestUtils.getProjectId().toUpperCase() + ".firebaseio.com/"
        + child;
    DatabaseReference ref2 = FirebaseDatabase.getInstance(masterApp).getReferenceFromUrl(url);

    new WriteFuture(ref1, "testdata").timedGet();
    DataSnapshot snap = TestHelpers.getSnap(ref2);
    assertEquals("testdata", snap.getValue());
  }

  @Test
  public void testNamespacesToStringCaseInsensitiveIn() throws DatabaseException {
    DatabaseReference ref1 = FirebaseDatabase.getInstance(masterApp).getReference();
    String url = "https://" + IntegrationTestUtils.getProjectId().toUpperCase() + ".firebaseio.com";
    DatabaseReference ref2 = FirebaseDatabase.getInstance(masterApp).getReferenceFromUrl(url);

    assertEquals(ref1.toString(), ref2.toString());
  }

  // NOTE: skipping test on re-entrant remove call. Not an issue w/ work queue
  // architecture

  @Test
  public void testSetNodeWithQuotedKey()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    Map<String, Object> expected = MapBuilder.of("\"herp\"", 1234L);
    new WriteFuture(ref, expected).timedGet();
    DataSnapshot snap = TestHelpers.getSnap(ref);
    TestHelpers.assertDeepEquals(expected, snap.getValue());
  }

  @Test
  public void testSetChildWithQuote()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ReadFuture readFuture = new ReadFuture(ref);
    new WriteFuture(ref.child("\""), 1).timedGet();
    DataSnapshot snap = readFuture.timedGet().get(0).getSnapshot();
    assertEquals(1L, snap.child("\"").getValue());
  }

  @Test
  public void testEmptyChildrenGetValueEventBeforeParent() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    EventHelper helper = new EventHelper().addValueExpectation(ref.child("a/aa/aaa"))
        .addValueExpectation(ref.child("a/aa")).addValueExpectation(ref.child("a"))
        .startListening();
    ref.setValueAsync(MapBuilder.of("b", 5));

    assertTrue(helper.waitForEvents());
    helper.cleanup();
  }

  // NOTE: skipping test for recursive sets. Java does not have reentrant
  // firebase api calls

  @Test
  public void testOnAfterSetWaitsForLatestData()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference ref1 = refs.get(0);
    DatabaseReference ref2 = refs.get(1);

    new WriteFuture(ref1, 5).timedGet();
    new WriteFuture(ref2, 42).timedGet();

    DataSnapshot snap = new ReadFuture(ref1).timedGet().get(0).getSnapshot();
    assertEquals(42L, snap.getValue());
  }

  @Test
  public void testOnceWaitsForLatestDataEachTime()
      throws InterruptedException, TestFailure, ExecutionException, TimeoutException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference ref1 = refs.get(0);
    DatabaseReference ref2 = refs.get(1);

    final Semaphore semaphore = new Semaphore(0);
    ref1.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        assertNull(snapshot.getValue());
        semaphore.release(1);
      }

      @Override
      public void onCancelled(DatabaseError error) {
        fail("Should not be cancelled");
      }
    });

    TestHelpers.waitFor(semaphore);

    new WriteFuture(ref2, 5).timedGet();
    ref1.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        assertEquals(5L, snapshot.getValue());
        semaphore.release(1);
      }

      @Override
      public void onCancelled(DatabaseError error) {
        fail("Should not be cancelled");
      }
    });

    TestHelpers.waitFor(semaphore);

    new WriteFuture(ref2, 42).timedGet();

    ref1.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        assertEquals(42L, snapshot.getValue());
        semaphore.release(1);
      }

      @Override
      public void onCancelled(DatabaseError error) {
        fail("Should not be cancelled");
      }
    });

    TestHelpers.waitFor(semaphore);
  }

  @Test
  public void testMemoryFreeingOnUnlistenDoesNotCorruptData()
      throws TestFailure, TimeoutException, InterruptedException, ExecutionException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference ref1 = refs.get(0);
    DatabaseReference ref2 = refs.get(1);

    final Semaphore semaphore = new Semaphore(0);
    final AtomicBoolean hasRun = new AtomicBoolean(false);
    ValueEventListener listener = ref1.addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        if (hasRun.compareAndSet(false, true)) {
          assertNull(snapshot.getValue());
          semaphore.release(1);
        }
      }

      @Override
      public void onCancelled(DatabaseError error) {
        fail("Should not fail");
      }
    });

    TestHelpers.waitFor(semaphore);

    new WriteFuture(ref1, "test").timedGet();
    ref1.removeEventListener(listener);

    DatabaseReference other = IntegrationTestUtils.getRandomNode(masterApp);
    new WriteFuture(other, "hello").timedGet();
    ref1.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        assertEquals("test", snapshot.getValue());
        semaphore.release(1);
      }

      @Override
      public void onCancelled(DatabaseError error) {
        fail("Should not fail");
      }
    });

    TestHelpers.waitFor(semaphore);

    ref2.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        assertEquals("test", snapshot.getValue());
        semaphore.release(1);
      }

      @Override
      public void onCancelled(DatabaseError error) {
        fail("Should not fail");
      }
    });

    TestHelpers.waitFor(semaphore);
  }

  @Test
  public void testUpdateRaisesCorrectLocalEvents()
      throws InterruptedException, TestFailure, TimeoutException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final ReadFuture readFuture = ReadFuture.untilCountAfterNull(ref, 2);

    ref.setValueAsync(new MapBuilder().put("a", 1).put("b", 2).put("c", 3).put("d", 4).build());

    EventHelper helper = new EventHelper().addValueExpectation(ref.child("a"))
        .addValueExpectation(ref.child("d"))
        .addChildExpectation(ref, Event.EventType.CHILD_CHANGED, "a")
        .addChildExpectation(ref, Event.EventType.CHILD_CHANGED, "d").addValueExpectation(ref)
        .startListening(true);

    ref.updateChildrenAsync(new MapBuilder().put("a", 4).put("d", 1).build());
    helper.waitForEvents();
    List<EventRecord> events = readFuture.timedGet();
    helper.cleanup();

    Map<String, Object> expected = new MapBuilder().put("a", 4L).put("b", 2L).put("c", 3L)
        .put("d", 1L).build();
    Object result = events.get(events.size() - 1).getSnapshot().getValue();
    TestHelpers.assertDeepEquals(expected, result);
  }

  @Test
  public void testUpdateRaisesCorrectRemoteEvents()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    new WriteFuture(writer,
        new MapBuilder().put("a", 1).put("b", 2).put("c", 3).put("d", 4).build()).timedGet();

    EventHelper helper = new EventHelper().addValueExpectation(reader.child("a"))
        .addValueExpectation(reader.child("d"))
        .addChildExpectation(reader, Event.EventType.CHILD_CHANGED, "a")
        .addChildExpectation(reader, Event.EventType.CHILD_CHANGED, "d").addValueExpectation(reader)
        .startListening(true);

    writer.updateChildrenAsync(new MapBuilder().put("a", 4).put("d", 1).build());
    helper.waitForEvents();
    helper.cleanup();

    DataSnapshot snap = TestHelpers.getSnap(reader);
    Map<String, Object> expected = new MapBuilder().put("a", 4L).put("b", 2L).put("c", 3L)
        .put("d", 1L).build();
    Object result = snap.getValue();
    TestHelpers.assertDeepEquals(expected, result);
  }

  @Test
  public void testUpdateRaisesChildEventsOnNewListener() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    EventHelper helper = new EventHelper().addValueExpectation(ref.child("a"))
        .addValueExpectation(ref.child("d"))
        .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "a")
        .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "d")
        .addValueExpectation(ref.child("c")).addValueExpectation(ref.child("d"))
        .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "c")
        .addChildExpectation(ref, Event.EventType.CHILD_CHANGED, "d").startListening();

    ref.updateChildrenAsync(new MapBuilder().put("a", 11).put("d", 44).build());
    ref.updateChildrenAsync(new MapBuilder().put("c", 33).put("d", 45).build());
    helper.waitForEvents();
    helper.cleanup();
  }

  @Test
  public void testUpdateAfterSetLeafNodeWorks() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    final Semaphore semaphore = new Semaphore(0);
    final Map<String, Object> expected = new MapBuilder().put("a", 1L).put("b", 2L).build();

    ref.addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        if (DeepEquals.deepEquals(snapshot.getValue(), expected)) {
          semaphore.release();
        }
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });
    ref.setValueAsync(42);
    ref.updateChildrenAsync(expected);

    TestHelpers.waitFor(semaphore);
  }

  @Test
  public void testUpdateChangesAreStoredCorrectlyInServer()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    final DatabaseReference writer = refs.get(0);
    final DatabaseReference reader = refs.get(1);

    new WriteFuture(writer,
        new MapBuilder().put("a", 1).put("b", 2).put("c", 3).put("d", 4).build()).timedGet();

    final Semaphore semaphore = new Semaphore(0);
    writer.updateChildren(MapBuilder.of("a", 42),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            assertNull(error);
            semaphore.release(1);
          }
        });

    TestHelpers.waitFor(semaphore);

    DataSnapshot snap = TestHelpers.getSnap(reader);
    Map<String, Object> expected = new MapBuilder().put("a", 42L).put("b", 2L).put("c", 3L)
        .put("d", 4L).build();
    Object result = snap.getValue();
    TestHelpers.assertDeepEquals(expected, result);
  }

  @Test
  public void testUpdateAffectPriorityLocally()
      throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ReadFuture readFuture = ReadFuture.untilCountAfterNull(ref, 2);

    ref.setValueAsync(new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build(), "testpri");
    ref.updateChildrenAsync(MapBuilder.of("a", 4));

    List<EventRecord> events = readFuture.timedGet();
    DataSnapshot snap = events.get(0).getSnapshot();
    assertEquals("testpri", snap.getPriority());

    snap = events.get(1).getSnapshot();
    assertEquals(4L, snap.child("a").getValue());
    assertEquals("testpri", snap.getPriority());
  }

  @Test
  public void testUpdateAffectPriorityRemotely()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    new WriteFuture(writer, new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build(), "testpri")
        .timedGet();

    DataSnapshot snap = TestHelpers.getSnap(reader);
    assertEquals("testpri", snap.getPriority());

    final Semaphore semaphore = new Semaphore(0);
    writer.updateChildren(MapBuilder.of("a", 4),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            assertNull(error);
            semaphore.release(1);
          }
        });

    TestHelpers.waitFor(semaphore);
    snap = TestHelpers.getSnap(reader);
    assertEquals("testpri", snap.getPriority());
  }

  @Test
  public void testUpdateReplacesChildren() throws InterruptedException, TestFailure,
      TimeoutException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    final DatabaseReference writer = refs.get(0);
    final DatabaseReference reader = refs.get(1);

    final ReadFuture readFuture = ReadFuture.untilCountAfterNull(writer, 2);

    writer.setValueAsync(
        new MapBuilder().put("a", new MapBuilder().put("aa", 1).put("ab", 2).build()).build());
    final Semaphore semaphore = new Semaphore(0);
    Map<String, Object> expected = MapBuilder.of("a", MapBuilder.of("aa", 1L));
    writer.updateChildren(expected, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        assertNull(error);
        semaphore.release(1);
      }
    });

    TestHelpers.waitFor(semaphore);

    DataSnapshot snap = TestHelpers.getSnap(reader);
    TestHelpers.assertDeepEquals(expected, snap.getValue());

    snap = readFuture.timedGet().get(1).getSnapshot();
    TestHelpers.assertDeepEquals(expected, snap.getValue());
  }

  @Test
  public void testDeepUpdate() throws InterruptedException, TestFailure, TimeoutException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    final DatabaseReference writer = refs.get(0);
    final DatabaseReference reader = refs.get(1);

    final ReadFuture readFuture = ReadFuture.untilCount(writer, 2);

    writer.setValueAsync(
        new MapBuilder().put("a", new MapBuilder().put("aa", 1).put("ab", 2).build()).build());
    Map<String, Object> expected = new MapBuilder()
        .put("a", new MapBuilder().put("aa", 10L).put("ab", 20L).build()).build();
    Map<String, Object> update = new MapBuilder().put("a/aa", 10).put(".priority", 3.0)
        .put("a/ab", new MapBuilder().put(".priority", 2.0).put(".value", 20).build()).build();

    final Semaphore semaphore = new Semaphore(0);
    writer.updateChildren(update, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        assertNull(error);
        semaphore.release(1);
      }
    });

    TestHelpers.waitFor(semaphore);

    DataSnapshot snap = TestHelpers.getSnap(reader);
    TestHelpers.assertDeepEquals(expected, snap.getValue());
    assertEquals(3.0, snap.getPriority());

    snap = readFuture.timedGet().get(1).getSnapshot();
    TestHelpers.assertDeepEquals(expected, snap.getValue());
    assertEquals(3.0, snap.getPriority());

    snap = TestHelpers.getSnap(reader.child("a/ab"));
    assertEquals(2.0, snap.getPriority());
  }

  // NOTE: skipping test of update w/ scalar value. Disallowed by the type
  // system

  @Test
  public void testUpdateWithNoChanges() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final Semaphore semaphore = new Semaphore(0);
    ref.updateChildren(new HashMap<String, Object>(), new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        assertNull(error);
        semaphore.release(1);
      }
    });

    TestHelpers.waitFor(semaphore);
  }

  // TODO: consider implementing update stress test
  // NOTE: skipping update stress test for now

  @Test
  public void testUpdateFiresCorrectEventWhenDeletingChild()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final ReadFuture writerFuture = ReadFuture.untilCountAfterNull(writer, 2);
    new WriteFuture(writer, new MapBuilder().put("a", 12).put("b", 6).build()).timedGet();
    final Semaphore semaphore = new Semaphore(0);
    final ReadFuture readerFuture = new ReadFuture(reader, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        if (events.size() == 1) {
          semaphore.release();
        }
        return events.size() == 2;
      }
    });

    TestHelpers.waitFor(semaphore);

    writer.updateChildrenAsync(MapBuilder.of("a", null));
    DataSnapshot snap = writerFuture.timedGet().get(1).getSnapshot();

    Map<String, Object> expected = MapBuilder.of("b", 6L);
    TestHelpers.assertDeepEquals(expected, snap.getValue());

    snap = readerFuture.timedGet().get(1).getSnapshot();

    TestHelpers.assertDeepEquals(expected, snap.getValue());
  }

  @Test
  public void testUpdateFiresCorrectEventOnNewChildren()
      throws TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final Semaphore readerInitializedSemaphore = new Semaphore(0);
    final Semaphore writerInitializedSemaphore = new Semaphore(0);
    final ReadFuture writerFuture = new ReadFuture(writer, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        if (events.size() == 1) {
          writerInitializedSemaphore.release();
        }
        return events.size() == 2;
      }
    });
    final ReadFuture readerFuture = new ReadFuture(reader, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        if (events.size() == 1) {
          readerInitializedSemaphore.release();
        }
        return events.size() == 2;
      }
    });

    TestHelpers.waitFor(readerInitializedSemaphore);
    TestHelpers.waitFor(writerInitializedSemaphore);

    writer.updateChildrenAsync(MapBuilder.of("a", 42));
    DataSnapshot snap = writerFuture.timedGet().get(1).getSnapshot();

    Map<String, Object> expected = MapBuilder.of("a", 42L);
    TestHelpers.assertDeepEquals(expected, snap.getValue());

    snap = readerFuture.timedGet().get(1).getSnapshot();

    TestHelpers.assertDeepEquals(expected, snap.getValue());
  }

  @Test
  public void testUpdateFiresCorrectEventWhenAllChildrenAreDeleted()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final ReadFuture writerFuture = ReadFuture.untilCountAfterNull(writer, 2);
    new WriteFuture(writer, MapBuilder.of("a", 12)).timedGet();
    final Semaphore semaphore = new Semaphore(0);
    final ReadFuture readerFuture = new ReadFuture(reader, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        if (events.size() == 1) {
          semaphore.release();
        }
        return events.size() == 2;
      }
    });

    TestHelpers.waitFor(semaphore);

    writer.updateChildrenAsync(MapBuilder.of("a", null));
    DataSnapshot snap = writerFuture.timedGet().get(1).getSnapshot();

    assertNull(snap.getValue());

    snap = readerFuture.timedGet().get(1).getSnapshot();

    assertNull(snap.getValue());
  }

  @Test
  public void testUpdateFiresCorrectEventOnChangedChildren()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final ReadFuture writerFuture = ReadFuture.untilCountAfterNull(writer, 2);
    new WriteFuture(writer, MapBuilder.of("a", 12)).timedGet();
    final Semaphore semaphore = new Semaphore(0);
    final ReadFuture readerFuture = new ReadFuture(reader, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        if (events.size() == 1) {
          semaphore.release();
        }
        return events.size() == 2;
      }
    });

    TestHelpers.waitFor(semaphore);

    writer.updateChildrenAsync(MapBuilder.of("a", 11));
    DataSnapshot snap = writerFuture.timedGet().get(1).getSnapshot();

    Map<String, Object> expected = MapBuilder.of("a", 11L);
    TestHelpers.assertDeepEquals(expected, snap.getValue());

    snap = readerFuture.timedGet().get(1).getSnapshot();

    TestHelpers.assertDeepEquals(expected, snap.getValue());
  }

  @Test
  public void testPriorityUpdate() throws InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    final DatabaseReference writer = refs.get(0);
    final DatabaseReference reader = refs.get(1);

    Map<String, Object> writeValue = new MapBuilder().put("a", 5).put(".priority", "pri1").build();

    Map<String, Object> updateValue = new MapBuilder().put("a", 6).put(".priority", "pri2")
        .put("b", new MapBuilder().put(".priority", "pri3").put("c", 10).build()).build();

    final Semaphore semaphore = new Semaphore(0);
    writer.setValueAsync(writeValue);
    writer.updateChildren(updateValue, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        assertNull(error);
        semaphore.release(1);
      }
    });
    TestHelpers.waitFor(semaphore);

    DataSnapshot snap = TestHelpers.getSnap(reader);
    assertEquals(6L, snap.child("a").getValue());
    assertEquals("pri2", snap.getPriority());
    assertEquals("pri3", snap.child("b").getPriority());
    assertEquals(10L, snap.child("b/c").getValue());
  }

  // NOTE: skipping test for circular data structures. StackOverflowError is
  // thrown, they'll
  // see it.

  // NOTE: skipping test for creating a child name 'hasOwnProperty'

  // NOTE: skipping nesting tests. The Java api is async and doesn't do
  // reentrant callbacks

  @Test
  public void testParentDeleteShadowsChildListeners() throws InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference deleter = refs.get(1);

    String childName = writer.push().getKey();

    final AtomicBoolean initialized = new AtomicBoolean(false);
    final AtomicBoolean called = new AtomicBoolean(false);
    final Semaphore semaphore = new Semaphore(0);
    final ValueEventListener listener = deleter.child(childName)
        .addValueEventListener(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            Object val = snapshot.getValue();
            boolean inited = initialized.get();
            if (val == null && inited) {
              assertTrue(called.compareAndSet(false, true));
            } else if (!inited && val != null) {
              assertTrue(initialized.compareAndSet(false, true));
              semaphore.release(1);
            }
          }

          @Override
          public void onCancelled(DatabaseError error) {
          }
        });
    writer.child(childName).setValueAsync("foo");
    // Make sure we get the data in the listener before we delete it
    TestHelpers.waitFor(semaphore);

    deleter.removeValue(new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        semaphore.release(1);
      }
    });

    TestHelpers.waitFor(semaphore);
    assertTrue(called.get());
    deleter.child(childName).removeEventListener(listener);
  }

  @Test
  public void testParentDeleteShadowsNonDefaultChildListeners() throws InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference deleter = refs.get(1);

    String childName = writer.push().getKey();

    final AtomicBoolean queryCalled = new AtomicBoolean(false);
    final AtomicBoolean deepChildCalled = new AtomicBoolean(false);

    final ValueEventListener queryListener = deleter.child(childName).startAt(null, "b")
        .addValueEventListener(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            assertNull(snapshot.getValue());
            queryCalled.compareAndSet(false, true);
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        });

    final ValueEventListener listener = deleter.child(childName).child("a")
        .addValueEventListener(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            assertNull(snapshot.getValue());
            deepChildCalled.compareAndSet(false, true);
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        });

    writer.child(childName).setValueAsync("foo");
    final Semaphore semaphore = new Semaphore(0);
    deleter.removeValue(new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        semaphore.release(1);
      }
    });

    TestHelpers.waitFor(semaphore);
    assertTrue(queryCalled.get());
    assertTrue(deepChildCalled.get());
    deleter.child(childName).startAt(null, "b").removeEventListener(queryListener);
    deleter.child(childName).child("a").removeEventListener(listener);
  }

  @Test
  public void testServerValuesSetWithPriorityRemoteEvents()
      throws TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final ReadFuture readerFuture = new ReadFuture(reader, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        valSemaphore.release();
        Object snap = events.get(events.size() - 1).getSnapshot().getValue();
        return snap != null;
      }
    });

    // Wait for initial (null) value.
    TestHelpers.waitFor(valSemaphore, 1);

    Map<String, Object> initialValues = new MapBuilder()
        .put("a", ServerValue.TIMESTAMP)
        .put("b", new MapBuilder()
            .put(".value", ServerValue.TIMESTAMP)
            .put(".priority", ServerValue.TIMESTAMP).build())
        .build();

    writer.setValue(initialValues, ServerValue.TIMESTAMP,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release();
          }
        });
    TestHelpers.waitFor(opSemaphore);

    EventRecord readerEventRecord = readerFuture.timedGet().get(1);
    DataSnapshot snap = readerEventRecord.getSnapshot();
    assertEquals(snap.child("a").getValue().getClass(), Long.class);
    assertEquals(snap.getPriority().getClass(), Double.class);
    assertEquals(snap.getPriority(), snap.child("b").getPriority());
    assertEquals(snap.child("a").getValue(), snap.child("b").getValue());
    TestHelpers.assertTimeDelta(Long.parseLong(snap.child("a").getValue().toString()));
  }

  @Test
  public void testServerValuesSetPriorityRemoteEvents() throws InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final AtomicLong priority = new AtomicLong(0);
    reader.addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
      }

      @Override
      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
      }

      @Override
      public void onChildRemoved(DataSnapshot snapshot) {
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        priority.set(((Double) snapshot.getPriority()).longValue());
        valSemaphore.release();
      }
    });

    Map<String, Object> initialValues = new MapBuilder()
        .put("a", 1)
        .put("b", new MapBuilder()
            .put(".value", 1)
            .put(".priority", 1).build())
        .build();

    writer.setValue(initialValues, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        opSemaphore.release();
      }
    });
    TestHelpers.waitFor(opSemaphore);

    writer.child("a").setPriority(ServerValue.TIMESTAMP,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release();
          }
        });
    TestHelpers.waitFor(opSemaphore);

    TestHelpers.waitFor(valSemaphore);
    TestHelpers.assertTimeDelta(priority.get());
  }

  @Test
  @Ignore
  public void testServerValuesUpdateRemoteEvents()
      throws TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final ReadFuture readerFuture = new ReadFuture(reader, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        valSemaphore.release();
        return events.size() == 3;
      }
    });

    // Wait for initial (null) value.
    TestHelpers.waitFor(valSemaphore, 1);

    writer.child("a/b/d").setValue(1, ServerValue.TIMESTAMP,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release();
          }
        });
    TestHelpers.waitFor(opSemaphore);

    Map<String, Object> updatedValue = new MapBuilder()
        .put("b", new MapBuilder()
            .put("c", ServerValue.TIMESTAMP)
            .put("d", ServerValue.TIMESTAMP).build())
        .build();

    writer.child("a").updateChildren(updatedValue, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        assertNull(error);
        opSemaphore.release();
      }
    });
    TestHelpers.waitFor(opSemaphore);

    EventRecord readerEventRecord = readerFuture.timedGet().get(2);
    DataSnapshot snap = readerEventRecord.getSnapshot();
    assertEquals(snap.child("a/b/c").getValue().getClass(), Long.class);
    assertEquals(snap.child("a/b/d").getValue().getClass(), Long.class);
    TestHelpers.assertTimeDelta(Long.parseLong(snap.child("a/b/c").getValue().toString()));
  }

  @Test
  public void testServerValuesSetWithPriorityLocalEvents()
      throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference writer = IntegrationTestUtils.getRandomNode(masterApp);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final ReadFuture writerFuture = new ReadFuture(writer, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        valSemaphore.release();
        Object snap = events.get(events.size() - 1).getSnapshot().getValue();
        return snap != null;
      }
    });

    // Wait for initial (null) value.
    TestHelpers.waitFor(valSemaphore, 1);

    Map<String, Object> initialValues = new MapBuilder()
        .put("a", ServerValue.TIMESTAMP)
        .put("b", new MapBuilder()
            .put(".value", ServerValue.TIMESTAMP)
            .put(".priority", ServerValue.TIMESTAMP).build())
        .build();

    writer.setValue(initialValues, ServerValue.TIMESTAMP,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release();
          }
        });
    TestHelpers.waitFor(opSemaphore);

    EventRecord readerEventRecord = writerFuture.timedGet().get(1);
    DataSnapshot snap = readerEventRecord.getSnapshot();
    assertEquals(snap.child("a").getValue().getClass(), Long.class);
    assertEquals(snap.getPriority().getClass(), Double.class);
    assertEquals(snap.getPriority(), snap.child("b").getPriority());
    assertEquals(snap.child("a").getValue(), snap.child("b").getValue());
    TestHelpers.assertTimeDelta(Long.parseLong(snap.child("a").getValue().toString()));
  }

  @Test
  public void testServerValuesSetPriorityLocalEvents() throws InterruptedException {
    DatabaseReference writer = IntegrationTestUtils.getRandomNode(masterApp);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final AtomicLong priority = new AtomicLong(0);
    writer.addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
      }

      @Override
      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
      }

      @Override
      public void onChildRemoved(DataSnapshot snapshot) {
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        priority.set(((Double) snapshot.getPriority()).longValue());
        valSemaphore.release();
      }
    });

    Map<String, Object> initialValues = new MapBuilder()
        .put("a", 1)
        .put("b", new MapBuilder()
            .put(".value", 1)
            .put(".priority", 1).build())
        .build();

    writer.setValue(initialValues, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        opSemaphore.release();
      }
    });
    TestHelpers.waitFor(opSemaphore);

    writer.child("a").setPriority(ServerValue.TIMESTAMP,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release();
          }
        });
    TestHelpers.waitFor(opSemaphore);

    TestHelpers.waitFor(valSemaphore);
    TestHelpers.assertTimeDelta(priority.get());
  }

  @Test
  public void testServerValuesUpdateLocalEvents()
      throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference writer = IntegrationTestUtils.getRandomNode(masterApp);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final ReadFuture writerFuture = new ReadFuture(writer, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        valSemaphore.release();
        return events.size() == 4;
      }
    });

    // Wait for initial (null) value.
    TestHelpers.waitFor(valSemaphore, 1);

    writer.child("a/b/d").setValue(1, ServerValue.TIMESTAMP,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release();
          }
        });
    TestHelpers.waitFor(opSemaphore);

    Map<String, Object> updatedValue = new MapBuilder()
        .put("b", new MapBuilder()
            .put("c", ServerValue.TIMESTAMP)
            .put("d", ServerValue.TIMESTAMP).build())
        .build();

    writer.child("a").updateChildren(updatedValue, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        opSemaphore.release();
      }
    });
    TestHelpers.waitFor(opSemaphore);

    EventRecord readerEventRecord = writerFuture.timedGet().get(3);
    DataSnapshot snap = readerEventRecord.getSnapshot();
    assertEquals(snap.child("a/b/c").getValue().getClass(), Long.class);
    assertEquals(snap.child("a/b/d").getValue().getClass(), Long.class);
    TestHelpers.assertTimeDelta(Long.parseLong(snap.child("a/b/c").getValue().toString()));
  }

  @Test
  public void testServerValuesTransactionLocalEvents()
      throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference writer = IntegrationTestUtils.getRandomNode(masterApp);
    final Semaphore valSemaphore1 = new Semaphore(0);
    final ReadFuture writerFuture = new ReadFuture(writer, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        valSemaphore1.release();
        return events.size() == 2;
      }
    });

    final Semaphore valSemaphore2 = new Semaphore(0);
    final ReadFuture readerFuture = new ReadFuture(writer, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        valSemaphore2.release();
        return events.size() == 2;
      }
    });

    // Wait for local (null) events.
    TestHelpers.waitFor(valSemaphore1);
    TestHelpers.waitFor(valSemaphore2);

    writer.runTransaction(new Transaction.Handler() {
      @Override
      public Transaction.Result doTransaction(MutableData currentData) {
        try {
          currentData.setValue(ServerValue.TIMESTAMP);
        } catch (DatabaseException e) {
          fail("Should not fail");
        }
        return Transaction.success(currentData);
      }

      @Override
      public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
        if (error != null || !committed) {
          fail("Transaction should succeed");
        }
      }
    });

    // Wait for local events.
    TestHelpers.waitFor(valSemaphore1);
    TestHelpers.waitFor(valSemaphore2);

    EventRecord writerEventRecord = writerFuture.timedGet().get(1);
    DataSnapshot snap1 = writerEventRecord.getSnapshot();
    assertEquals(snap1.getValue().getClass(), Long.class);
    TestHelpers.assertTimeDelta(Long.parseLong(snap1.getValue().toString()));

    EventRecord readerEventRecord = readerFuture.timedGet().get(1);
    DataSnapshot snap2 = readerEventRecord.getSnapshot();
    assertEquals(snap2.getValue().getClass(), Long.class);
    TestHelpers.assertTimeDelta(Long.parseLong(snap2.getValue().toString()));
  }

  @Test
  public void testUpdateAfterChildSet() throws InterruptedException {
    final DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    final Semaphore doneSemaphore = new Semaphore(0);

    Map<String, Object> initial = MapBuilder.of("a", "a");
    ref.setValue(initial, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        ref.addValueEventListener(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            Map res = (Map) snapshot.getValue();
            if (res != null && res.size() == 3 && res.containsKey("a") && res.containsKey("b")
                && res.containsKey("c")) {
              doneSemaphore.release();
            }
          }

          @Override
          public void onCancelled(DatabaseError error) {
          }
        });

        ref.child("b").setValueAsync("b");

        Map<String, Object> update = MapBuilder.of("c", "c");
        ref.updateChildrenAsync(update);
      }
    });

    TestHelpers.waitFor(doneSemaphore);
  }

  @Test
  public void testMaxFrameSize()
      throws InterruptedException, TestFailure, ExecutionException, TimeoutException {
    StringBuilder msg = new StringBuilder("");
    // This will generate a string well over max frame size
    for (int i = 0; i < 16384; ++i) {
      msg.append(String.valueOf(i));
    }

    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    new WriteFuture(ref, msg.toString()).timedGet();

    DataSnapshot snap = TestHelpers.getSnap(ref);
    assertEquals(msg.toString(), snap.getValue());
  }

  @Test
  public void testDeltaSyncNoDataUpdatesAfterReconnect() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    // Create a fresh connection so we can be sure we won't get any other data
    // updates for stuff.    
    final DatabaseReference ref2 = FirebaseDatabase.getInstance(masterApp)
        .getReference(ref.getKey());

    final Map<String, Object> data = new MapBuilder().put("a", 1L).put("b", 2L)
        .put("c", new MapBuilder().put(".value", 3L).put(".priority", 3.0).build()).put("d", 4L)
        .build();

    final Semaphore gotData = new Semaphore(0);
    final AtomicReference<Map> result = new AtomicReference<>();
    ref.setValue(data, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        ref2.addValueEventListener(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            assertFalse(gotData.tryAcquire());
            result.compareAndSet(null, (Map) snapshot.getValue(true));
            gotData.release();
          }

          @Override
          public void onCancelled(DatabaseError error) {
          }
        });
      }
    });

    TestHelpers.waitFor(gotData);
    TestHelpers.assertDeepEquals(data, result.get());

    final Semaphore done = new Semaphore(0);

    // Bounce connection.
    DatabaseConfig ctx = TestHelpers.getDatabaseConfig(masterApp);
    RepoManager.interrupt(ctx);
    RepoManager.resume(ctx);

    ref2.getRoot().child(".info/connected").addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        if ((Boolean) snapshot.getValue()) { 
          // We're connected. Do one more round-trip to make
          // sure all state restoration is done
          ref2.getRoot().child("foobar/empty/blah").setValue(null,
              new DatabaseReference.CompletionListener() {
                @Override
                public void onComplete(DatabaseError error, DatabaseReference ref) {
                  done.release();
                }
              });
        }
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    TestHelpers.waitFor(done);
  }

  @Test
  public void testDeltaSyncWithQueryNoDataUpdatesAfterReconnect() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    // Create a fresh connection so we can be sure we won't get any other data
    // updates for stuff.    
    final DatabaseReference ref2 = FirebaseDatabase.getInstance(masterApp)
        .getReference(ref.getKey());

    final Map<String, Object> data = new MapBuilder()
        .put("a", 1L)
        .put("b", 2L)
        .put("c", new MapBuilder().put(".value", 3L).put(".priority", 3.0).build())
        .put("d", 4L)
        .build();

    final Map<String, Object> expected = new MapBuilder()
        .put("c", new MapBuilder().put(".value", 3L).put(".priority", 3.0).build())
        .put("d", 4L)
        .build();

    final Semaphore gotData = new Semaphore(0);

    final AtomicInteger updateCount = new AtomicInteger(0);
    final AtomicReference<Map> result = new AtomicReference<>();
    ref.setValue(data, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        ref2.limitToLast(2).addValueEventListener(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            assertFalse(gotData.tryAcquire());
            updateCount.incrementAndGet();
            result.compareAndSet(null, (Map) snapshot.getValue(true));
            gotData.release();
          }

          @Override
          public void onCancelled(DatabaseError error) {
          }
        });
      }
    });

    TestHelpers.waitFor(gotData);
    assertEquals(1, updateCount.get());
    TestHelpers.assertDeepEquals(expected, result.get());

    final Semaphore done = new Semaphore(0);

    // Bounce connection.
    DatabaseConfig ctx = TestHelpers.getDatabaseConfig(masterApp);
    RepoManager.interrupt(ctx);
    RepoManager.resume(ctx);

    ref2.getRoot().child(".info/connected").addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        if ((Boolean) snapshot.getValue()) { 
          // We're connected. Do one more round-trip to make
          // sure all state restoration is // done.
          ref2.getRoot().child("foobar/empty/blah").setValue(null,
              new DatabaseReference.CompletionListener() {
                @Override
                public void onComplete(DatabaseError error, DatabaseReference ref) {
                  done.release();
                }
              });
        }
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    TestHelpers.waitFor(done);
  }

  @Test
  @Ignore
  public void testDeltaSyncWithUnfilteredQuery()
      throws InterruptedException, ExecutionException, TestFailure, TimeoutException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writeRef = refs.get(0);
    DatabaseReference readRef = refs.get(1);

    // List must be large enough to trigger delta sync.
    Map<String, Object> longList = new HashMap<>();
    for (long i = 0; i < 50; i++) {
      String key = writeRef.push().getKey();
      longList.put(key,
          new MapBuilder().put("order", i).put("text", "This is an awesome message!").build());
    }

    new WriteFuture(writeRef, longList).timedGet();

    // start listening.
    final List<DataSnapshot> readSnapshots = new ArrayList<>();
    final Semaphore readSemaphore = new Semaphore(0);
    readRef.orderByChild("order").addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        readSnapshots.add(snapshot);
        readSemaphore.release();
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    TestHelpers.waitFor(readSemaphore);
    TestHelpers.assertDeepEquals(longList, readSnapshots.get(0).getValue());

    // Add a new child while readRef is offline.
    readRef.getDatabase().goOffline();

    DatabaseReference newChildRef = writeRef.push();
    Map<String, Object> newChild = new MapBuilder().put("order", 50L)
        .put("text", "This is a new child!").build();
    new WriteFuture(newChildRef, newChild).timedGet();
    longList.put(newChildRef.getKey(), newChild);

    // Go back online and make sure we get the new item.
    readRef.getDatabase().goOnline();
    TestHelpers.waitFor(readSemaphore);
    TestHelpers.assertDeepEquals(longList, readSnapshots.get(1).getValue());
  }

  @Test
  public void testNegativeIntegerKeys()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    new WriteFuture(ref,
        new MapBuilder().put("-1", "minus-one").put("0", "zero").put("1", "one").build())
            .timedGet();

    DataSnapshot snap = TestHelpers.getSnap(ref);
    Map<String, Object> expected = new MapBuilder().put("-1", "minus-one").put("0", "zero")
        .put("1", "one").build();
    Object result = snap.getValue();
    TestHelpers.assertDeepEquals(expected, result);
  }

  @Test
  @Ignore
  public void testLocalServerValuesEventuallyButNotImmediatelyMatchServer()
      throws InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final Semaphore completionSemaphore = new Semaphore(0);
    final List<DataSnapshot> readSnaps = new ArrayList<>();
    final List<DataSnapshot> writeSnaps = new ArrayList<>();

    reader.addValueEventListener(new ValueEventListener() {
      @Override
      public void onCancelled(DatabaseError error) {
      }

      @Override
      public void onDataChange(DataSnapshot snapshot) {
        if (snapshot.getValue() != null) {
          readSnaps.add(snapshot);
          completionSemaphore.release();
        }
      }
    });

    writer.addValueEventListener(new ValueEventListener() {
      @Override
      public void onCancelled(DatabaseError error) {
      }

      @Override
      public void onDataChange(DataSnapshot snapshot) {
        if (snapshot.getValue() != null) {
          if (snapshot.getValue() != null) {
            writeSnaps.add(snapshot);
            completionSemaphore.release();
          }
        }
      }
    });

    writer.setValueAsync(ServerValue.TIMESTAMP, ServerValue.TIMESTAMP);

    TestHelpers.waitFor(completionSemaphore, 3);

    assertEquals(1, readSnaps.size());
    assertEquals(2, writeSnaps.size());
    assertTrue(Math.abs(System.currentTimeMillis() - (Long) writeSnaps.get(0).getValue()) < 3000);
    assertTrue(
        Math.abs(System.currentTimeMillis() - (Double) writeSnaps.get(0).getPriority()) < 3000);
    assertTrue(Math.abs(System.currentTimeMillis() - (Long) writeSnaps.get(1).getValue()) < 3000);
    assertTrue(
        Math.abs(System.currentTimeMillis() - (Double) writeSnaps.get(1).getPriority()) < 3000);
    assertFalse(writeSnaps.get(0).getValue().equals(writeSnaps.get(1).getValue()));
    assertFalse(writeSnaps.get(0).getPriority().equals(writeSnaps.get(1).getPriority()));
    assertEquals(writeSnaps.get(1).getValue(), readSnaps.get(0).getValue());
    assertEquals(writeSnaps.get(1).getPriority(), readSnaps.get(0).getPriority());
  }

  @Test
  public void testBasicObjectMappingRoundTrip()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {

    DumbBean bean = new DumbBean();
    bean.name = "bean";

    DumbBean nestedBean = new DumbBean();
    nestedBean.name = "nested-bean";
    bean.nestedBean = nestedBean;

    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    new WriteFuture(ref, bean).timedGet();

    final Semaphore done = new Semaphore(0);
    ref.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        DumbBean bean = snapshot.getValue(DumbBean.class);
        assertEquals("bean", bean.name);
        assertEquals("nested-bean", bean.nestedBean.name);
        assertNull(bean.nestedBean.nestedBean);
        done.release();
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    TestHelpers.waitFor(done);
  }

  @Test
  public void testUpdateChildrenWithObjectMapping() throws InterruptedException {

    DumbBean bean1 = new DumbBean();
    bean1.name = "bean1";

    DumbBean bean2 = new DumbBean();
    bean2.name = "bean2";

    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final Semaphore writeComplete = new Semaphore(0);
    ref.updateChildren(new MapBuilder().put("bean1", bean1).put("bean2", bean2).build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            writeComplete.release();
          }
        });
    TestHelpers.waitFor(writeComplete);

    final Semaphore done = new Semaphore(0);
    ref.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        Map<String, DumbBean> beans = snapshot
            .getValue(new GenericTypeIndicator<Map<String, DumbBean>>() {
            });
        assertEquals("bean1", beans.get("bean1").name);
        assertEquals("bean2", beans.get("bean2").name);
        done.release();
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    TestHelpers.waitFor(done);
  }

  @Test
  public void testUpdateChildrenDeepUpdatesWithObjectMapping() throws InterruptedException {

    DumbBean bean1 = new DumbBean();
    bean1.name = "bean1";

    DumbBean bean2 = new DumbBean();
    bean2.name = "bean2";

    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    final Semaphore writeComplete = new Semaphore(0);
    ref.updateChildren(new MapBuilder().put("bean1", bean1).put("deep/bean2", bean2).build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            writeComplete.release();
          }
        });
    TestHelpers.waitFor(writeComplete);

    final Semaphore done = new Semaphore(0);
    ref.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        assertEquals("bean1", snapshot.child("bean1").getValue(DumbBean.class).name);
        assertEquals("bean2", snapshot.child("deep/bean2").getValue(DumbBean.class).name);
        done.release();
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    TestHelpers.waitFor(done);
  }

  private static class DumbBean {
    public String name;
    public DumbBean nestedBean;
  }
}
