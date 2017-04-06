package com.google.firebase.database;

import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.database.future.WriteFuture;
import com.google.firebase.database.logging.DefaultLogger;
import com.google.firebase.database.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OrderByTestIT {

  private static String formatRules(DatabaseReference ref, String rules) {
    return String.format(
        "{\"rules\": {\".read\": true, \".write\": true, \"%s\": %s}}", ref.getKey(), rules);
  }

  @After
  public void cleanup() throws IOException {
    DatabaseReference ref = TestHelpers.getRandomNode();
    TestHelpers.uploadRules(ref, "{\"rules\": {\".read\": true, \".write\": true}}");
    TestHelpers.failOnFirstUncaughtException();
  }

  @Test
  public void defineAndUseIndexWorks() throws IOException, InterruptedException {
    final String indexRule = "{ \".indexOn\": \"nuggets\"}";
    DatabaseReference ref = TestHelpers.getRandomNode();
    String rules = formatRules(ref, indexRule);

    TestHelpers.uploadRules(ref, rules);

    final Semaphore semaphore = new Semaphore(0);

    DatabaseReference.CompletionListener listener =
        new DatabaseReference.CompletionListener() {
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

    ChildEventListener testListener =
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

    Assert.assertEquals(Arrays.asList("Andrew", "Greg", "Rob"), names);

    TestHelpers.waitForRoundtrip(ref);
    ref.removeEventListener(testListener);
  }

  @Test
  public void canUseAFallbackThenDefineTheSpecifiedIndex()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure, IOException {

    final List<String> warnMessages = new ArrayList<>();
    final Semaphore warnSemaphore = new Semaphore(0);
    DefaultLogger testLogger =
        new DefaultLogger(Logger.Level.DEBUG, null) {
          @Override
          protected void warn(String tag, String toLog) {
            super.warn(tag, toLog);
            warnMessages.add(toLog);
            warnSemaphore.release();
          }
        };

    DatabaseReference writer = TestHelpers.getRandomNode();

    // Setup with config and test logger to make sure we can read the warnings
    DatabaseConfig readerCfg = TestHelpers.newTestConfig();
    readerCfg.setLogger(testLogger);
    DatabaseReference readerReference = TestHelpers.rootWithConfig(readerCfg);
    DatabaseReference reader = readerReference.child(writer.getPath().toString());

    Map<String, Object> foo1 =
        TestHelpers.fromJsonString(
            "{ "
                + "\"a\": {\"order\": 2, \"foo\": 1}, "
                + "\"b\": {\"order\": 0}, "
                + "\"c\": {\"order\": 1, \"foo\": false}, "
                + "\"d\": {\"order\": 3, \"foo\": \"hello\"} }");
    Map<String, Object> fooE = TestHelpers.fromJsonString("{\"order\": 1.5, \"foo\": true}");
    Map<String, Object> fooF =
        TestHelpers.fromJsonString("{\"order\": 4, \"foo\": {\"bar\": \"baz\"}}");

    new WriteFuture(writer, foo1).timedGet();

    final List<DataSnapshot> snapshots = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);
    Query query = reader.orderByChild("order").limitToLast(2);
    ValueEventListener listener =
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
    // TODO(dimond): DynamicConnection uses a new logger instance on Android, so the warnings
    // will not actually be visible to the provided logger.
    /*
    TestHelpers.waitFor(warnSemaphore);

    // We should've gotten warned about not having an index
    Assert.assertEquals("We should have received a warning message about no indexes", 1,
        warnMessages.size());
    // check for content in the warn message that should be somehow be communicated
    String warnMessage = warnMessages.get(0);
    Assert.assertTrue(warnMessage.contains("\".indexOn\": \"order\""));
    Assert.assertTrue(warnMessage.contains("security"));
    Assert.assertTrue(warnMessage.contains("rules"));
    Assert.assertTrue(warnMessage.contains("performance"));
    Assert.assertTrue(warnMessage.contains(reader.getPath().toString()));
    */

    Assert.assertEquals(1, snapshots.size());
    Map<String, Object> expected =
        new MapBuilder()
            .put("d", new MapBuilder().put("order", 3L).put("foo", "hello").build())
            .put("a", new MapBuilder().put("order", 2L).put("foo", 1L).build())
            .build();
    Assert.assertEquals(expected, snapshots.get(0).getValue());

    String indexDef = "{ \".indexOn\": \"order\" }";
    TestHelpers.uploadRules(reader, formatRules(reader, indexDef));

    new WriteFuture(writer.child("e"), fooE).timedGet();

    TestHelpers.waitForRoundtrip(reader);

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
                    .put("foo", new MapBuilder().put("bar", "baz").build())
                    .build())
            .put("d", new MapBuilder().put("order", 3L).put("foo", "hello").build())
            .build();
    Assert.assertEquals(expected2, snapshots.get(1).getValue());

    // cleanup
    TestHelpers.waitForRoundtrip(reader);
    reader.removeEventListener(listener);
  }

  @Test
  public void snapshotsAreIteratedInOrder()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    DatabaseReference ref = TestHelpers.getRandomNode();

    Map<String, Object> initial =
        TestHelpers.fromJsonString(
            "{"
                + "\"alex\": {\"nuggets\": 60},"
                + "\"greg\": {\"nuggets\": 52},"
                + "\"rob\": {\"nuggets\": 56},"
                + "\"vassili\": {\"nuggets\": 55.5},"
                + "\"tony\": {\"nuggets\": 52}"
                + "}");
    List<String> expectedOrder = Arrays.asList("greg", "tony", "vassili", "rob", "alex");
    List<String> expectedPrevNames = Arrays.asList(null, "greg", "tony", "vassili", "rob");

    Query query = ref.orderByChild("nuggets");

    final List<String> valueOrder = new ArrayList<>();
    final List<String> childOrder = new ArrayList<>();
    final List<String> childPrevNames = new ArrayList<>();
    ValueEventListener valueListener =
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

    ChildEventListener testListener =
        query.addChildEventListener(
            new TestChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                childOrder.add(snapshot.getKey());
                childPrevNames.add(previousChildName);
              }
            });

    new WriteFuture(ref, initial).timedGet();

    Assert.assertEquals(expectedOrder, valueOrder);
    Assert.assertEquals(expectedOrder, childOrder);
    Assert.assertEquals(expectedPrevNames, childPrevNames);

    // cleanup
    TestHelpers.waitForRoundtrip(ref);
    ref.removeEventListener(testListener);
    ref.removeEventListener(valueListener);
  }

  @Test
  public void canUseDeepPathsForIndex()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    DatabaseReference ref = TestHelpers.getRandomNode();

    Map<String, Object> initial =
        TestHelpers.fromJsonString(
            "{"
                + "\"alex\": {\"deep\": {\"nuggets\": 60}},"
                + "\"greg\": {\"deep\": {\"nuggets\": 52}},"
                + "\"rob\": {\"deep\": {\"nuggets\": 56}},"
                + "\"vassili\": {\"deep\": {\"nuggets\": 55.5}},"
                + "\"tony\": {\"deep\": {\"nuggets\": 52}}"
                + "}");
    List<String> expectedOrder = Arrays.asList("greg", "tony", "vassili");
    List<String> expectedPrevNames = Arrays.asList(null, "greg", "tony");

    new WriteFuture(ref, initial).timedGet();

    Query query = ref.orderByChild("deep/nuggets").limitToFirst(3);

    final List<String> valueOrder = new ArrayList<>();
    final List<String> childOrder = new ArrayList<>();
    final List<String> childPrevNames = new ArrayList<>();
    ValueEventListener valueListener =
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

    ChildEventListener testListener =
        query.addChildEventListener(
            new TestChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                childOrder.add(snapshot.getKey());
                childPrevNames.add(previousChildName);
              }
            });

    TestHelpers.waitForRoundtrip(ref);

    Assert.assertEquals(expectedOrder, valueOrder);
    Assert.assertEquals(expectedOrder, childOrder);
    Assert.assertEquals(expectedPrevNames, childPrevNames);

    // cleanup
    ref.removeEventListener(testListener);
    ref.removeEventListener(valueListener);
  }

  @Test
  public void snapshotsAreIteratedInOrderForValueIndex()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    DatabaseReference ref = TestHelpers.getRandomNode();

    Map<String, Object> initial =
        TestHelpers.fromJsonString(
            "{"
                + "\"alex\": 60,"
                + "\"greg\": 52,"
                + "\"rob\": 56,"
                + "\"vassili\": 55.5,"
                + "\"tony\": 52"
                + "}");
    List<String> expectedOrder = Arrays.asList("greg", "tony", "vassili", "rob", "alex");
    List<String> expectedPrevNames = Arrays.asList(null, "greg", "tony", "vassili", "rob");

    Query query = ref.orderByValue();

    final List<String> valueOrder = new ArrayList<>();
    final List<String> childOrder = new ArrayList<>();
    final List<String> childPrevNames = new ArrayList<>();
    ValueEventListener valueListener =
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

    ChildEventListener testListener =
        query.addChildEventListener(
            new TestChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                childOrder.add(snapshot.getKey());
                childPrevNames.add(previousChildName);
              }
            });

    new WriteFuture(ref, initial).timedGet();

    Assert.assertEquals(expectedOrder, valueOrder);
    Assert.assertEquals(expectedOrder, childOrder);
    Assert.assertEquals(expectedPrevNames, childPrevNames);

    // cleanup
    TestHelpers.waitForRoundtrip(ref);
    ref.removeEventListener(testListener);
    ref.removeEventListener(valueListener);
  }

  @Test
  public void childMovedEventsAreFired() throws InterruptedException {
    DatabaseReference ref = TestHelpers.getRandomNode();
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
    ChildEventListener testListener =
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

    ref.setValue(initial);
    ref.child("greg/nuggets").setValue(57);

    TestHelpers.waitFor(semaphore);

    Assert.assertEquals("greg", snapshot[0].getKey());
    Assert.assertEquals("rob", prevName[0]);
    Map<String, Long> expectedValue = new HashMap<>();
    expectedValue.put("nuggets", 57L);
    Assert.assertEquals(expectedValue, snapshot[0].getValue());

    TestHelpers.waitForRoundtrip(ref);
    ref.removeEventListener(testListener);
  }

  @Test
  public void multipleIndexesAtLocation()
      throws IOException, ExecutionException, InterruptedException, TimeoutException, TestFailure {
    List<DatabaseReference> refs = TestHelpers.getRandomNode(2);
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
    TestHelpers.uploadRules(reader, rules);

    new WriteFuture(writer, foo1).timedGet();

    Query fooQuery = reader.orderByChild("foo");
    Query orderQuery = reader.orderByChild("order");
    final List<DataSnapshot> fooSnaps = new ArrayList<>();
    final List<DataSnapshot> orderSnaps = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);

    ValueEventListener fooListener =
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

    ValueEventListener orderListener =
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
    fooExpected.put("b", new MapBuilder().put("order", 0L).build());
    fooExpected.put("c", new MapBuilder().put("order", 1L).put("foo", false).build());

    Map<String, Object> orderExpected = new HashMap<>();
    orderExpected.put("d", new MapBuilder().put("order", 3L).put("foo", "hello").build());
    orderExpected.put("a", new MapBuilder().put("order", 2L).put("foo", 2L).build());

    Assert.assertEquals(1, fooSnaps.size());
    Assert.assertEquals(fooExpected, fooSnaps.get(0).getValue());
    Assert.assertEquals(1, orderSnaps.size());
    Assert.assertEquals(orderExpected, orderSnaps.get(0).getValue());

    new WriteFuture(writer.child("a"), new MapBuilder().put("order", -1L).put("foo", 1L).build())
        .timedGet();

    fooExpected = new HashMap<>();
    fooExpected.put("a", new MapBuilder().put("order", -1L).put("foo", 1L).build());
    fooExpected.put("b", new MapBuilder().put("order", 0L).build());
    fooExpected.put("c", new MapBuilder().put("order", 1L).put("foo", false).build());

    orderExpected = new HashMap<>();
    orderExpected.put("d", new MapBuilder().put("order", 3L).put("foo", "hello").build());
    orderExpected.put("c", new MapBuilder().put("order", 1L).put("foo", false).build());

    TestHelpers.waitFor(semaphore, 2);

    Assert.assertEquals(2, fooSnaps.size());
    Assert.assertEquals(fooExpected, fooSnaps.get(1).getValue());
    Assert.assertEquals(2, orderSnaps.size());
    Assert.assertEquals(orderExpected, orderSnaps.get(1).getValue());

    // cleanup
    TestHelpers.waitForRoundtrip(reader);
    reader.removeEventListener(fooListener);
    reader.removeEventListener(orderListener);
  }

  @Test
  public void callbackRemovalWorks() throws InterruptedException {
    DatabaseReference ref = TestHelpers.getRandomNode();

    final int[] reads = new int[1];
    final Semaphore semaphore = new Semaphore(0);
    ValueEventListener fooListener =
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
    ValueEventListener barListener =
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
    ValueEventListener bazListener =
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
    ValueEventListener defaultListener =
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

    ref.setValue(1);

    TestHelpers.waitFor(semaphore, 4);
    Assert.assertEquals(8, reads[0]);

    ref.removeEventListener(fooListener);
    ref.setValue(2);

    TestHelpers.waitFor(semaphore, 3);
    Assert.assertEquals(11, reads[0]);

    // Should be a no-op resulting in 3 more reads
    ref.orderByChild("foo").removeEventListener(bazListener);
    ref.setValue(3);

    TestHelpers.waitFor(semaphore, 3);
    Assert.assertEquals(14, reads[0]);

    ref.orderByChild("bar").removeEventListener(barListener);
    ref.setValue(4);
    TestHelpers.waitFor(semaphore, 2);
    Assert.assertEquals(16, reads[0]);

    // Now, remove everything
    ref.removeEventListener(bazListener);
    ref.removeEventListener(defaultListener);
    ref.setValue(5);

    // No more reads
    TestHelpers.waitForRoundtrip(ref);
    Assert.assertEquals(16, reads[0]);
  }

  @Test
  public void childAddedEventsAreInCorrectOrder() throws InterruptedException {
    DatabaseReference ref = TestHelpers.getRandomNode();

    Map<String, Object> initial =
        new MapBuilder()
            .put("a", new MapBuilder().put("value", 5L).build())
            .put("c", new MapBuilder().put("value", 3L).build())
            .build();

    final List<String> snapshotNames = new ArrayList<>();
    final List<String> prevNames = new ArrayList<>();
    final Semaphore semaphore = new Semaphore(0);
    ChildEventListener testListener =
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

    ref.setValue(initial);
    TestHelpers.waitFor(semaphore, 2);
    Assert.assertEquals(Arrays.asList("c", "a"), snapshotNames);
    Assert.assertEquals(Arrays.asList(null, "c"), prevNames);

    Map<String, Object> updates = new HashMap<>();
    updates.put("b", new MapBuilder().put("value", 4).build());
    updates.put("d", new MapBuilder().put("value", 2).build());
    ref.updateChildren(updates);

    TestHelpers.waitFor(semaphore, 2);
    Assert.assertEquals(Arrays.asList("c", "a", "d", "b"), snapshotNames);
    Assert.assertEquals(Arrays.asList(null, "c", null, "c"), prevNames);
    ref.removeEventListener(testListener);
  }

  @Test
  public void updatesForUnindexedQuery()
      throws InterruptedException, ExecutionException, TestFailure, TimeoutException {
    List<DatabaseReference> refs = TestHelpers.getRandomNode(2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    final List<DataSnapshot> snapshots = new ArrayList<>();

    Map<String, Object> value = new HashMap<>();
    value.put("one", new MapBuilder().put("index", 1).put("value", "one").build());
    value.put("two", new MapBuilder().put("index", 2).put("value", "two").build());
    value.put("three", new MapBuilder().put("index", 3).put("value", "three").build());

    new WriteFuture(writer, value).timedGet();

    final Semaphore semaphore = new Semaphore(0);

    Query query = reader.orderByChild("index").limitToLast(2);
    ValueEventListener listener =
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
    writer.child("one/index").setValue(4);
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
  public void queriesWorkOnLeafNodes()
      throws DatabaseException, InterruptedException, ExecutionException, TestFailure,
          TimeoutException {
    DatabaseReference ref = TestHelpers.getRandomNode();
    final Semaphore semaphore = new Semaphore(0);
    new WriteFuture(ref, "leaf-node").timedGet();

    final List<DataSnapshot> snapshots = new ArrayList<>();
    Query query = ref.orderByChild("foo").limitToLast(1);
    ValueEventListener listener =
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
  public void serverRespectsKeyIndex()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    List<DatabaseReference> refs = TestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    Map<String, Object> initial = new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build();
    // If the server doesn't respect the index, it will send down limited data, but with no
    // offset,
    // so the expected
    // and actual data don't match
    Query query = reader.orderByKey().startAt("b").limitToFirst(2);

    List<String> expectedChildren = Arrays.asList("b", "c");

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

    Assert.assertEquals(expectedChildren, actualChildren);

    // cleanup
    reader.removeEventListener(valueListener);
  }

  @Test
  public void serverRespectsValueIndex()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure, IOException {
    List<DatabaseReference> refs = TestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final String indexRule = "{ \".indexOn\": \".value\"}";
    String rules = formatRules(writer, indexRule);

    TestHelpers.uploadRules(writer, rules);

    Map<String, Object> initial = new MapBuilder().put("a", 1).put("c", 2).put("b", 3).build();
    // If the server doesn't respect the index, it will send down limited data, but with no
    // offset,
    // so the expected and actual data don't match
    Query query = reader.orderByValue().startAt(2).limitToFirst(2);

    List<String> expectedChildren = Arrays.asList("c", "b");

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

    Assert.assertEquals(expectedChildren, actualChildren);

    // cleanup
    reader.removeEventListener(valueListener);
  }

  @Test
  public void deepUpdatesWorkWithQueries()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure, IOException {
    List<DatabaseReference> refs = TestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final String indexRule = "{ \".indexOn\": \"idx\"}";
    String rules = formatRules(writer, indexRule);

    TestHelpers.uploadRules(writer, rules);

    Map<String, Object> initial =
        new MapBuilder()
            .put("a", new MapBuilder().put("data", "foo").put("idx", true).build())
            .put("b", new MapBuilder().put("data", "bar").put("idx", true).build())
            .put("c", new MapBuilder().put("data", "baz").put("idx", false).build())
            .build();
    new WriteFuture(writer, initial).timedGet();

    Query query = reader.orderByChild("idx").equalTo(true);

    DataSnapshot snap = TestHelpers.getSnap(query);
    DeepEquals.assertEquals(
        new MapBuilder()
            .put("a", new MapBuilder().put("data", "foo").put("idx", true).build())
            .put("b", new MapBuilder().put("data", "bar").put("idx", true).build())
            .build(),
        snap.getValue());

    Map<String, Object> update =
        new MapBuilder().put("a/idx", false).put("b/data", "blah").put("c/idx", true).build();
    final Semaphore semaphore = new Semaphore(0);
    writer.updateChildren(
        update,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            assertNull(error);
            semaphore.release(1);
          }
        });
    TestHelpers.waitFor(semaphore);

    snap = TestHelpers.getSnap(query);
    DeepEquals.assertEquals(
        new MapBuilder()
            .put("b", new MapBuilder().put("data", "blah").put("idx", true).build())
            .put("c", new MapBuilder().put("data", "baz").put("idx", true).build())
            .build(),
        snap.getValue());
  }

  @Test
  public void startAtAndEndAtWorkOnValueIndex() throws Throwable {
    DatabaseReference ref = TestHelpers.getRandomNode();

    Map<String, Object> initial =
        TestHelpers.fromJsonString(
            "{"
                + "\"alex\": 60,"
                + "\"greg\": 52,"
                + "\"rob\": 56,"
                + "\"vassili\": 55.5,"
                + "\"tony\": 52"
                + "}");
    List<String> expectedOrder = Arrays.asList("tony", "vassili", "rob");
    List<String> expectedPrevNames = Arrays.asList(null, "tony", "vassili");

    Query query = ref.orderByValue().startAt(52, "tony").endAt(56);

    final List<String> valueOrder = new ArrayList<>();
    final List<String> childOrder = new ArrayList<>();
    final List<String> childPrevNames = new ArrayList<>();
    ValueEventListener valueListener =
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

    ChildEventListener testListener =
        query.addChildEventListener(
            new TestChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                childOrder.add(snapshot.getKey());
                childPrevNames.add(previousChildName);
              }
            });

    new WriteFuture(ref, initial).timedGet();

    Assert.assertEquals(expectedOrder, valueOrder);
    Assert.assertEquals(expectedOrder, childOrder);
    Assert.assertEquals(expectedPrevNames, childPrevNames);

    // cleanup
    TestHelpers.waitForRoundtrip(ref);
    ref.removeEventListener(testListener);
    ref.removeEventListener(valueListener);
  }

  @Test
  public void removingDefaultListenerRemovesNonDefaultListenWithLoadsAllData() throws Throwable {
    DatabaseReference ref = TestHelpers.getRandomNode();

    Object initialData = new MapBuilder().put("key", "value").build();
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
