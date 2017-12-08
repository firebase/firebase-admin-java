package com.google.firebase.database.core;

import static com.google.firebase.database.TestHelpers.waitFor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.TestHelpers;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.connection.HostInfo;
import com.google.firebase.database.connection.PersistentConnection;
import com.google.firebase.database.connection.RangeMerge;
import com.google.firebase.database.connection.RequestResultCallback;
import com.google.firebase.database.core.view.QueryParams;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;
import com.google.firebase.database.snapshot.ValueIndex;
import com.google.firebase.testing.ServiceAccount;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class RepoTest {

  private static final List<String> SUCCESS = ImmutableList.of("success");
  private static final List<String> FAILURE = ImmutableList.of("failure");

  private static DatabaseConfig config;
  private static PersistentConnection connection;

  @BeforeClass
  public static void setUpClass() throws IOException {
    FirebaseApp testApp = FirebaseApp.initializeApp(
        new FirebaseOptions.Builder()
            .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
            .setDatabaseUrl("https://admin-java-sdk.firebaseio.com")
            .build());
    config = Mockito.spy(TestHelpers.newFrozenTestConfig(testApp));

    Mockito.when(config.getEventTarget()).thenReturn(new EventTarget() {
      @Override
      public void postEvent(Runnable r) {
        r.run();
      }

      @Override
      public void shutdown() {
      }

      @Override
      public void restart() {
      }
    });

    connection = mockConnection();
    Mockito.when(config.newPersistentConnection(
        Mockito.any(HostInfo.class), Mockito.any(PersistentConnection.Delegate.class)))
        .thenReturn(connection);
    Mockito.doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        RequestResultCallback callback = invocation.getArgument(3);
        callback.onRequestResult(null, null);
        return null;
      }
    })
        .when(connection).compareAndPut(
        Mockito.eq(ImmutableList.of("txn")),
        Mockito.any(),
        Mockito.anyString(),
        Mockito.any(RequestResultCallback.class));
  }

  @AfterClass
  public static void tearDownClass() throws InterruptedException {
    config.stop();
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testDataUpdate() throws InterruptedException {
    final Repo repo = newRepo();
    final List<DataSnapshot> events = new ArrayList<>();
    QuerySpec spec = new QuerySpec(new Path("/foo"), QueryParams.DEFAULT_PARAMS);
    addCallback(
        repo, new ValueEventRegistration(repo, newValueEventListener(events), spec));

    final Semaphore semaphore = new Semaphore(0);
    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.onDataUpdate(ImmutableList.of("foo"), "testData", false, null);
        semaphore.release();
      }
    });
    waitFor(semaphore);
    assertEquals(1, events.size());
    assertNotNull(events.get(0));
    assertEquals("testData", events.get(0).getValue(String.class));

    final Map<String, String> update = ImmutableMap.of("key1", "value1");
    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.onDataUpdate(ImmutableList.of("foo"), update, true, null);
        semaphore.release();
      }
    });
    waitFor(semaphore);
    assertEquals(2, events.size());
    assertNotNull(events.get(1));
    assertEquals(update, events.get(1).getValue());
  }

  @Test
  public void testDataUpdateForQuery() throws InterruptedException {
    final Repo repo = newRepo();
    final List<DataSnapshot> events = new ArrayList<>();
    QuerySpec spec = new QuerySpec(new Path("/bar"),
        QueryParams.DEFAULT_PARAMS.orderBy(ValueIndex.getInstance()).limitToFirst(10));
    addCallback(repo, new ValueEventRegistration(repo, newValueEventListener(events), spec));

    final Semaphore semaphore = new Semaphore(0);
    final Map<String, String> update = ImmutableMap.of("key1", "value1");

    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.onDataUpdate(ImmutableList.of("bar"), update, false, 1L);
        semaphore.release();
      }
    });
    waitFor(semaphore);
    assertEquals(1, events.size());
    assertNotNull(events.get(0));
    assertEquals(update, events.get(0).getValue());

    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.onDataUpdate(ImmutableList.of("bar"), ImmutableMap.of("key2", "value2"), true, 1L);
        semaphore.release();
      }
    });
    waitFor(semaphore);
    assertEquals(2, events.size());
    assertNotNull(events.get(1));
    assertEquals(ImmutableMap.of("key1", "value1", "key2", "value2"), events.get(1).getValue());
  }

  @Test
  public void testRangeMergeUpdate() throws InterruptedException {
    final Repo repo = newRepo();
    final List<DataSnapshot> events = new ArrayList<>();
    QuerySpec spec = new QuerySpec(new Path("/rangeMerge"), QueryParams.DEFAULT_PARAMS);
    addCallback(repo, new ValueEventRegistration(repo, newValueEventListener(events), spec));

    final Semaphore semaphore = new Semaphore(0);
    final RangeMerge merge = new RangeMerge(ImmutableList.of("p1"),
        ImmutableList.of("p5"), ImmutableMap.of("p2", "v2", "p3", "v3"));
    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.onRangeMergeUpdate(
            ImmutableList.of("rangeMerge"), ImmutableList.of(merge), null);
        semaphore.release();
      }
    });
    waitFor(semaphore);
    assertEquals(1, events.size());
    assertNotNull(events.get(0));
    assertEquals(ImmutableMap.of("p2", "v2", "p3", "v3"), events.get(0).getValue());
  }

  @Test
  public void testRemoveCallback() throws InterruptedException {
    final Repo repo = newRepo();
    final List<DataSnapshot> events = new ArrayList<>();
    QuerySpec spec = new QuerySpec(new Path("/foo"), QueryParams.DEFAULT_PARAMS);
    final ValueEventRegistration callback = new ValueEventRegistration(repo,
        newValueEventListener(events), spec);
    addCallback(repo, callback);

    final Semaphore semaphore = new Semaphore(0);
    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.onDataUpdate(ImmutableList.of("foo"), "testData", false, null);
        semaphore.release();
      }
    });
    waitFor(semaphore);
    assertEquals(1, events.size());
    assertNotNull(events.get(0));
    assertEquals("testData", events.get(0).getValue(String.class));

    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.removeEventCallback(callback);
        semaphore.release();
      }
    });
    waitFor(semaphore);

    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.onDataUpdate(ImmutableList.of("foo"), "testData2", false, null);
        semaphore.release();
      }
    });
    waitFor(semaphore);
    assertEquals(1, events.size());
  }

  @Test
  public void testSetValue() throws InterruptedException {
    final Repo repo = newRepo();
    final List<DataSnapshot> events = new ArrayList<>();
    final Path path = new Path("/foo");
    QuerySpec spec = new QuerySpec(path, QueryParams.DEFAULT_PARAMS);
    addCallback(repo, new ValueEventRegistration(repo, newValueEventListener(events), spec));

    final Semaphore semaphore = new Semaphore(0);
    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.setValue(path, NodeUtilities.NodeFromJSON("setValue"), null);
        semaphore.release();
      }
    });
    waitFor(semaphore);
    assertEquals(1, events.size());
    assertNotNull(events.get(0));
    assertEquals("setValue", events.get(0).getValue(String.class));
  }

  @Test
  public void testUpdateChildren() throws InterruptedException {
    final Repo repo = newRepo();
    final List<DataSnapshot> events = new ArrayList<>();
    final Path path = new Path("/child");
    QuerySpec spec = new QuerySpec(path, QueryParams.DEFAULT_PARAMS);
    ChildEventRegistration callback = new ChildEventRegistration(repo, new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        events.add(snapshot);
      }

      @Override
      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
        events.add(snapshot);
      }

      @Override
      public void onChildRemoved(DataSnapshot snapshot) {
        events.add(snapshot);
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        events.add(snapshot);
      }

      @Override
      public void onCancelled(DatabaseError error) {
        fail(error.getMessage());
      }
    }, spec);
    addCallback(repo, callback);

    final Semaphore semaphore = new Semaphore(0);
    final Map<String, Object> update1 = ImmutableMap.<String, Object>of("key1", "value1");
    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.updateChildren(path, CompoundWrite.fromValue(update1), null, update1);
        semaphore.release();
      }
    });
    waitFor(semaphore);
    assertEquals(1, events.size());
    assertNotNull(events.get(0));
    assertEquals("key1", events.get(0).getKey());
    assertEquals("value1", events.get(0).getValue(String.class));

    final Map<String, Object> update2 = ImmutableMap.of();
    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.updateChildren(path, CompoundWrite.fromValue(update2), null, update2);
        semaphore.release();
      }
    });
    waitFor(semaphore);
    assertEquals(1, events.size());
  }

  @Test
  public void testTransaction() throws InterruptedException {
    final Repo repo = newRepo();
    final List<DataSnapshot> events = new ArrayList<>();
    final Path path = new Path("/txn");
    QuerySpec spec = new QuerySpec(path, QueryParams.DEFAULT_PARAMS);
    addCallback(repo, new ValueEventRegistration(repo, newValueEventListener(events), spec));

    final Semaphore semaphore = new Semaphore(0);
    final Map<String, Object> update = ImmutableMap.<String, Object>of("key", "value");
    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.startTransaction(path, new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            currentData.setValue(update);
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
          }
        }, true);
        semaphore.release();
      }
    });
    waitFor(semaphore);
    assertEquals(1, events.size());
    assertNotNull(events.get(0));
    assertEquals(update, events.get(0).getValue());
  }

  @Test
  public void testTransactionError() throws InterruptedException {
    Mockito.doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        RequestResultCallback callback = invocation.getArgument(3);
        callback.onRequestResult("test error", "test error message");
        return null;
      }
    })
        .when(connection).compareAndPut(
        Mockito.eq(ImmutableList.of("txn_error")),
        Mockito.any(),
        Mockito.anyString(),
        Mockito.any(RequestResultCallback.class));

    final Repo repo = newRepo();
    final List<DataSnapshot> events = new ArrayList<>();
    final Path path = new Path("/txn_error");
    QuerySpec spec = new QuerySpec(path, QueryParams.DEFAULT_PARAMS);
    addCallback(repo, new ValueEventRegistration(repo, newValueEventListener(events), spec));

    final Semaphore semaphore = new Semaphore(0);
    final Map<String, Object> update = ImmutableMap.<String, Object>of("key", "value");
    final AtomicInteger count = new AtomicInteger(0);
    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.startTransaction(path, new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            count.incrementAndGet();
            currentData.setValue(update);
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertEquals(1, count.get());
            assertFalse(committed);
            semaphore.release();
          }
        }, true);
        semaphore.release();
      }
    });
    waitFor(semaphore, 2);
    assertEquals(1, events.size());
    assertNotNull(events.get(0));
    assertEquals(update, events.get(0).getValue());
  }

  @Test
  public void testTransactionDataStaleError() throws InterruptedException {
    Mockito.doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        RequestResultCallback callback = invocation.getArgument(3);
        callback.onRequestResult("datastale", "test error message");
        return null;
      }
    })
        .when(connection).compareAndPut(
        Mockito.eq(ImmutableList.of("txn_stale_error")),
        Mockito.any(),
        Mockito.anyString(),
        Mockito.any(RequestResultCallback.class));

    final Repo repo = newRepo();
    final List<DataSnapshot> events = new ArrayList<>();
    final Path path = new Path("/txn_stale_error");
    QuerySpec spec = new QuerySpec(path, QueryParams.DEFAULT_PARAMS);
    addCallback(repo, new ValueEventRegistration(repo, newValueEventListener(events), spec));

    final Semaphore semaphore = new Semaphore(0);
    final Map<String, Object> update = ImmutableMap.<String, Object>of("key", "value");
    final AtomicInteger count = new AtomicInteger(0);
    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.startTransaction(path, new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            count.incrementAndGet();
            currentData.setValue(update);
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertEquals(25, count.get());
            assertFalse(committed);
            semaphore.release();
          }
        }, true);
        semaphore.release();
      }
    });
    waitFor(semaphore, 2);
    assertEquals(1, events.size());
    assertNotNull(events.get(0));
    assertEquals(update, events.get(0).getValue());
  }

  @Test
  public void testTransactionAbort() throws InterruptedException {
    final Repo repo = newRepo();
    final List<DataSnapshot> events = new ArrayList<>();
    final Path path = new Path("/txn_abort");
    QuerySpec spec = new QuerySpec(path, QueryParams.DEFAULT_PARAMS);
    addCallback(repo, new ValueEventRegistration(repo, newValueEventListener(events), spec));

    final Semaphore semaphore = new Semaphore(0);
    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.startTransaction(path, new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            return Transaction.abort();
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
          }
        }, true);
        semaphore.release();
      }
    });
    waitFor(semaphore);
    assertTrue(events.isEmpty());
  }

  @Test
  public void testInfoNodeUpdates() throws InterruptedException {
    final Repo repo = newRepo();
    final List<DataSnapshot> events = new ArrayList<>();
    QuerySpec spec = new QuerySpec(new Path(".info"), QueryParams.DEFAULT_PARAMS);

    final Semaphore semaphore = new Semaphore(0);
    ChildEventRegistration callback = new ChildEventRegistration(repo, new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        events.add(snapshot);
        semaphore.release();
      }

      @Override
      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
        events.add(snapshot);
        semaphore.release();
      }

      @Override
      public void onChildRemoved(DataSnapshot snapshot) {
        events.add(snapshot);
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        events.add(snapshot);
      }

      @Override
      public void onCancelled(DatabaseError error) {
        fail(error.getMessage());
      }
    }, spec);
    addCallback(repo, callback);
    waitFor(semaphore, 2);
    assertEquals(2, events.size());

    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.onConnect();
        semaphore.release();
      }
    });
    waitFor(semaphore, 2);
    assertEquals("connected", events.get(2).getKey());
    assertTrue(events.get(2).getValue(Boolean.class));

    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.onAuthStatus(true);
        semaphore.release();
      }
    });
    waitFor(semaphore, 2);
    assertEquals("authenticated", events.get(3).getKey());
    assertTrue(events.get(3).getValue(Boolean.class));

    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.onDisconnect();
        semaphore.release();
      }
    });
    waitFor(semaphore, 2);
    assertEquals("connected", events.get(4).getKey());
    assertFalse(events.get(4).getValue(Boolean.class));

    final Map<String, Object> update = ImmutableMap.<String, Object>of("k1", "v1");
    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.onServerInfoUpdate(update);
        semaphore.release();
      }
    });
    waitFor(semaphore, 2);
    assertEquals("k1", events.get(5).getKey());
    assertEquals("v1", events.get(5).getValue(String.class));
  }

  @Test
  public void testCallOnComplete() {
    final Repo repo = newRepo();
    final AtomicReference<DatabaseError> errorResult = new AtomicReference<>();
    final AtomicReference<DatabaseReference> refResult = new AtomicReference<>();
    DatabaseReference.CompletionListener listener = new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        errorResult.set(error);
        refResult.set(ref);
      }
    };
    repo.callOnComplete(listener, null, new Path("/foo"));
    assertNull(errorResult.get());
    assertEquals("foo", refResult.get().getKey());

    DatabaseError ex = DatabaseError.fromCode(DatabaseError.WRITE_CANCELED);
    repo.callOnComplete(listener, ex, new Path("/bar"));
    assertEquals(ex, errorResult.get());
    assertEquals("bar", refResult.get().getKey());
  }

  @Test
  public void testOnDisconnectSetValue() throws InterruptedException {
    final Repo repo = newRepo();
    final AtomicReference<DatabaseError> errorResult = new AtomicReference<>();
    final AtomicReference<DatabaseReference> refResult = new AtomicReference<>();
    final Semaphore semaphore = new Semaphore(0);
    final DatabaseReference.CompletionListener listener = newCompletionListener(
        errorResult, refResult);

    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.onDisconnectSetValue(new Path(SUCCESS), NodeUtilities.NodeFromJSON("test"), listener);
        semaphore.release();
      }
    });
    waitFor(semaphore);
    assertNull(errorResult.get());
    assertEquals("success", refResult.get().getKey());

    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.onDisconnectSetValue(new Path(FAILURE), NodeUtilities.NodeFromJSON("test"), listener);
        semaphore.release();
      }
    });
    waitFor(semaphore);
    assertNotNull(errorResult.get());
    assertEquals(DatabaseError.DATA_STALE, errorResult.get().getCode());
    assertEquals("failure", refResult.get().getKey());
  }

  @Test
  public void testOnDisconnectUpdate() throws InterruptedException {
    final Repo repo = newRepo();
    final AtomicReference<DatabaseError> errorResult = new AtomicReference<>();
    final AtomicReference<DatabaseReference> refResult = new AtomicReference<>();
    final Semaphore semaphore = new Semaphore(0);
    final DatabaseReference.CompletionListener listener = newCompletionListener(
        errorResult, refResult);

    final Map<Path, Node> update = ImmutableMap.of(
        new Path("/child"), NodeUtilities.NodeFromJSON("foo"));
    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.onDisconnectUpdate(new Path(SUCCESS), update, listener,
            ImmutableMap.<String, Object>of("child", "foo"));
        semaphore.release();
      }
    });
    waitFor(semaphore);
    assertNull(errorResult.get());
    assertEquals("success", refResult.get().getKey());

    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.onDisconnectUpdate(new Path(FAILURE), update, listener,
            ImmutableMap.<String, Object>of("child", "foo"));
        semaphore.release();
      }
    });
    waitFor(semaphore);
    assertNotNull(errorResult.get());
    assertEquals(DatabaseError.DATA_STALE, errorResult.get().getCode());
    assertEquals("failure", refResult.get().getKey());
  }

  @Test
  public void testOnDisconnectCancel() throws InterruptedException {
    final Repo repo = newRepo();
    final AtomicReference<DatabaseError> errorResult = new AtomicReference<>();
    final AtomicReference<DatabaseReference> refResult = new AtomicReference<>();
    final Semaphore semaphore = new Semaphore(0);
    final DatabaseReference.CompletionListener listener = newCompletionListener(
        errorResult, refResult);

    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.onDisconnectCancel(new Path(SUCCESS), listener);
        semaphore.release();
      }
    });
    waitFor(semaphore);
    assertNull(errorResult.get());
    assertEquals("success", refResult.get().getKey());

    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.onDisconnectCancel(new Path(FAILURE), listener);
        semaphore.release();
      }
    });
    waitFor(semaphore);
    assertNotNull(errorResult.get());
    assertEquals(DatabaseError.DATA_STALE, errorResult.get().getCode());
    assertEquals("failure", refResult.get().getKey());
  }

  private DatabaseReference.CompletionListener newCompletionListener(
      final AtomicReference<DatabaseError> errorResult,
      final AtomicReference<DatabaseReference> refResult) {
    return new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        errorResult.set(error);
        refResult.set(ref);
      }
    };
  }

  private Repo newRepo() {
    return new Repo(new RepoInfo(), config, Mockito.mock(FirebaseDatabase.class));
  }

  private ValueEventListener newValueEventListener(final List<DataSnapshot> events) {
    return new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        events.add(snapshot);
      }

      @Override
      public void onCancelled(DatabaseError error) {
        fail(error.getMessage());
      }
    };
  }

  private void addCallback(final Repo repo, final EventRegistration callback) {
    repo.scheduleNow(new Runnable() {
      @Override
      public void run() {
        repo.addEventCallback(callback);
      }
    });
  }

  private static PersistentConnection mockConnection() {
    PersistentConnection connection = Mockito.mock(PersistentConnection.class);
    Answer success = new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        RequestResultCallback callback = invocation.getArgument(2);
        callback.onRequestResult(null, null);
        return null;
      }
    };
    Mockito.doAnswer(success).when(connection).onDisconnectPut(
        Mockito.eq(SUCCESS),
        Mockito.any(),
        Mockito.any(RequestResultCallback.class));
    Mockito.doAnswer(success).when(connection).onDisconnectMerge(
        Mockito.eq(SUCCESS),
        Mockito.<String, Object>anyMap(),
        Mockito.any(RequestResultCallback.class));

    Answer failure = new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        RequestResultCallback callback = invocation.getArgument(2);
        callback.onRequestResult("datastale", null);
        return null;
      }
    };
    Mockito.doAnswer(failure).when(connection).onDisconnectPut(
        Mockito.eq(FAILURE),
        Mockito.any(),
        Mockito.any(RequestResultCallback.class));
    Mockito.doAnswer(failure).when(connection).onDisconnectMerge(
        Mockito.eq(FAILURE),
        Mockito.<String, Object>anyMap(),
        Mockito.any(RequestResultCallback.class));

    success = new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        RequestResultCallback callback = invocation.getArgument(1);
        callback.onRequestResult(null, null);
        return null;
      }
    };
    Mockito.doAnswer(success).when(connection).onDisconnectCancel(
        Mockito.eq(SUCCESS),
        Mockito.any(RequestResultCallback.class));

    failure = new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        RequestResultCallback callback = invocation.getArgument(1);
        callback.onRequestResult("datastale", null);
        return null;
      }
    };
    Mockito.doAnswer(failure).when(connection).onDisconnectCancel(
        Mockito.eq(FAILURE),
        Mockito.any(RequestResultCallback.class));
    return connection;
  }
}
