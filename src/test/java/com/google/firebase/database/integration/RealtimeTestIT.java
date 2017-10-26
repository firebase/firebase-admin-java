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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.EventRecord;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.TestFailure;
import com.google.firebase.database.TestHelpers;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.RepoManager;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.database.future.WriteFuture;
import com.google.firebase.database.utilities.ParsedUrl;
import com.google.firebase.database.utilities.Utilities;
import com.google.firebase.testing.IntegrationTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class RealtimeTestIT {

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
  public void testUrlParsing() {
    ParsedUrl parsed = Utilities.parseUrl("https://admin-java-sdk.firebaseio.com");
    assertEquals("/", parsed.path.toString());
    assertEquals("admin-java-sdk.firebaseio.com", parsed.repoInfo.host);
    assertEquals("admin-java-sdk.firebaseio.com", parsed.repoInfo.internalHost);
    assertEquals(true, parsed.repoInfo.secure);

    parsed = Utilities.parseUrl("https://admin-java-sdk.firebaseio.com/foo/bar");
    assertEquals("/foo/bar", parsed.path.toString());
    assertEquals("admin-java-sdk.firebaseio.com", parsed.repoInfo.host);
    assertEquals("admin-java-sdk.firebaseio.com", parsed.repoInfo.internalHost);
    assertEquals(true, parsed.repoInfo.secure);
  }

  @Test
  public void testOnDisconnectSetWorks()
      throws TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    final DatabaseConfig ctx = TestHelpers.getDatabaseConfig(masterApp);
    RepoManager.resume(ctx);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final ReadFuture writerFuture = new ReadFuture(writer.child("disconnected"),
        new ReadFuture.CompletionCondition() {
          @Override
          public boolean isComplete(List<EventRecord> events) {
            valSemaphore.release();
            Object snap = events.get(events.size() - 1).getSnapshot().getValue();
            return snap != null;
          }
        });

    final ReadFuture readerFuture = new ReadFuture(reader.child("disconnected"),
        new ReadFuture.CompletionCondition() {
          @Override
          public boolean isComplete(List<EventRecord> events) {
            valSemaphore.release();
            Object snap = events.get(events.size() - 1).getSnapshot().getValue();
            return snap != null;
          }
        });

    // Wait for initial (null) value on both reader and writer.
    TestHelpers.waitFor(valSemaphore, 2);

    Object expected = "dummy";
    writer.child("disconnected").onDisconnect().setValue(expected,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            RepoManager.interrupt(ctx);
            opSemaphore.release();
          }
        });
    TestHelpers.waitFor(opSemaphore);

    EventRecord writerEventRecord = writerFuture.timedGet().get(1);
    EventRecord readerEventRecord = readerFuture.timedGet().get(1);

    RepoManager.resume(ctx);

    TestHelpers.assertDeepEquals(expected, writerEventRecord.getSnapshot().getValue());
    TestHelpers.assertDeepEquals(expected, readerEventRecord.getSnapshot().getValue());
  }

  @Test
  public void testOnDisconnectSetWithPriorityWorks()
      throws TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    final DatabaseConfig ctx = TestHelpers.getDatabaseConfig(masterApp);
    RepoManager.resume(ctx);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final ReadFuture writerFuture = new ReadFuture(writer.child("disconnected"),
        new ReadFuture.CompletionCondition() {
          @Override
          public boolean isComplete(List<EventRecord> events) {
            Object snap = events.get(events.size() - 1).getSnapshot().getValue();
            valSemaphore.release();
            return snap != null;
          }
        });

    final ReadFuture readerFuture = new ReadFuture(reader.child("disconnected"),
        new ReadFuture.CompletionCondition() {
          @Override
          public boolean isComplete(List<EventRecord> events) {
            Object snap = events.get(events.size() - 1).getSnapshot().getValue();
            valSemaphore.release();
            return snap != null;
          }
        });

    // Wait for initial (null) value on both reader and writer.
    TestHelpers.waitFor(valSemaphore, 2);

    String expectedPriority = "12345";
    writer.child("disconnected").onDisconnect().setValue(true, expectedPriority,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            RepoManager.interrupt(ctx);
            opSemaphore.release();
          }
        });
    TestHelpers.waitFor(opSemaphore);

    EventRecord writerEventRecord = writerFuture.timedGet().get(1);
    final EventRecord readerEventRecord = readerFuture.timedGet().get(1);

    RepoManager.resume(ctx);

    TestHelpers.assertDeepEquals(true, writerEventRecord.getSnapshot().getValue());
    TestHelpers.assertDeepEquals(expectedPriority, writerEventRecord.getSnapshot().getPriority());
    TestHelpers.assertDeepEquals(true, readerEventRecord.getSnapshot().getValue());
    TestHelpers.assertDeepEquals(expectedPriority, readerEventRecord.getSnapshot().getPriority());
  }

  @Test
  public void testOnDisconnectRemoveWorks()
      throws TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    final DatabaseConfig ctx = TestHelpers.getDatabaseConfig(masterApp);
    RepoManager.resume(ctx);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final ReadFuture writerFuture = new ReadFuture(writer.child("foo"),
        new ReadFuture.CompletionCondition() {
          @Override
          public boolean isComplete(List<EventRecord> events) {
            valSemaphore.release();
            return events.size() == 3;
          }
        });

    final ReadFuture readerFuture = new ReadFuture(reader.child("foo"),
        new ReadFuture.CompletionCondition() {
          @Override
          public boolean isComplete(List<EventRecord> events) {
            valSemaphore.release();
            return events.size() == 3;
          }
        });

    // Wait for initial (null) value on both reader and writer.
    TestHelpers.waitFor(valSemaphore, 2);

    writer.child("foo").setValue("bar", new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        opSemaphore.release();
      }
    });
    TestHelpers.waitFor(opSemaphore);

    writer.child("foo").onDisconnect().removeValue(new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        RepoManager.interrupt(ctx);
        opSemaphore.release();
      }
    });
    TestHelpers.waitFor(opSemaphore);

    EventRecord writerEventRecord = writerFuture.timedGet().get(2);
    EventRecord readerEventRecord = readerFuture.timedGet().get(2);

    RepoManager.resume(ctx);

    TestHelpers.assertDeepEquals(null, writerEventRecord.getSnapshot().getValue());
    TestHelpers.assertDeepEquals(null, readerEventRecord.getSnapshot().getValue());
  }

  @Test
  public void testOnDisconnectUpdateWorks()
      throws TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    final DatabaseConfig ctx = TestHelpers.getDatabaseConfig(masterApp);
    RepoManager.resume(ctx);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final ReadFuture writerFuture = new ReadFuture(writer.child("foo"),
        new ReadFuture.CompletionCondition() {
          @Override
          public boolean isComplete(List<EventRecord> events) {
            valSemaphore.release();
            return events.size() == 4;
          }
        });

    final ReadFuture readerFuture = new ReadFuture(reader.child("foo"),
        new ReadFuture.CompletionCondition() {
          @Override
          public boolean isComplete(List<EventRecord> events) {
            valSemaphore.release();
            return events.size() == 4;
          }
        });

    // Wait for initial (null) value on both reader and writer.
    TestHelpers.waitFor(valSemaphore, 2);

    Map<String, Object> initialValues = new MapBuilder().put("bar", "a").put("baz", "b").build();
    writer.child("foo").setValue(initialValues, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        opSemaphore.release();
      }
    });
    TestHelpers.waitFor(opSemaphore);

    Map<String, Object> updatedValues = new MapBuilder().put("baz", "c").put("bat", "d").build();
    writer.child("foo").onDisconnect().updateChildren(updatedValues,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            RepoManager.interrupt(ctx);
            opSemaphore.release();
          }
        });
    TestHelpers.waitFor(opSemaphore);

    EventRecord writerEventRecord = writerFuture.timedGet().get(3);
    EventRecord readerEventRecord = readerFuture.timedGet().get(3);

    RepoManager.resume(ctx);

    Map<String, Object> expected = new MapBuilder().put("bar", "a").put("baz", "c").put("bat", "d")
        .build();

    TestHelpers.assertDeepEquals(expected, writerEventRecord.getSnapshot().getValue());
    TestHelpers.assertDeepEquals(expected, readerEventRecord.getSnapshot().getValue());
  }

  @Test
  public void testOnDisconnectTriggersSingleLocalValueEventForWriter()
      throws TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 1);
    DatabaseReference writer = refs.get(0);
    final DatabaseConfig ctx = TestHelpers.getDatabaseConfig(masterApp);
    RepoManager.resume(ctx);

    final AtomicInteger callbackCount = new AtomicInteger(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final ReadFuture writerFuture = new ReadFuture(writer, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        callbackCount.incrementAndGet();
        valSemaphore.release(1);
        return events.size() == 2;
      }
    });
    TestHelpers.waitFor(valSemaphore);

    final Semaphore opSemaphore = new Semaphore(0);
    writer.child("foo").onDisconnect().setValue(
        new MapBuilder().put("bar", "a").put("baz", "b").build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release(1);
          }
        });
    TestHelpers.waitFor(opSemaphore);

    writer.child("foo").onDisconnect().updateChildren(new MapBuilder().put("bam", "c").build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release(1);
          }
        });
    TestHelpers.waitFor(opSemaphore);

    writer.child("foo/baz").onDisconnect().removeValue(new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        opSemaphore.release(1);
      }
    });
    TestHelpers.waitFor(opSemaphore);

    RepoManager.interrupt(ctx);

    TestHelpers.waitFor(valSemaphore);
    EventRecord writerEventRecord = writerFuture.timedGet().get(1);

    RepoManager.resume(ctx);

    Map<String, Object> expected = new MapBuilder()
        .put("foo", new MapBuilder().put("bam", "c").put("bar", "a").build()).build();
    TestHelpers.assertDeepEquals(expected, writerEventRecord.getSnapshot().getValue());
    assertTrue(callbackCount.get() == 2);
  }

  @Test
  public void testOnDisconnectTriggersSingleLocalValueEventForReader()
      throws TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    final DatabaseConfig ctx = TestHelpers.getDatabaseConfig(masterApp);
    RepoManager.resume(ctx);

    final AtomicInteger callbackCount = new AtomicInteger(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final ReadFuture readerFuture = new ReadFuture(reader, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        callbackCount.incrementAndGet();
        valSemaphore.release(1);
        return events.size() == 2;
      }
    });
    TestHelpers.waitFor(valSemaphore);

    final Semaphore opSemaphore = new Semaphore(0);
    writer.child("foo").onDisconnect().setValue(
        new MapBuilder().put("bar", "a").put("baz", "b").build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release(1);
          }
        });
    TestHelpers.waitFor(opSemaphore);

    writer.child("foo").onDisconnect().updateChildren(new MapBuilder().put("bam", "c").build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release(1);
          }
        });
    TestHelpers.waitFor(opSemaphore);

    writer.child("foo/baz").onDisconnect().removeValue(new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        opSemaphore.release(1);
      }
    });
    TestHelpers.waitFor(opSemaphore);

    RepoManager.interrupt(ctx);

    TestHelpers.waitFor(valSemaphore);
    EventRecord readerEventRecord = readerFuture.timedGet().get(1);

    RepoManager.resume(ctx);

    Map<String, Object> expected = new MapBuilder()
        .put("foo", new MapBuilder().put("bam", "c").put("bar", "a").build()).build();
    TestHelpers.assertDeepEquals(expected, readerEventRecord.getSnapshot().getValue());
    assertTrue(callbackCount.get() == 2);
  }

  @Test
  public void testOnDisconnectTriggersSingleLocalValueEventForWriterWithQuery()
      throws TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 1);
    DatabaseReference writer = refs.get(0);
    final DatabaseConfig ctx = TestHelpers.getDatabaseConfig(masterApp);
    RepoManager.resume(ctx);

    final AtomicInteger callbackCount = new AtomicInteger(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final ReadFuture writerFuture = new ReadFuture(writer, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        callbackCount.incrementAndGet();
        valSemaphore.release(1);
        return events.size() == 2;
      }
    });
    TestHelpers.waitFor(valSemaphore);

    final Semaphore opSemaphore = new Semaphore(0);
    writer.child("foo").onDisconnect().setValue(
        new MapBuilder().put("bar", "a").put("baz", "b").build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release(1);
          }
        });
    TestHelpers.waitFor(opSemaphore);

    writer.child("foo").onDisconnect().updateChildren(new MapBuilder().put("bam", "c").build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release(1);
          }
        });
    TestHelpers.waitFor(opSemaphore);

    writer.child("foo/baz").onDisconnect().removeValue(new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        opSemaphore.release(1);
      }
    });
    TestHelpers.waitFor(opSemaphore);

    RepoManager.interrupt(ctx);

    TestHelpers.waitFor(valSemaphore);
    EventRecord writerEventRecord = writerFuture.timedGet().get(1);

    RepoManager.resume(ctx);

    Map<String, Object> expected = new MapBuilder()
        .put("foo", new MapBuilder().put("bam", "c").put("bar", "a").build()).build();
    TestHelpers.assertDeepEquals(expected, writerEventRecord.getSnapshot().getValue());
    assertTrue(callbackCount.get() == 2);
  }

  @Test
  public void testOnDisconnectTriggersSingleLocalValueEventForReaderWithQuery()
      throws TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    final DatabaseConfig ctx = TestHelpers.getDatabaseConfig(masterApp);

    final AtomicInteger callbackCount = new AtomicInteger(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final ReadFuture readerFuture = new ReadFuture(reader, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        callbackCount.incrementAndGet();
        valSemaphore.release(1);
        return events.size() == 2;
      }
    });
    TestHelpers.waitFor(valSemaphore);

    final Semaphore opSemaphore = new Semaphore(0);
    writer.child("foo").onDisconnect().setValue(
        new MapBuilder().put("bar", "a").put("baz", "b").build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release(1);
          }
        });
    TestHelpers.waitFor(opSemaphore);

    writer.child("foo").onDisconnect().updateChildren(new MapBuilder().put("bam", "c").build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release(1);
          }
        });
    TestHelpers.waitFor(opSemaphore);

    writer.child("foo/baz").onDisconnect().removeValue(new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        opSemaphore.release(1);
      }
    });
    TestHelpers.waitFor(opSemaphore);

    RepoManager.interrupt(ctx);

    TestHelpers.waitFor(valSemaphore);
    EventRecord readerEventRecord = readerFuture.timedGet().get(1);

    RepoManager.resume(ctx);

    Map<String, Object> expected = new MapBuilder()
        .put("foo", new MapBuilder().put("bam", "c").put("bar", "a").build()).build();
    TestHelpers.assertDeepEquals(expected, readerEventRecord.getSnapshot().getValue());
    assertTrue(callbackCount.get() == 2);
  }

  @Test
  public void testOnDisconnectDeepMergeTriggersOnlyOneValueEventForReaderWithQuery()
      throws TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    final DatabaseConfig ctx = TestHelpers.getDatabaseConfig(masterApp);

    final AtomicInteger callbackCount = new AtomicInteger(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final ReadFuture readerFuture = new ReadFuture(reader, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        callbackCount.incrementAndGet();
        valSemaphore.release(1);
        return events.size() == 4;
      }
    });
    TestHelpers.waitFor(valSemaphore);

    final Semaphore opSemaphore = new Semaphore(0);
    Map<String, Object> initialValues = new MapBuilder().put("a", 1)
        .put("b", new MapBuilder().put("c", true).put("d", "scalar")
            .put("e", new MapBuilder().put("f", "hooray").build()).build())
        .build();
    writer.setValue(initialValues, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        opSemaphore.release(1);
      }
    });
    TestHelpers.waitFor(valSemaphore);
    TestHelpers.waitFor(opSemaphore);

    writer.child("b/c").onDisconnect().setValue(false, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        opSemaphore.release(1);
      }
    });
    TestHelpers.waitFor(opSemaphore);

    writer.child("b/d").onDisconnect().removeValue(new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        opSemaphore.release(1);
      }
    });
    TestHelpers.waitFor(opSemaphore);

    RepoManager.interrupt(ctx);

    TestHelpers.waitFor(valSemaphore);
    EventRecord readerEventRecord = readerFuture.timedGet().get(3);

    RepoManager.resume(ctx);

    Map<String, Object> expected = new MapBuilder().put("a", 1L).put("b", new MapBuilder()
        .put("c", false).put("e", new MapBuilder().put("f", "hooray").build()).build()).build();
    TestHelpers.assertDeepEquals(expected, readerEventRecord.getSnapshot().getValue());
    assertEquals(4, callbackCount.get());
  }

  @Test
  public void testOnDisconnectCancelWorks()
      throws TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    final DatabaseConfig ctx = TestHelpers.getDatabaseConfig(masterApp);
    RepoManager.resume(ctx);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final ReadFuture writerFuture = new ReadFuture(writer.child("foo"),
        new ReadFuture.CompletionCondition() {
          @Override
          public boolean isComplete(List<EventRecord> events) {
            valSemaphore.release();
            return events.size() == 3;
          }
        });

    final ReadFuture readerFuture = new ReadFuture(reader.child("foo"),
        new ReadFuture.CompletionCondition() {
          @Override
          public boolean isComplete(List<EventRecord> events) {
            valSemaphore.release();
            return events.size() == 3;
          }
        });

    // Wait for initial (null) value on both reader and writer.
    TestHelpers.waitFor(valSemaphore, 2);

    Map<String, Object> initialValues = new MapBuilder().put("bar", "a").put("baz", "b").build();
    writer.child("foo").setValue(initialValues, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        opSemaphore.release();
      }
    });
    TestHelpers.waitFor(opSemaphore);

    Map<String, Object> updatedValues = new MapBuilder().put("baz", "c").put("bat", "d").build();
    writer.child("foo").onDisconnect().updateChildren(updatedValues,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release();
          }
        });
    TestHelpers.waitFor(opSemaphore);

    writer.child("foo/bat").onDisconnect().cancel(new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        RepoManager.interrupt(ctx);
        opSemaphore.release();
      }
    });
    TestHelpers.waitFor(opSemaphore);

    EventRecord writerEventRecord = writerFuture.timedGet().get(2);
    EventRecord readerEventRecord = readerFuture.timedGet().get(2);

    RepoManager.resume(ctx);

    Map<String, Object> expected = new MapBuilder().put("bar", "a").put("baz", "c").build();

    TestHelpers.assertDeepEquals(expected, writerEventRecord.getSnapshot().getValue());
    TestHelpers.assertDeepEquals(expected, readerEventRecord.getSnapshot().getValue());
  }

  @Test
  public void testOnDisconnectWithServerValuesWorks()
      throws TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 1);
    DatabaseReference writer = refs.get(0);
    final DatabaseConfig ctx = TestHelpers.getDatabaseConfig(masterApp);
    RepoManager.resume(ctx);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final ReadFuture writerFuture = new ReadFuture(writer, new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        valSemaphore.release();
        Object snapVal = events.get(events.size() - 1).getSnapshot().getValue();
        return snapVal != null;
      }
    });

    // Wait for initial (null) value.
    TestHelpers.waitFor(valSemaphore);

    Map<String, Object> initialValues = new MapBuilder()
        .put("a", ServerValue.TIMESTAMP).put("b", new MapBuilder()
            .put(".value", ServerValue.TIMESTAMP).put(".priority", ServerValue.TIMESTAMP).build())
        .build();

    writer.onDisconnect().setValue(initialValues, ServerValue.TIMESTAMP,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            RepoManager.interrupt(ctx);
            opSemaphore.release();
          }
        });
    TestHelpers.waitFor(opSemaphore);

    EventRecord readerEventRecord = writerFuture.timedGet().get(1);
    DataSnapshot snap = readerEventRecord.getSnapshot();

    RepoManager.resume(ctx);

    assertEquals(snap.child("a").getValue().getClass(), Long.class);
    assertEquals(snap.getPriority().getClass(), Double.class);
    assertEquals(snap.getPriority(), snap.child("b").getPriority());
    assertEquals(snap.child("a").getValue(), snap.child("b").getValue());
    TestHelpers.assertTimeDelta(Long.parseLong(snap.child("a").getValue().toString()));
  }

  // TODO: Find better way to test shutdown behavior. This test is not worth a
  // 13-second pause (6
  // second sleep and then 7 second timeout via timedGet())!
  @Test
  @Ignore
  public void testShutdown()
      throws InterruptedException, ExecutionException, TestFailure, TimeoutException {
    DatabaseConfig config = TestHelpers.getDatabaseConfig(masterApp);

    // Shut it down right away
    RepoManager.interrupt(config);
    Thread.sleep(6 * 1000); // Long enough for all of the threads to exit
    assertTrue(config.isStopped());

    // Test that we can use an existing ref
    DatabaseReference ref = FirebaseDatabase.getInstance().getReference();
    DatabaseReference pushed = ref.push();
    try {
      new WriteFuture(pushed, "foo").timedGet();
      fail("Should time out, we're offline");
    } catch (TimeoutException t) {
      // Expected, we're offline
    }
    assertFalse(config.isStopped());
    RepoManager.resume(config);

    final Semaphore ready = new Semaphore(0);
    ref.child(".info/connected").addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        Boolean connected = snapshot.getValue(Boolean.class);
        if (connected) {
          ready.release(1);
        }
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });

    // Wait for us to be connected so we send the buffered put
    TestHelpers.waitFor(ready);

    DataSnapshot snap = TestHelpers.getSnap(pushed);
    assertEquals("foo", snap.getValue(String.class));
  }

  @Test
  public void testWritesToSameLocationWhileOfflineAreInOrder()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    DatabaseReference.goOffline();
    for (int i = 0; i < 100; i++) {
      ref.setValueAsync(i);
    }
    // This should be the last write and the actual value
    WriteFuture future = new WriteFuture(ref, 100);
    DatabaseReference.goOnline();
    future.timedGet();

    final Semaphore semaphore = new Semaphore(0);
    ref.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        assertEquals(100L, snapshot.getValue());
        semaphore.release();
      }

      @Override
      public void onCancelled(DatabaseError error) {
        fail("Shouldn't be cancelled");
      }
    });

    TestHelpers.waitFor(semaphore);
  }

  @Test
  public void testOnDisconnectIsNotRerunOnReconnect()
      throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    final DatabaseConfig ctx = TestHelpers.getDatabaseConfig(masterApp);
    RepoManager.resume(ctx);
    // Wait for the initialization to complete
    TestHelpers.waitForRoundtrip(ref);

    final Semaphore semaphore = new Semaphore(0);

    // Will ensure that the operation is queued
    RepoManager.interrupt(ctx);

    final int[] counter = new int[1];

    ref.child("disconnected").onDisconnect().setValue(true,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            assertNull(error);
            semaphore.release();
            counter[0]++;
          }
        });

    // Will trigger sending the onDisconnect
    RepoManager.resume(ctx);

    // Should be complete initially
    TestHelpers.waitFor(semaphore);
    // One onComplete called
    assertEquals(1, counter[0]);

    // Will trigger a reconnect
    RepoManager.interrupt(ctx);
    RepoManager.resume(ctx);

    // Make sure we sent all outstanding onDisconnects
    TestHelpers.waitForRoundtrip(ref);
    // Two are needed because writes are restored first, then onDisconnects
    TestHelpers.waitForRoundtrip(ref); 
    assertEquals(1, counter[0]); // No onComplete should have triggered
  }
}
