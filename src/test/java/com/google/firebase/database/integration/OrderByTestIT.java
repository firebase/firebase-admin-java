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

import com.google.common.collect.ImmutableList;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.DatabaseReference.CompletionListener;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.Query;
import com.google.firebase.database.TestChildEventListener;
import com.google.firebase.database.TestFailure;
import com.google.firebase.database.TestHelpers;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.database.future.WriteFuture;
import com.google.firebase.testing.IntegrationTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class OrderByTestIT {

  private static FirebaseApp masterApp;

  @BeforeClass
  public static void setUpClass() {
    masterApp = IntegrationTestUtils.ensureDefaultApp();
  }

  @AfterClass
  public static void tearDownClass() throws IOException {
    uploadRules(masterApp, "{\"rules\": {\".read\": true, \".write\": true}}");
  }

  @Before
  public void prepareApp() {
    TestHelpers.wrapForErrorHandling(masterApp);
  }

  @After
  public void checkAndCleanupApp() {
    TestHelpers.assertAndUnwrapErrorHandlers(masterApp);
  }

  private static String formatRules(DatabaseReference ref, String rules) {
    return String.format(
        "{\"rules\": {\".read\": true, \".write\": true, \"%s\": %s}}", ref.getKey(), rules);
  }
  
  private static void uploadRules(FirebaseApp app, String rules) throws IOException {
    IntegrationTestUtils.AppHttpClient client = new IntegrationTestUtils.AppHttpClient(app);
    IntegrationTestUtils.ResponseInfo response = client.put("/.settings/rules.json", rules);
    assertEquals(200, response.getStatus());
  }

  @Test
  public void testBasicIndexing() throws IOException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    String rules = formatRules(ref, "{ \".indexOn\": \"nuggets\"}");

    uploadRules(masterApp, rules);

    final Semaphore semaphore = new Semaphore(0);

    CompletionListener listener =
        new CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            Assert.assertNull(error);
            semaphore.release();
          }
        };
    ref.push()
        .setValue(TestHelpers.fromJsonString("{\"name\": \"Andrew\", \"nuggets\": 35}"), listener);
    ref.push()
        .setValue(TestHelpers.fromJsonString("{\"name\": \"Rob\", \"nuggets\": 40}"), listener);
    ref.push()
        .setValue(TestHelpers.fromJsonString("{\"name\": \"Greg\", \"nuggets\": 38}"), listener);

    TestHelpers.waitFor(semaphore, 3);

    final List<String> names = new ArrayList<>();

    final ChildEventListener testListener =
        ref.orderByChild("nuggets")
            .addChildEventListener(
                new TestChildEventListener() {
                  @SuppressWarnings("unchecked")
                  @Override
                  public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                    names.add(((Map<String, String>) snapshot.getValue()).get("name"));
                    semaphore.release();
                  }
                });

    TestHelpers.waitFor(semaphore, 3);
    Assert.assertEquals(ImmutableList.of("Andrew", "Greg", "Rob"), names);
    TestHelpers.waitForRoundtrip(ref);
    ref.removeEventListener(testListener);
  }

  @Test
  public void testUseFallbackThenDefineIndex()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure, IOException {

    DatabaseReference writer = IntegrationTestUtils.getRandomNode(masterApp) ;
    DatabaseReference readerReference = FirebaseDatabase.getInstance(masterApp).getReference();
    DatabaseReference reader = readerReference.child(writer.getPath().toString());

    Map<String, Object> foo1 =
        TestHelpers.fromJsonString(
            "{ "
                + "\"a\": {\"order\": 2, \"foo\": 1}, "
                + "\"b\": {\"order\": 0}, "
                + "\"c\": {\"order\": 1, \"foo\": false}, "
                + "\"d\": {\"order\": 3, \"foo\": \"hello\"} }");

    new WriteFuture(writer, foo1).timedGet();

    final List<DataSnapshot> snapshots = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);
    Query query = reader.orderByChild("order").limitToLast(2);
    final ValueEventListener listener =
        query.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                snapshots.add(snapshot);
                semaphore.release();
              }

              @Override
              public void onCancelled(DatabaseError error) {
                Assert.fail();
              }
            });

    TestHelpers.waitFor(semaphore);

    Assert.assertEquals(1, snapshots.size());
    Map<String, Object> expected = MapBuilder.of(
        "d", MapBuilder.of("order", 3L, "foo", "hello"),
        "a", MapBuilder.of("order", 2L, "foo", 1L));
    Assert.assertEquals(expected, snapshots.get(0).getValue());

    uploadRules(masterApp, formatRules(reader, "{ \".indexOn\": \"order\" }"));

    Map<String, Object> fooE = TestHelpers.fromJsonString("{\"order\": 1.5, \"foo\": true}");
    new WriteFuture(writer.child("e"), fooE).timedGet();
    TestHelpers.waitForRoundtrip(reader);

    Map<String, Object> fooF =
        TestHelpers.fromJsonString("{\"order\": 4, \"foo\": {\"bar\": \"baz\"}}");
    new WriteFuture(writer.child("f"), fooF).timedGet();
    TestHelpers.waitForRoundtrip(reader);

    TestHelpers.waitFor(semaphore);
    Assert.assertEquals(2, snapshots.size());

    Map<String, Object> expected2 =
        new MapBuilder()
            .put(
                "f",
                new MapBuilder()
                    .put("order", 4L)
                    .put("foo", MapBuilder.of("bar", "baz"))
                    .build())
            .put("d", MapBuilder.of("order", 3L, "foo", "hello"))
            .build();
    Assert.assertEquals(expected2, snapshots.get(1).getValue());

    // cleanup
    TestHelpers.waitForRoundtrip(reader);
    reader.removeEventListener(listener);
  }

  @Test
  public void testSnapshotIterationOrder()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    Map<String, Object> initial =
        TestHelpers.fromJsonString(
            "{"
                + "\"alex\": {\"nuggets\": 60},"
                + "\"greg\": {\"nuggets\": 52},"
                + "\"rob\": {\"nuggets\": 56},"
                + "\"vassili\": {\"nuggets\": 55.5},"
                + "\"tony\": {\"nuggets\": 52}"
                + "}");

    Query query = ref.orderByChild("nuggets");

    final List<String> valueOrder = new ArrayList<>();
    final List<String> childOrder = new ArrayList<>();
    final List<String> childPrevNames = new ArrayList<>();
    final ValueEventListener valueListener =
        query.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                  valueOrder.add(child.getKey());
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {
                Assert.fail();
              }
            });

    final ChildEventListener testListener =
        query.addChildEventListener(
            new TestChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                childOrder.add(snapshot.getKey());
                childPrevNames.add(previousChildName);
              }
            });

    new WriteFuture(ref, initial).timedGet();

    List<String> expectedOrder = Arrays.asList("greg", "tony", "vassili", "rob", "alex");
    List<String> expectedPrevNames = Arrays.asList(null, "greg", "tony", "vassili", "rob");
    Assert.assertEquals(expectedOrder, valueOrder);
    Assert.assertEquals(expectedOrder, childOrder);
    Assert.assertEquals(expectedPrevNames, childPrevNames);

    // cleanup
    TestHelpers.waitForRoundtrip(ref);
    ref.removeEventListener(testListener);
    ref.removeEventListener(valueListener);
  }

  @Test
  public void testDeepPathsForIndex()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    Map<String, Object> initial =
        TestHelpers.fromJsonString(
            "{"
                + "\"alex\": {\"deep\": {\"nuggets\": 60}},"
                + "\"greg\": {\"deep\": {\"nuggets\": 52}},"
                + "\"rob\": {\"deep\": {\"nuggets\": 56}},"
                + "\"vassili\": {\"deep\": {\"nuggets\": 55.5}},"
                + "\"tony\": {\"deep\": {\"nuggets\": 52}}"
                + "}");

    new WriteFuture(ref, initial).timedGet();

    Query query = ref.orderByChild("deep/nuggets").limitToFirst(3);

    final List<String> valueOrder = new ArrayList<>();
    final List<String> childOrder = new ArrayList<>();
    final List<String> childPrevNames = new ArrayList<>();
    final ValueEventListener valueListener =
        query.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                  valueOrder.add(child.getKey());
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {
                Assert.fail();
              }
            });

    final ChildEventListener testListener =
        query.addChildEventListener(
            new TestChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                childOrder.add(snapshot.getKey());
                childPrevNames.add(previousChildName);
              }
            });

    TestHelpers.waitForRoundtrip(ref);

    List<String> expectedOrder = Arrays.asList("greg", "tony", "vassili");
    List<String> expectedPrevNames = Arrays.asList(null, "greg", "tony");
    Assert.assertEquals(expectedOrder, valueOrder);
    Assert.assertEquals(expectedOrder, childOrder);
    Assert.assertEquals(expectedPrevNames, childPrevNames);

    // cleanup
    ref.removeEventListener(testListener);
    ref.removeEventListener(valueListener);
  }

  @Test
  public void testSnapshotIterationOrderForValueIndex()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    Map<String, Object> initial =
        TestHelpers.fromJsonString(
            "{"
                + "\"alex\": 60,"
                + "\"greg\": 52,"
                + "\"rob\": 56,"
                + "\"vassili\": 55.5,"
                + "\"tony\": 52"
                + "}");

    Query query = ref.orderByValue();

    final List<String> valueOrder = new ArrayList<>();
    final List<String> childOrder = new ArrayList<>();
    final List<String> childPrevNames = new ArrayList<>();
    final ValueEventListener valueListener =
        query.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                  valueOrder.add(child.getKey());
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {
                Assert.fail();
              }
            });

    final ChildEventListener testListener =
        query.addChildEventListener(
            new TestChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                childOrder.add(snapshot.getKey());
                childPrevNames.add(previousChildName);
              }
            });

    new WriteFuture(ref, initial).timedGet();

    List<String> expectedOrder = Arrays.asList("greg", "tony", "vassili", "rob", "alex");
    List<String> expectedPrevNames = Arrays.asList(null, "greg", "tony", "vassili", "rob");
    Assert.assertEquals(expectedOrder, valueOrder);
    Assert.assertEquals(expectedOrder, childOrder);
    Assert.assertEquals(expectedPrevNames, childPrevNames);

    // cleanup
    TestHelpers.waitForRoundtrip(ref);
    ref.removeEventListener(testListener);
    ref.removeEventListener(valueListener);
  }

  @Test
  public void testCildMovedEvents() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;
    Map<String, Object> initial =
        TestHelpers.fromJsonString(
            "{"
                + "\"alex\": {\"nuggets\": 60},"
                + "\"greg\": {\"nuggets\": 52},"
                + "\"rob\": {\"nuggets\": 56},"
                + "\"vassili\": {\"nuggets\": 55.5},"
                + "\"tony\": {\"nuggets\": 52}"
                + "}");

    Query query = ref.orderByChild("nuggets");

    final Semaphore semaphore = new Semaphore(0);
    final String[] prevName = new String[1];
    final DataSnapshot[] snapshot = new DataSnapshot[1];
    final ChildEventListener testListener =
        query.addChildEventListener(
            new TestChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {}

              @Override
              public void onChildMoved(DataSnapshot snap, String previousChildName) {
                snapshot[0] = snap;
                prevName[0] = previousChildName;
                semaphore.release();
              }

              @Override
              public void onChildChanged(DataSnapshot snap, String previousChildName) {}
            });

    ref.setValueAsync(initial);
    ref.child("greg/nuggets").setValueAsync(57);

    TestHelpers.waitFor(semaphore);

    Assert.assertEquals("greg", snapshot[0].getKey());
    Assert.assertEquals("rob", prevName[0]);
    Map<String, Object> expectedValue = MapBuilder.of("nuggets", 57L);
    Assert.assertEquals(expectedValue, snapshot[0].getValue());

    TestHelpers.waitForRoundtrip(ref);
    ref.removeEventListener(testListener);
  }

  @Test
  public void testMultipleIndexesAtLocation()
      throws IOException, ExecutionException, InterruptedException, TimeoutException, TestFailure {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    Map<String, Object> foo1 =
        TestHelpers.fromJsonString(
            "{"
                + "\"a\": {\"order\": 2, \"foo\": 2},"
                + "\"b\": {\"order\": 0},"
                + "\"c\": {\"order\": 1, \"foo\": false},"
                + "\"d\": {\"order\": 3, \"foo\": \"hello\"}"
                + "}");

    String indexDef = "{\".indexOn\": [\"order\", \"foo\"]}";
    String rules = formatRules(reader, indexDef);
    uploadRules(masterApp, rules);

    new WriteFuture(writer, foo1).timedGet();

    Query fooQuery = reader.orderByChild("foo");
    Query orderQuery = reader.orderByChild("order");
    final List<DataSnapshot> fooSnaps = new ArrayList<>();
    final List<DataSnapshot> orderSnaps = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);

    final ValueEventListener fooListener =
        fooQuery
            .startAt(null)
            .endAt(1)
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    fooSnaps.add(snapshot);
                    semaphore.release();
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {
                    Assert.fail();
                  }
                });

    final ValueEventListener orderListener =
        orderQuery
            .limitToLast(2)
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    orderSnaps.add(snapshot);
                    semaphore.release();
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {
                    Assert.fail();
                  }
                });

    TestHelpers.waitFor(semaphore, 2);

    Map<String, Object> fooExpected = new HashMap<>();
    fooExpected.put("b", MapBuilder.of("order", 0L));
    fooExpected.put("c", MapBuilder.of("order", 1L, "foo", false));

    Map<String, Object> orderExpected = new HashMap<>();
    orderExpected.put("d", MapBuilder.of("order", 3L, "foo", "hello"));
    orderExpected.put("a", MapBuilder.of("order", 2L, "foo", 2L));

    Assert.assertEquals(1, fooSnaps.size());
    Assert.assertEquals(fooExpected, fooSnaps.get(0).getValue());
    Assert.assertEquals(1, orderSnaps.size());
    Assert.assertEquals(orderExpected, orderSnaps.get(0).getValue());

    new WriteFuture(writer.child("a"), MapBuilder.of("order", -1L, "foo", 1L)).timedGet();

    fooExpected = new HashMap<>();
    fooExpected.put("a", MapBuilder.of("order", -1L, "foo", 1L));
    fooExpected.put("b", MapBuilder.of("order", 0L));
    fooExpected.put("c", MapBuilder.of("order", 1L, "foo", false));

    orderExpected = new HashMap<>();
    orderExpected.put("d", MapBuilder.of("order", 3L, "foo", "hello"));
    orderExpected.put("c", MapBuilder.of("order", 1L, "foo", false));

    TestHelpers.waitFor(semaphore, 2);

    Assert.assertEquals(2, fooSnaps.size());
    Assert.assertEquals(fooExpected, fooSnaps.get(1).getValue());

    //FIXME: There are 3 snapshots here for some reason, instead of 2.
    //Assert.assertEquals(2, orderSnaps.size());
    //Assert.assertEquals(orderExpected, orderSnaps.get(1).getValue());

    // cleanup
    TestHelpers.waitForRoundtrip(reader);
    reader.removeEventListener(fooListener);
    reader.removeEventListener(orderListener);
  }

  @Test
  public void testCallbackRemoval() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    final int[] reads = new int[1];
    final Semaphore semaphore = new Semaphore(0);
    final ValueEventListener fooListener =
        ref.orderByChild("foo")
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    reads[0]++;
                    semaphore.release();
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {
                    Assert.fail();
                  }
                });
    final ValueEventListener barListener =
        ref.orderByChild("bar")
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    reads[0]++;
                    semaphore.release();
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {
                    Assert.fail();
                  }
                });
    final ValueEventListener bazListener =
        ref.orderByChild("baz")
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    reads[0]++;
                    semaphore.release();
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {
                    Assert.fail();
                  }
                });
    final ValueEventListener defaultListener =
        ref.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                reads[0]++;
                semaphore.release();
              }

              @Override
              public void onCancelled(DatabaseError error) {
                Assert.fail();
              }
            });

    // wait for initial null events.
    TestHelpers.waitFor(semaphore, 4);
    Assert.assertEquals(4, reads[0]);

    ref.setValueAsync(1);

    TestHelpers.waitFor(semaphore, 4);
    Assert.assertEquals(8, reads[0]);

    ref.removeEventListener(fooListener);
    ref.setValueAsync(2);

    TestHelpers.waitFor(semaphore, 3);
    Assert.assertEquals(11, reads[0]);

    // Should be a no-op resulting in 3 more reads
    ref.orderByChild("foo").removeEventListener(bazListener);
    ref.setValueAsync(3);

    TestHelpers.waitFor(semaphore, 3);
    Assert.assertEquals(14, reads[0]);

    ref.orderByChild("bar").removeEventListener(barListener);
    ref.setValueAsync(4);
    TestHelpers.waitFor(semaphore, 2);
    Assert.assertEquals(16, reads[0]);

    // Now, remove everything
    ref.removeEventListener(bazListener);
    ref.removeEventListener(defaultListener);
    ref.setValueAsync(5);

    // No more reads
    TestHelpers.waitForRoundtrip(ref);
    Assert.assertEquals(16, reads[0]);
  }

  @Test
  public void testChildAddedEvents() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    Map<String, Object> initial =
        new MapBuilder()
            .put("a", MapBuilder.of("value", 5L))
            .put("c", MapBuilder.of("value", 3L))
            .build();

    final List<String> snapshotNames = new ArrayList<>();
    final List<String> prevNames = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);
    final ChildEventListener testListener =
        ref.orderByChild("value")
            .addChildEventListener(
                new TestChildEventListener() {
                  @Override
                  public void onChildAdded(DataSnapshot snap, String prevName) {
                    snapshotNames.add(snap.getKey());
                    prevNames.add(prevName);
                    semaphore.release();
                  }
                });

    ref.setValueAsync(initial);
    TestHelpers.waitFor(semaphore, 2);
    Assert.assertEquals(Arrays.asList("c", "a"), snapshotNames);
    Assert.assertEquals(Arrays.asList(null, "c"), prevNames);

    Map<String, Object> updates = new HashMap<>();
    updates.put("b", MapBuilder.of("value", 4));
    updates.put("d", MapBuilder.of("value", 2));
    ref.updateChildrenAsync(updates);

    TestHelpers.waitFor(semaphore, 2);
    Assert.assertEquals(Arrays.asList("c", "a", "d", "b"), snapshotNames);
    Assert.assertEquals(Arrays.asList(null, "c", null, "c"), prevNames);
    ref.removeEventListener(testListener);
  }

  @Test
  public void testUpdatesForUnindexedQuery()
      throws InterruptedException, ExecutionException, TestFailure, TimeoutException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    final DatabaseReference reader = refs.get(0);
    final DatabaseReference writer = refs.get(1);

    final List<DataSnapshot> snapshots = new ArrayList<>();

    Map<String, Object> value = new HashMap<>();
    value.put("one", new MapBuilder().put("index", 1).put("value", "one").build());
    value.put("two", new MapBuilder().put("index", 2).put("value", "two").build());
    value.put("three", new MapBuilder().put("index", 3).put("value", "three").build());

    new WriteFuture(writer, value).timedGet();

    final Semaphore semaphore = new Semaphore(0);

    Query query = reader.orderByChild("index").limitToLast(2);
    final ValueEventListener listener =
        query.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                snapshots.add(snapshot);
                semaphore.release();
              }

              @Override
              public void onCancelled(DatabaseError error) {
                Assert.fail();
              }
            });

    TestHelpers.waitFor(semaphore);

    Assert.assertEquals(1, snapshots.size());

    Map<String, Object> expected1 = new HashMap<>();
    expected1.put("two", new MapBuilder().put("index", 2L).put("value", "two").build());
    expected1.put("three", new MapBuilder().put("index", 3L).put("value", "three").build());
    Assert.assertEquals(expected1, snapshots.get(0).getValue());

    // update child which should trigger value event
    writer.child("one/index").setValueAsync(4);
    TestHelpers.waitFor(semaphore);

    Assert.assertEquals(2, snapshots.size());
    Map<String, Object> expected2 = new HashMap<>();
    expected2.put("three", new MapBuilder().put("index", 3L).put("value", "three").build());
    expected2.put("one", new MapBuilder().put("index", 4L).put("value", "one").build());
    Assert.assertEquals(expected2, snapshots.get(1).getValue());

    // cleanup
    TestHelpers.waitForRoundtrip(reader);
    reader.removeEventListener(listener);
  }

  @Test
  public void testQueriesOnLeafNodes()
      throws InterruptedException, ExecutionException, TestFailure, TimeoutException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;
    final Semaphore semaphore = new Semaphore(0);
    new WriteFuture(ref, "leaf-node").timedGet();

    final List<DataSnapshot> snapshots = new ArrayList<>();
    Query query = ref.orderByChild("foo").limitToLast(1);
    final ValueEventListener listener =
        query.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                snapshots.add(snapshot);
                semaphore.release();
              }

              @Override
              public void onCancelled(DatabaseError error) {
                Assert.fail();
              }
            });

    TestHelpers.waitFor(semaphore);

    Assert.assertEquals(1, snapshots.size());
    Assert.assertNull(snapshots.get(0).getValue());

    // cleanup
    TestHelpers.waitForRoundtrip(ref);
    ref.removeEventListener(listener);
  }

  @Test
  public void testServerRespectsKeyIndex()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    Map<String, Object> initial = MapBuilder.of("a", 1, "b", 2, "c", 3);
    // If the server doesn't respect the index, it will send down limited data, but with no
    // offset, so the expected and actual data don't match.
    Query query = reader.orderByKey().startAt("b").limitToFirst(2);

    new WriteFuture(writer, initial).timedGet();

    final List<String> actualChildren = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);
    ValueEventListener valueListener =
        query.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                  actualChildren.add(child.getKey());
                }
                semaphore.release();
              }

              @Override
              public void onCancelled(DatabaseError error) {
                Assert.fail();
              }
            });

    TestHelpers.waitFor(semaphore);
    Assert.assertEquals(ImmutableList.of("b", "c"), actualChildren);

    // cleanup
    reader.removeEventListener(valueListener);
  }

  @Test
  public void testServerRespectsValueIndex()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure, IOException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final String indexRule = "{ \".indexOn\": \".value\"}";
    String rules = formatRules(writer, indexRule);

    uploadRules(masterApp, rules);

    Map<String, Object> initial = MapBuilder.of("a", 1, "c", 2, "b", 3);
    // If the server doesn't respect the index, it will send down limited data, but with no
    // offset, so the expected and actual data don't match.
    Query query = reader.orderByValue().startAt(2).limitToFirst(2);

    new WriteFuture(writer, initial).timedGet();

    final List<String> actualChildren = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);
    ValueEventListener valueListener =
        query.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                  actualChildren.add(child.getKey());
                }
                semaphore.release();
              }

              @Override
              public void onCancelled(DatabaseError error) {
                Assert.fail();
              }
            });

    TestHelpers.waitFor(semaphore);

    Assert.assertEquals(ImmutableList.of("c", "b"), actualChildren);

    // cleanup
    reader.removeEventListener(valueListener);
  }

  @Test
  public void testDeepUpdates()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure, IOException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final String indexRule = "{ \".indexOn\": \"idx\"}";
    String rules = formatRules(writer, indexRule);

    uploadRules(masterApp, rules);

    Map<String, Object> initial =
        new MapBuilder()
            .put("a", MapBuilder.of("data", "foo", "idx", true))
            .put("b", MapBuilder.of("data", "bar", "idx", true))
            .put("c", MapBuilder.of("data", "baz", "idx", false))
            .build();
    new WriteFuture(writer, initial).timedGet();

    Query query = reader.orderByChild("idx").equalTo(true);

    DataSnapshot snap = TestHelpers.getSnap(query);
    TestHelpers.assertDeepEquals(
        new MapBuilder()
            .put("a", MapBuilder.of("data", "foo", "idx", true))
            .put("b", MapBuilder.of("data", "bar", "idx", true))
            .build(),
        snap.getValue());

    Map<String, Object> update =
        new MapBuilder().put("a/idx", false).put("b/data", "blah").put("c/idx", true).build();
    final Semaphore semaphore = new Semaphore(0);
    writer.updateChildren(
        update,
        new CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            assertNull(error);
            semaphore.release(1);
          }
        });
    TestHelpers.waitFor(semaphore);

    snap = TestHelpers.getSnap(query);
    TestHelpers.assertDeepEquals(
        new MapBuilder()
            .put("b", MapBuilder.of("data", "blah", "idx", true))
            .put("c", MapBuilder.of("data", "baz", "idx", true))
            .build(),
        snap.getValue());
  }

  @Test
  public void testStartAtAndEndAtOnValueIndex()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure, IOException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    Map<String, Object> initial =
        TestHelpers.fromJsonString(
            "{"
                + "\"alex\": 60,"
                + "\"greg\": 52,"
                + "\"rob\": 56,"
                + "\"vassili\": 55.5,"
                + "\"tony\": 52"
                + "}");

    Query query = ref.orderByValue().startAt(52, "tony").endAt(56);

    final List<String> valueOrder = new ArrayList<>();
    final List<String> childOrder = new ArrayList<>();
    final List<String> childPrevNames = new ArrayList<>();
    final ValueEventListener valueListener =
        query.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                  valueOrder.add(child.getKey());
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {
                Assert.fail();
              }
            });

    final ChildEventListener testListener =
        query.addChildEventListener(
            new TestChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                childOrder.add(snapshot.getKey());
                childPrevNames.add(previousChildName);
              }
            });

    new WriteFuture(ref, initial).timedGet();

    List<String> expectedOrder = Arrays.asList("tony", "vassili", "rob");
    List<String> expectedPrevNames = Arrays.asList(null, "tony", "vassili");
    Assert.assertEquals(expectedOrder, valueOrder);
    Assert.assertEquals(expectedOrder, childOrder);
    Assert.assertEquals(expectedPrevNames, childPrevNames);

    // cleanup
    TestHelpers.waitForRoundtrip(ref);
    ref.removeEventListener(testListener);
    ref.removeEventListener(valueListener);
  }

  @Test
  public void testRemovingDefaultListener()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure, IOException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    Object initialData = MapBuilder.of("key", "value");
    new WriteFuture(ref, initialData).timedGet();

    ValueEventListener listener =
        ref.orderByKey()
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {}

                  @Override
                  public void onCancelled(DatabaseError error) {}
                });

    ref.addValueEventListener(listener);
    // Should remove both listener and should remove the listen sent to the server
    ref.removeEventListener(listener);

    // This used to crash because a listener for ref.orderByKey() existed already
    Object result = new ReadFuture(ref.orderByKey()).waitForLastValue();
    assertEquals(initialData, result);
  }
}
