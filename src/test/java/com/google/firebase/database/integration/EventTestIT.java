package com.google.firebase.database.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.EventRecord;
import com.google.firebase.database.TestFailure;
import com.google.firebase.database.TestHelpers;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.core.ZombieVerifier;
import com.google.firebase.database.core.view.Event;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.database.future.WriteFuture;
import com.google.firebase.testing.IntegrationTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class EventTestIT {

  private static FirebaseApp masterApp;

  @BeforeClass
  public static void setUpClass() throws TestFailure, TimeoutException, InterruptedException {
    masterApp = IntegrationTestUtils.initDefaultApp();
  }

  @AfterClass
  public static void tearDownClass() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }
  
  // NOTE: skipping test on valid types.

  @Test
  public void testWriteLeafNodeExpectValue() throws InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    final EventHelper readerHelper = new EventHelper().addValueExpectation(reader)
        .startListening(true);
    final EventHelper writerHelper = new EventHelper().addValueExpectation(writer)
        .startListening(true);

    ZombieVerifier.verifyRepoZombies(refs);

    writer.setValue(42);
    assertTrue(writerHelper.waitForEvents());
    assertTrue(readerHelper.waitForEvents());
    writerHelper.cleanup();
    readerHelper.cleanup();
    ZombieVerifier.verifyRepoZombies(refs);
  }

  @Test
  public void testWriteNestedLeafNodeWaitForEvents() throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;
    EventHelper helper =
        new EventHelper()
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "foo")
            .addValueExpectation(ref)
            .startListening(true);

    ZombieVerifier.verifyRepoZombies(ref);

    ref.child("foo").setValue(42);
    assertTrue(helper.waitForEvents());
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void testWriteTwoLeafNodeThenChangeThem() throws InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    final EventHelper readHelper =
        new EventHelper()
            .addValueExpectation(reader.child("foo"))
            .addChildExpectation(reader, Event.EventType.CHILD_ADDED, "foo")
            .addValueExpectation(reader)
            .addValueExpectation(reader.child("bar"))
            .addChildExpectation(reader, Event.EventType.CHILD_ADDED, "bar")
            .addValueExpectation(reader)
            .addValueExpectation(reader.child("foo"))
            .addChildExpectation(reader, Event.EventType.CHILD_CHANGED, "foo")
            .addValueExpectation(reader)
            .startListening(true);

    final EventHelper writeHelper =
        new EventHelper()
            .addValueExpectation(writer.child("foo"))
            .addChildExpectation(writer, Event.EventType.CHILD_ADDED, "foo")
            .addValueExpectation(writer)
            .addValueExpectation(writer.child("bar"))
            .addChildExpectation(writer, Event.EventType.CHILD_ADDED, "bar")
            .addValueExpectation(writer)
            .addValueExpectation(writer.child("foo"))
            .addChildExpectation(writer, Event.EventType.CHILD_CHANGED, "foo")
            .addValueExpectation(writer)
            .startListening(true);

    ZombieVerifier.verifyRepoZombies(refs);

    writer.child("foo").setValue(42);
    writer.child("bar").setValue(24);

    writer.child("foo").setValue(31415);
    assertTrue(writeHelper.waitForEvents());
    assertTrue(readHelper.waitForEvents());
    ZombieVerifier.verifyRepoZombies(refs);
  }

  @Test
  public void testSetMultipleEventListenersOnSameNode() throws InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    final EventHelper writeHelper = new EventHelper().addValueExpectation(writer)
        .startListening(true);
    final EventHelper writeHelper2 = new EventHelper().addValueExpectation(writer)
        .startListening(true);
    final EventHelper readHelper = new EventHelper().addValueExpectation(reader)
        .startListening(true);
    final EventHelper readHelper2 = new EventHelper().addValueExpectation(reader)
        .startListening(true);

    ZombieVerifier.verifyRepoZombies(refs);

    writer.setValue(42);
    assertTrue(writeHelper.waitForEvents());
    assertTrue(writeHelper2.waitForEvents());
    assertTrue(readHelper.waitForEvents());
    assertTrue(readHelper2.waitForEvents());
    ZombieVerifier.verifyRepoZombies(refs);
  }

  @Test
  public void testSetDataMultipleTimesEnsureValueIsCalledAppropriately()
      throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    ReadFuture readFuture = ReadFuture.untilEquals(ref, 2L, /*ignoreFirstNull=*/ true);
    ZombieVerifier.verifyRepoZombies(ref);

    for (int i = 0; i < 3; ++i) {
      ref.setValue(i);
    }

    List<EventRecord> events = readFuture.timedGet();
    for (long i = 0; i < 3; ++i) {
      DataSnapshot snap = events.get((int) i).getSnapshot();
      assertEquals(i, snap.getValue());
    }
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void testUnsubscribeEventsAndConfirmEventsNoLongerFire()
      throws TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    final AtomicInteger callbackCount = new AtomicInteger(0);

    final ValueEventListener listener =
        ref.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.getValue() != null) {
                  callbackCount.incrementAndGet();
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {
                fail("Should not be cancelled");
              }
            });
    ZombieVerifier.verifyRepoZombies(ref);

    for (int i = 0; i < 3; ++i) {
      ref.setValue(i);
    }

    TestHelpers.waitForRoundtrip(ref);
    ref.removeEventListener(listener);
    ZombieVerifier.verifyRepoZombies(ref);

    for (int i = 10; i < 13; ++i) {
      ref.setValue(i);
    }

    for (int i = 20; i < 22; ++i) {
      ref.setValue(i);
    }
    new WriteFuture(ref, 22).timedGet();
    assertEquals(3, callbackCount.get());
  }

  @Test
  public void testSubscribeThenUnsubscribeWithoutProblems()
      throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    ValueEventListener listener =
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {}

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        };

    ValueEventListener listenerHandle = ref.addValueEventListener(listener);
    ZombieVerifier.verifyRepoZombies(ref);
    ref.removeEventListener(listenerHandle);
    ZombieVerifier.verifyRepoZombies(ref);
    ValueEventListener listenerHandle2 = ref.addValueEventListener(listener);
    ZombieVerifier.verifyRepoZombies(ref);
    ref.removeEventListener(listenerHandle2);
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void testSubscribeThenUnsubscribeWithoutProblemsWithLimit()
      throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    ValueEventListener listener =
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {}

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        };

    ValueEventListener listenerHandle = ref.limitToLast(100).addValueEventListener(listener);
    ZombieVerifier.verifyRepoZombies(ref);
    ref.removeEventListener(listenerHandle);
    ZombieVerifier.verifyRepoZombies(ref);
    ValueEventListener listenerHandle2 = ref.limitToLast(100).addValueEventListener(listener);
    ZombieVerifier.verifyRepoZombies(ref);
    ref.removeEventListener(listenerHandle2);
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void testWriteJsonAndGetGranularEvents() throws InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    final AtomicBoolean readerSawA = new AtomicBoolean(false);
    final AtomicBoolean readerSawB = new AtomicBoolean(false);
    final AtomicBoolean readerSawB2 = new AtomicBoolean(false);
    final AtomicBoolean writerSawA = new AtomicBoolean(false);
    final AtomicBoolean writerSawB = new AtomicBoolean(false);
    final AtomicBoolean writerSawB2 = new AtomicBoolean(false);
    final Semaphore readerReady = new Semaphore(0);
    final Semaphore writerReady = new Semaphore(0);

    reader
        .child("a")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                Long val = (Long) snapshot.getValue();
                if (val != null && val == 10L) {
                  assertTrue(readerSawA.compareAndSet(false, true));
                  readerReady.release(1);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    reader
        .child("b")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                Long val = (Long) snapshot.getValue();
                if (val != null) {
                  if (val == 20L) {
                    assertTrue(readerSawB.compareAndSet(false, true));
                  } else if (val == 30L) {
                    assertTrue(readerSawB2.compareAndSet(false, true));
                  }
                  readerReady.release(1);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    writer
        .child("a")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                Long val = (Long) snapshot.getValue();
                if (val != null) {
                  assertTrue(writerSawA.compareAndSet(false, true));
                  writerReady.release(1);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    writer
        .child("b")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                Long val = (Long) snapshot.getValue();
                if (val != null) {
                  if (val == 20L) {
                    assertTrue(writerSawB.compareAndSet(false, true));
                  } else if (val == 30L) {
                    assertTrue(writerSawB2.compareAndSet(false, true));
                  }
                  writerReady.release(1);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ZombieVerifier.verifyRepoZombies(refs);

    writer.setValue(ImmutableMap.of("a", 10, "b", 20));
    TestHelpers.waitFor(writerReady, 2);
    TestHelpers.waitFor(readerReady, 2);

    writer.setValue(ImmutableMap.of("a", 10, "b", 30));
    TestHelpers.waitFor(writerReady);
    TestHelpers.waitFor(readerReady);
  }

  @Test
  public void testValueIsTriggeredForEmptyNodes()
      throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    DataSnapshot snap = new ReadFuture(ref).timedGet().get(0).getSnapshot();
    ZombieVerifier.verifyRepoZombies(ref);
    assertNull(snap.getValue());
  }

  @Test
  public void testLeafNodeTurnsIntoAnInternalNode()
      throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    final ReadFuture readFuture = ReadFuture.untilCountAfterNull(ref, 4);

    ZombieVerifier.verifyRepoZombies(ref);

    ref.setValue(42);
    ref.setValue(ImmutableMap.of("a", 2));
    ref.setValue(84);
    ref.setValue(null);

    List<EventRecord> events = readFuture.timedGet();
    ZombieVerifier.verifyRepoZombies(ref);

    assertEquals(42L, events.get(0).getSnapshot().getValue());
    assertEquals(2L, events.get(1).getSnapshot().child("a").getValue());
    assertEquals(84L, events.get(2).getSnapshot().getValue());
    assertNull(events.get(3).getSnapshot().getValue());
  }

  @Test
  public void testRegisterTheSameCallbackMultipleTimes()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    final AtomicInteger callbackCount = new AtomicInteger(0);
    ValueEventListener listener =
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            if (snapshot.getValue() != null) {
              callbackCount.incrementAndGet();
            }
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        };

    ref.addValueEventListener(listener);
    ref.addValueEventListener(listener);
    ref.addValueEventListener(listener);
    ZombieVerifier.verifyRepoZombies(ref);

    new WriteFuture(ref, 42).timedGet();
    assertEquals(3, callbackCount.get());

    ref.removeEventListener(listener);
    new WriteFuture(ref, 84).timedGet();
    assertEquals(5, callbackCount.get());
    ZombieVerifier.verifyRepoZombies(ref);

    ref.removeEventListener(listener);
    new WriteFuture(ref, 168).timedGet();
    assertEquals(6, callbackCount.get());
    ZombieVerifier.verifyRepoZombies(ref);

    ref.removeEventListener(listener);
    new WriteFuture(ref, 376).timedGet();
    assertEquals(6, callbackCount.get());
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void testUnregisterSameCallbackTooManyTimes()
      throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    ValueEventListener listener =
        ref.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                // no-op
              }

              @Override
              public void onCancelled(DatabaseError error) {
                // no-op
              }
            });

    ZombieVerifier.verifyRepoZombies(ref);

    ref.removeEventListener(listener);
    ref.removeEventListener(listener);
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void testRemovesHappenImmediately()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    final DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;
    final Semaphore blockSem = new Semaphore(0);
    final Semaphore endingSemaphore = new Semaphore(0);

    final AtomicBoolean called = new AtomicBoolean(false);
    ref.addValueEventListener(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            if (snapshot.getValue() != null) {
              assertTrue(called.compareAndSet(false, true));
              try {
                TestHelpers.waitFor(blockSem);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
              ref.removeEventListener(this);
              try {
                // this doesn't block immediately because we are already on the repo
                // thread.
                // we kick off the verify and let the unit test block on the
                // endingsemaphore
                ZombieVerifier.verifyRepoZombies(ref, endingSemaphore);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            }
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        });
    ZombieVerifier.verifyRepoZombies(ref);

    ref.setValue(42);
    TestHelpers.waitForQueue(ref);
    ref.setValue(84);
    blockSem.release();
    new WriteFuture(ref, null).timedGet();
    TestHelpers.waitFor(endingSemaphore);
  }

  @Test
  public void testRemovesHappenImmediatelyOnOuterRef()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    final DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;
    final Semaphore gotInitialEvent = new Semaphore(0);
    final Semaphore blockSem = new Semaphore(0);
    final Semaphore endingSemaphore = new Semaphore(0);

    final AtomicBoolean called = new AtomicBoolean(false);
    ref.limitToFirst(5)
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                gotInitialEvent.release();
                if (snapshot.getValue() != null) {
                  assertTrue(called.compareAndSet(false, true));
                  try {
                    TestHelpers.waitFor(blockSem);
                  } catch (InterruptedException e) {
                    e.printStackTrace();
                  }
                  ref.removeEventListener(this);
                  try {
                    // this doesn't block immediately because we are already on the
                    // repo thread.
                    // we kick off the verify and let the unit test block on the
                    // endingsemaphore
                    ZombieVerifier.verifyRepoZombies(ref, endingSemaphore);
                  } catch (InterruptedException e) {
                    e.printStackTrace();
                  }
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {
                fail("Should not be cancelled");
              }
            });
    ZombieVerifier.verifyRepoZombies(ref);

    TestHelpers.waitFor(gotInitialEvent);

    ref.child("a").setValue(42);
    TestHelpers.waitForQueue(ref);
    ref.child("b").setValue(84);
    blockSem.release();
    new WriteFuture(ref, null).timedGet();
    TestHelpers.waitFor(endingSemaphore);
  }

  @Test
  public void testRemovesHappenImmediatelyOnMultipleRef()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    final DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;
    final Semaphore gotInitialEvent = new Semaphore(0);
    final Semaphore blockSem = new Semaphore(0);
    final Semaphore endingSemaphore = new Semaphore(0);

    final AtomicBoolean called = new AtomicBoolean(false);
    ValueEventListener listener =
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            gotInitialEvent.release();
            if (snapshot.getValue() != null) {
              assertTrue(called.compareAndSet(false, true));
              try {
                TestHelpers.waitFor(blockSem);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
              ref.removeEventListener(this);
              try {
                // this doesn't block immediately because we are already on the repo
                // thread.
                // we kick off the verify and let the unit test block on the
                // endingsemaphore
                ZombieVerifier.verifyRepoZombies(ref, endingSemaphore);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            }
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        };

    ref.addValueEventListener(listener);
    ref.limitToFirst(5).addValueEventListener(listener);
    ZombieVerifier.verifyRepoZombies(ref);

    TestHelpers.waitFor(gotInitialEvent, 2);
    ref.child("a").setValue(42);
    TestHelpers.waitForQueue(ref);
    ref.child("b").setValue(84);
    blockSem.release();
    new WriteFuture(ref, null).timedGet();
    TestHelpers.waitFor(endingSemaphore);
  }

  @Test
  public void testRemovesHappenImmediatelyChild()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    final DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;
    final Semaphore blockSem = new Semaphore(0);
    final Semaphore endingSemaphore = new Semaphore(0);

    final AtomicBoolean called = new AtomicBoolean(false);
    ref.addChildEventListener(
        new ChildEventListener() {
          @Override
          public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
            if (snapshot.getValue() != null) {
              assertTrue(called.compareAndSet(false, true));
              try {
                TestHelpers.waitFor(blockSem);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
              ref.removeEventListener(this);
              try {
                // this doesn't block immediately because we are already on the repo
                // thread.
                // we kick off the verify and let the unit test block on the
                // endingsemaphore
                ZombieVerifier.verifyRepoZombies(ref, endingSemaphore);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            }
          }

          @Override
          public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}

          @Override
          public void onChildRemoved(DataSnapshot snapshot) {}

          @Override
          public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}

          @Override
          public void onCancelled(DatabaseError error) {}
        });

    ZombieVerifier.verifyRepoZombies(ref);

    ref.child("a").setValue(42);
    TestHelpers.waitForQueue(ref);
    ref.child("b").setValue(84);
    blockSem.release();
    new WriteFuture(ref, null).timedGet();
    TestHelpers.waitFor(endingSemaphore);
  }

  @Test
  public void testOnceFiresExactlyOnce()
      throws TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    final AtomicBoolean called = new AtomicBoolean(false);
    ref.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            assertTrue(called.compareAndSet(false, true));
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        });

    ZombieVerifier.verifyRepoZombies(ref);

    ref.setValue(42);
    ref.setValue(84);
    new WriteFuture(ref, null).timedGet();
    ZombieVerifier.verifyRepoZombies(ref);
  }

  // NOTE: skipped tests on testing 'once' with child events. Not supported in Java SDK

  @Test
  public void testValueOnEmptyChildFires()
      throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    DataSnapshot snap = new ReadFuture(ref.child("test")).timedGet().get(0).getSnapshot();
    assertNull(snap.getValue());
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void testValueOnEmptyChildFiresImmediately()
      throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    // Sync parent
    new ReadFuture(ref).timedGet();

    DataSnapshot snap = new ReadFuture(ref.child("test")).timedGet().get(0).getSnapshot();
    assertNull(snap.getValue());
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void testChildEventsAreRaised()
      throws TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    final Map<String, Object> firstValue =
        ImmutableMap.<String, Object>builder()
            .put("a", ImmutableMap.of(".value", "x", ".priority", 0))
            .put("b", ImmutableMap.of(".value", "x", ".priority", 1))
            .put("c", ImmutableMap.of(".value", "x", ".priority", 2))
            .put("d", ImmutableMap.of(".value", "x", ".priority", 3))
            .put("e", ImmutableMap.of(".value", "x", ".priority", 4))
            .put("f", ImmutableMap.of(".value", "x", ".priority", 5))
            .put("g", ImmutableMap.of(".value", "x", ".priority", 6))
            .put("h", ImmutableMap.of(".value", "x", ".priority", 7))
            .build();

    final Map<String, Object> secondValue =
        ImmutableMap.<String, Object>builder()
            // added
            .put("aa", ImmutableMap.of(".value", "x", ".priority", 0))
            .put("b", ImmutableMap.of(".value", "x", ".priority", 1))
            // added
            .put("bb", ImmutableMap.of(".value", "x", ".priority", 2))
            // removed c
            // changed
            .put("d", ImmutableMap.of(".value", "y", ".priority", 3))
            .put("e", ImmutableMap.of(".value", "x", ".priority", 4))
            // moved
            .put("a", ImmutableMap.of(".value", "x", ".priority", 6))
            // moved
            .put("f", ImmutableMap.of(".value", "x", ".priority", 7))
            // removed g
            // changed
            .put("h", ImmutableMap.of(".value", "y", ".priority", 7))
            .build();

    final List<String> events = new ArrayList<>();
    ref.addChildEventListener(
        new ChildEventListener() {
          @Override
          public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
            events.add("added " + snapshot.getKey());
          }

          @Override
          public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
            events.add("changed " + snapshot.getKey());
          }

          @Override
          public void onChildRemoved(DataSnapshot snapshot) {
            events.add("removed " + snapshot.getKey());
          }

          @Override
          public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
            events.add("moved " + snapshot.getKey());
          }

          @Override
          public void onCancelled(DatabaseError error) {}
        });
    new WriteFuture(ref, firstValue).timedGet();
    events.clear();
    new WriteFuture(ref, secondValue).timedGet();

    List<String> expected =
        Arrays.asList(
            "removed c",
            "removed g",
            "added aa",
            "added bb",
            "moved a",
            "moved f",
            "changed d",
            "changed a",
            "changed f",
            "changed h");
    String expectedString = expected.toString();
    String actualString = events.toString();
    assertEquals(expectedString, actualString);
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void testChildEventsAreRaisedWithAQuery()
      throws TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    final Map<String, Object> firstValue =
        ImmutableMap.<String, Object>builder()
            .put("a", ImmutableMap.of(".value", "x", ".priority", 0))
            .put("b", ImmutableMap.of(".value", "x", ".priority", 1))
            .put("c", ImmutableMap.of(".value", "x", ".priority", 2))
            .put("d", ImmutableMap.of(".value", "x", ".priority", 3))
            .put("e", ImmutableMap.of(".value", "x", ".priority", 4))
            .put("f", ImmutableMap.of(".value", "x", ".priority", 5))
            .put("g", ImmutableMap.of(".value", "x", ".priority", 6))
            .put("h", ImmutableMap.of(".value", "x", ".priority", 7))
            .build();

    final Map<String, Object> secondValue =
        ImmutableMap.<String, Object>builder()
            // added
            .put("aa", ImmutableMap.of(".value", "x", ".priority", 0))
            .put("b", ImmutableMap.of(".value", "x", ".priority", 1))
            // added
            .put("bb", ImmutableMap.of(".value", "x", ".priority", 2))
            // removed c
            // changed
            .put("d", ImmutableMap.of(".value", "y", ".priority", 3))
            .put("e", ImmutableMap.of(".value", "x", ".priority", 4))
            // moved
            .put("a", ImmutableMap.of(".value", "x", ".priority", 6))
            // moved
            .put("f", ImmutableMap.of(".value", "x", ".priority", 7))
            // removed g
            // changed
            .put("h", ImmutableMap.of(".value", "y", ".priority", 7))
            .build();

    final List<String> events = new ArrayList<>();
    ref.limitToLast(10)
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                events.add("added " + snapshot.getKey());
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                events.add("changed " + snapshot.getKey());
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                events.add("removed " + snapshot.getKey());
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                events.add("moved " + snapshot.getKey());
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });
    new WriteFuture(ref, firstValue).timedGet();
    events.clear();
    new WriteFuture(ref, secondValue).timedGet();

    List<String> expected =
        Arrays.asList(
            "removed c",
            "removed g",
            "added aa",
            "added bb",
            "moved a",
            "moved f",
            "changed d",
            "changed a",
            "changed f",
            "changed h");
    String expectedString = expected.toString();
    String actualString = events.toString();
    assertEquals(expectedString, actualString);
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void testPriorityChange()
      throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    final EventHelper helper =
        new EventHelper()
            .addValueExpectation(ref.child("bar"))
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "bar")
            .addValueExpectation(ref)
            .addValueExpectation(ref.child("foo"))
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "foo")
            .addValueExpectation(ref)
            .startListening(true);

    ref.child("bar").setValue(42, 10);
    TestHelpers.waitForRoundtrip(ref);
    ref.child("foo").setValue(42, 20);

    assertTrue(helper.waitForEvents());
    helper
        .addValueExpectation(ref.child("bar"))
        .addChildExpectation(ref, Event.EventType.CHILD_MOVED, "bar")
        .addChildExpectation(ref, Event.EventType.CHILD_CHANGED, "bar")
        .addValueExpectation(ref)
        .startListening();

    ref.child("bar").setPriority(30);
    assertTrue(helper.waitForEvents());
    helper.cleanup();
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void testPriorityChange2()
      throws InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp) ;

    EventHelper helper =
        new EventHelper()
            .addValueExpectation(ref.child("bar"))
            .addValueExpectation(ref.child("foo"))
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "bar")
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "foo")
            .addValueExpectation(ref)
            .startListening(true);

    ZombieVerifier.verifyRepoZombies(ref);
    ref.setValue(
        ImmutableMap.of(
            "bar", ImmutableMap.of(".value", 42, ".priority", 10),
            "foo", ImmutableMap.of(".value", 42, ".priority", 20)));
    assertTrue(helper.waitForEvents());
    helper
        .addValueExpectation(ref.child("bar"))
        .addChildExpectation(ref, Event.EventType.CHILD_MOVED, "bar")
        .addChildExpectation(ref, Event.EventType.CHILD_CHANGED, "bar")
        .addValueExpectation(ref)
        .startListening();

    ZombieVerifier.verifyRepoZombies(ref);
    ref.setValue(
        ImmutableMap.of(
            "foo", ImmutableMap.of(".value", 42, ".priority", 20),
            "bar", ImmutableMap.of(".value", 42, ".priority", 30)));
    assertTrue(helper.waitForEvents());
    helper.cleanup();
    ZombieVerifier.verifyRepoZombies(ref);
  }
}
