package com.google.firebase.database;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.persistence.CachePolicy;
import com.google.firebase.database.core.persistence.DefaultPersistenceManager;
import com.google.firebase.database.core.persistence.MockPersistenceStorageEngine;
import com.google.firebase.database.core.persistence.PersistenceManager;
import com.google.firebase.database.future.WriteFuture;
import com.google.firebase.testing.ServiceAccount;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.firebase.database.TestHelpers.fromSingleQuotedString;
import static org.junit.Assert.*;

public class FirebaseDatabaseTestIT {

  private static DatabaseReference rootRefWithEngine(MockPersistenceStorageEngine engine) {
    DatabaseConfig config = TestHelpers.newTestConfig();
    PersistenceManager persistenceManager =
        new DefaultPersistenceManager(config, engine, CachePolicy.NONE);
    TestHelpers.setForcedPersistentCache(config, persistenceManager);
    return TestHelpers.rootWithConfig(config);
  }

  private static FirebaseApp emptyApp(String appId) {
    return appForDatabaseUrl(null, appId);
  }

  private static FirebaseApp appForDatabaseUrl(String url, String name) {
    return FirebaseApp.initializeApp(
        new FirebaseOptions.Builder()
            .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
            .setDatabaseUrl(url)
            .build(),
        name);
  }

  private static FirebaseDatabase dbForDatabaseUrl(String url, String name) {
    return FirebaseDatabase.getInstance(appForDatabaseUrl(url, name));
  }

  @Before
  public void setup() {
    TestHelpers.ensureAppInitialized();
  }

  @After
  public void tearDown() {
    TestHelpers.failOnFirstUncaughtException();
  }

  @Test
  public void getInstance() {
    FirebaseDatabase db = FirebaseDatabase.getInstance();
    assertEquals(
        FirebaseApp.getInstance().getOptions().getDatabaseUrl(), db.getReference().toString());
  }

  @Test
  public void getInstanceForApp() {
    FirebaseApp app = appForDatabaseUrl(TestConstants.TEST_ALT_NAMESPACE, "getInstanceForApp");
    FirebaseDatabase db = FirebaseDatabase.getInstance(app);

    assertEquals(TestConstants.TEST_ALT_NAMESPACE, db.getReference().toString());
  }

  @Test
  public void getInstanceForAppWithUrl() {
    FirebaseApp app =
        appForDatabaseUrl(TestConstants.TEST_ALT_NAMESPACE, "getInstanceForAppWithUrl");
    FirebaseDatabase db = FirebaseDatabase.getInstance(app, TestConstants.TEST_NAMESPACE);

    assertEquals(TestConstants.TEST_NAMESPACE, db.getReference().toString());
  }

  @Test
  public void getInstanceForAppWithHttpsUrl() {
    FirebaseApp app =
        appForDatabaseUrl(TestConstants.TEST_ALT_NAMESPACE, "getInstanceForAppWithHttpsUrl");
    FirebaseDatabase db = FirebaseDatabase.getInstance(app, "https://tests.fblocal.com:9000");

    assertEquals("https://tests.fblocal.com:9000", db.getReference().toString());
  }

  @Test
  public void getDifferentInstanceForAppWithUrl() {
    FirebaseApp app =
        appForDatabaseUrl(TestConstants.TEST_NAMESPACE, "getDifferentInstanceForAppWithUrl");
    FirebaseDatabase unspecified = FirebaseDatabase.getInstance(app);
    FirebaseDatabase original = FirebaseDatabase.getInstance(app, TestConstants.TEST_NAMESPACE);
    FirebaseDatabase alternate =
        FirebaseDatabase.getInstance(app, TestConstants.TEST_ALT_NAMESPACE);

    assertEquals(TestConstants.TEST_NAMESPACE, unspecified.getReference().toString());
    assertEquals(TestConstants.TEST_NAMESPACE, original.getReference().toString());
    assertEquals(TestConstants.TEST_ALT_NAMESPACE, alternate.getReference().toString());
  }

  @Test
  public void getInstanceForAppWithInvalidUrls() {
    try {
      FirebaseApp app = appForDatabaseUrl(null, "getInstanceForAppWithInvalidUrls-0");
      FirebaseDatabase.getInstance(app);
      fail("should throw");
    } catch (DatabaseException e) {
      // expected
    }

    try {
      FirebaseApp app = appForDatabaseUrl("not-a-url", "getInstanceForAppWithInvalidUrls-1");
      FirebaseDatabase.getInstance(app);
      fail("should throw");
    } catch (DatabaseException e) {
      // expected
    }

    try {
      FirebaseApp app = emptyApp("getInstanceForAppWithInvalidUrls-2");
      FirebaseDatabase.getInstance(app, "not-a-url");
      fail("should throw");
    } catch (DatabaseException e) {
      // expected
    }

    try {
      FirebaseApp app =
          appForDatabaseUrl(
              "http://x.fblocal.com:9000/paths/are/not/allowed",
              "getInstanceForAppWithInvalidUrls-3");
      FirebaseDatabase.getInstance(app);
      fail("should throw");
    } catch (DatabaseException e) {
      // expected
    }

    try {
      FirebaseApp app = emptyApp("getInstanceForAppWithInvalidUrls-4");
      FirebaseDatabase.getInstance(app, "http://x.fblocal.com:9000/paths/are/not/allowed");
      fail("should throw");
    } catch (DatabaseException e) {
      // expected
    }
  }

  @Test
  public void getReference() {
    FirebaseDatabase db = FirebaseDatabase.getInstance();
    assertEquals(TestConstants.TEST_NAMESPACE + "/foo", db.getReference("foo").toString());
  }

  @Test
  public void persistenceSettings() {
    FirebaseDatabase.getInstance().setPersistenceEnabled(true);

    try {
      FirebaseDatabase.getInstance().setPersistenceCacheSizeBytes(1024 * 1024 - 1);
      fail("should throw - minumum size is 1 MB");
    } catch (DatabaseException e) {
      // expected
    }
    try {
      FirebaseDatabase.getInstance().setPersistenceCacheSizeBytes(100 * 1024 * 1024 + 1);
      fail("should throw - maximum size is 100 MB");
    } catch (DatabaseException e) {
      // expected
    }

    FirebaseDatabase.getInstance().setPersistenceCacheSizeBytes(1024 * 1024);
    FirebaseDatabase db = FirebaseDatabase.getInstance();
    assertEquals(TestConstants.TEST_NAMESPACE + "/foo", db.getReference("foo").toString());

    try {
      FirebaseDatabase.getInstance().setPersistenceCacheSizeBytes(1024 * 1024);
      fail("should throw - can't modify after init");
    } catch (DatabaseException e) {
      // expected
    }
  }

  @Test
  public void getReferenceFromURLWithEmptyPath() {
    FirebaseDatabase db = FirebaseDatabase.getInstance();
    DatabaseReference ref = db.getReferenceFromUrl(TestConstants.TEST_NAMESPACE);
    assertEquals(TestConstants.TEST_NAMESPACE, ref.toString());
  }

  @Test
  public void getReferenceFromURLWithPath() {
    FirebaseDatabase db = FirebaseDatabase.getInstance();
    DatabaseReference ref = db.getReferenceFromUrl(TestConstants.TEST_NAMESPACE + "/foo/bar");
    assertEquals(TestConstants.TEST_NAMESPACE + "/foo/bar", ref.toString());
  }

  @Test(expected = DatabaseException.class)
  public void getReferenceThrowsWithBadUrl() {
    FirebaseDatabase db = FirebaseDatabase.getInstance();
    db.getReferenceFromUrl(TestConstants.TEST_ALT_NAMESPACE);
  }

  @Test
  public void getReferenceWithCustomDatabaseUrl() {
    FirebaseDatabase db = FirebaseDatabase.getInstance(TestConstants.TEST_ALT_NAMESPACE);
    db.getReferenceFromUrl(TestConstants.TEST_ALT_NAMESPACE);
  }

  @Test
  public void referenceEqualityForDatabase() {
    FirebaseDatabase db1 = dbForDatabaseUrl(TestConstants.TEST_NAMESPACE, "db1");
    FirebaseDatabase db2 = dbForDatabaseUrl(TestConstants.TEST_NAMESPACE, "db2");
    FirebaseDatabase altDb = dbForDatabaseUrl(TestConstants.TEST_ALT_NAMESPACE, "altdb");

    DatabaseReference testRef1 = db1.getReference();
    DatabaseReference testRef2 = db1.getReference("foo");
    DatabaseReference testRef3 = altDb.getReference();

    DatabaseReference testRef5 = db2.getReference();
    DatabaseReference testRef6 = db2.getReference();

    // Referential equality
    assertSame(testRef1.getDatabase(), testRef2.getDatabase());
    assertNotSame(testRef1.getDatabase(), testRef3.getDatabase());
    assertNotSame(testRef1.getDatabase(), testRef5.getDatabase());
    assertNotSame(testRef1.getDatabase(), testRef6.getDatabase());

    // Same config yields same firebase
    assertSame(testRef5.getDatabase(), testRef6.getDatabase());

    db1.goOffline();
    db2.goOffline();
    altDb.goOffline();
  }

  @Test
  public void defaultPersistenceLocation() {
    FirebaseDatabase db = FirebaseDatabase.getInstance();
    // call get reference to ensure everything's initialized.
    db.getReference();

    // The default persistence key must be "default" to maintain backwards compatibility
    // with persisted data.
    assertEquals("default", db.getConfig().getSessionPersistenceKey());
  }

  @Test
  public void purgeWritesPurgesAllWrites() {
    MockPersistenceStorageEngine engine = new MockPersistenceStorageEngine();
    DatabaseReference ref = rootRefWithEngine(engine);
    FirebaseDatabase app = ref.getDatabase();

    app.goOffline();

    ref.push().setValue("test-value-1");
    ref.push().setValue("test-value-2");
    ref.push().setValue("test-value-3");
    ref.push().setValue("test-value-4");

    TestHelpers.waitForQueue(ref);
    assertEquals(4, engine.loadUserWrites().size());

    app.purgeOutstandingWrites();

    TestHelpers.waitForQueue(ref);
    assertEquals(0, engine.loadUserWrites().size());
  }

  @Test
  @Ignore("This test is flaky because cancel order isn't actually deterministic!")
  // TODO(mikelehen): Need to fix
  public void purgedWritesAreCancelledInOrder() {
    MockPersistenceStorageEngine engine = new MockPersistenceStorageEngine();
    DatabaseReference ref = rootRefWithEngine(engine);
    FirebaseDatabase app = ref.getDatabase();

    app.goOffline();

    final List<String> order = new ArrayList<>();

    ref.push()
        .setValue(
            "test-value-1",
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                assertEquals(error.getCode(), DatabaseError.WRITE_CANCELED);
                order.add("1");
              }
            });
    ref.push()
        .setValue(
            "test-value-2",
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                assertEquals(error.getCode(), DatabaseError.WRITE_CANCELED);
                order.add("2");
              }
            });
    ref.push()
        .setValue(
            "test-value-3",
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                assertEquals(error.getCode(), DatabaseError.WRITE_CANCELED);
                order.add("3");
              }
            });
    ref.push()
        .setValue(
            "test-value-4",
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                assertEquals(error.getCode(), DatabaseError.WRITE_CANCELED);
                order.add("4");
              }
            });

    TestHelpers.waitForQueue(ref);
    assertEquals(4, engine.loadUserWrites().size());

    app.purgeOutstandingWrites();

    TestHelpers.waitForEvents(ref);
    assertEquals(Arrays.asList("1", "2", "3", "4"), order);
  }

  @Test
  public void purgeWritesCancelsOnDisconnects() {
    MockPersistenceStorageEngine engine = new MockPersistenceStorageEngine();
    DatabaseReference ref = rootRefWithEngine(engine);
    FirebaseDatabase app = ref.getDatabase();

    app.goOffline();

    final List<String> order = new ArrayList<>();

    ref.push()
        .onDisconnect()
        .setValue(
            "test-value-1",
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                assertEquals(error.getCode(), DatabaseError.WRITE_CANCELED);
                order.add("1");
              }
            });
    ref.push()
        .onDisconnect()
        .setValue(
            "test-value-2",
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                assertEquals(error.getCode(), DatabaseError.WRITE_CANCELED);
                order.add("2");
              }
            });

    TestHelpers.waitForEvents(ref);

    app.purgeOutstandingWrites();

    TestHelpers.waitForEvents(ref);
    assertEquals(Arrays.asList("1", "2"), order);
  }

  @Test
  @Ignore("This test is flaky because cancel order isn't actually deterministic!")
  // TODO(mikelehen): Need to fix
  public void purgeWritesReraisesEvents() throws Throwable {
    MockPersistenceStorageEngine engine = new MockPersistenceStorageEngine();
    DatabaseReference ref = rootRefWithEngine(engine).push();
    FirebaseDatabase app = ref.getDatabase();

    new WriteFuture(
        ref, fromSingleQuotedString("{'foo': 'foo-value', 'bar': {'qux': 'qux-value'}}"))
        .timedGet();

    final List<Object> fooValues = new ArrayList<>();
    final List<Object> barQuuValues = new ArrayList<>();
    final List<Object> barQuxValues = new ArrayList<>();

    ref.child("foo")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                fooValues.add(snapshot.getValue());
              }

              @Override
              public void onCancelled(DatabaseError error) {
              }
            });

    ref.child("bar/quu")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                barQuuValues.add(snapshot.getValue());
              }

              @Override
              public void onCancelled(DatabaseError error) {
              }
            });

    ref.child("bar/qux")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                barQuxValues.add(snapshot.getValue());
              }

              @Override
              public void onCancelled(DatabaseError error) {
              }
            });

    app.goOffline();

    List<Object> expectedFooValues =
        Arrays.<Object>asList("foo-value", "new-foo-value", "newest-foo-value", "foo-value");
    List<Object> expectedBarQuuValues = Arrays.<Object>asList(null, "quu-value", null);
    List<Object> expectedBarQuxValues =
        Arrays.<Object>asList("qux-value", "new-qux-value", "qux-value");

    final List<String> cancelOrder = new ArrayList<>();

    ref.child("foo")
        .setValue(
            "new-foo-value",
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                assertEquals(DatabaseError.WRITE_CANCELED, error.getCode());
                // This should be after we raised the events
                assertEquals("foo-value", fooValues.get(fooValues.size() - 1));
                cancelOrder.add("foo-1");
              }
            });
    ref.child("bar")
        .updateChildren(
            fromSingleQuotedString("{'quu': 'quu-value', 'qux': 'new-qux-value'}"),
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                assertEquals(DatabaseError.WRITE_CANCELED, error.getCode());
                // This should be after we raised the events
                assertEquals("qux-value", barQuxValues.get(barQuuValues.size() - 1));
                assertEquals(null, barQuuValues.get(barQuuValues.size() - 1));
                cancelOrder.add("bar");
              }
            });
    ref.child("foo")
        .setValue(
            "newest-foo-value",
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                assertEquals(DatabaseError.WRITE_CANCELED, error.getCode());
                // This should be after we raised the events
                assertEquals("foo-value", fooValues.get(fooValues.size() - 1));
                cancelOrder.add("foo-2");
              }
            });

    app.purgeOutstandingWrites();

    TestHelpers.waitForEvents(ref);

    assertEquals(Arrays.asList("foo-1", "bar", "foo-2"), cancelOrder);

    assertEquals(expectedFooValues, fooValues);
    assertEquals(expectedBarQuuValues, barQuuValues);
    assertEquals(expectedBarQuxValues, barQuxValues);

    app.goOnline();
    // Make sure we're back online and reconnected again
    TestHelpers.waitForEvents(ref);

    // No events should be reraised...
    assertEquals(expectedFooValues, fooValues);
    assertEquals(expectedBarQuuValues, barQuuValues);
    assertEquals(expectedBarQuxValues, barQuxValues);
  }

  @Test
  public void purgeWritesCancelsTransactions() throws Throwable {
    MockPersistenceStorageEngine engine = new MockPersistenceStorageEngine();
    DatabaseReference ref = rootRefWithEngine(engine).push();
    FirebaseDatabase app = ref.getDatabase();

    final List<String> events = new ArrayList<>();

    ref.addValueEventListener(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            events.add("value-" + snapshot.getValue());
          }

          @Override
          public void onCancelled(DatabaseError error) {
          }
        });

    // Make sure the first value event is fired
    TestHelpers.waitForRoundtrip(ref);

    app.goOffline();

    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            currentData.setValue("1");
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertEquals(DatabaseError.WRITE_CANCELED, error.getCode());
            events.add("cancel-1");
          }
        });

    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            currentData.setValue("2");
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertEquals(DatabaseError.WRITE_CANCELED, error.getCode());
            events.add("cancel-2");
          }
        });

    ref.getDatabase().purgeOutstandingWrites();

    TestHelpers.waitForEvents(ref);

    // The order should really be cancel-1 then cancel-2, but too difficult to implement currently.
    assertEquals(
        Arrays.asList("value-null", "value-1", "value-2", "value-null", "cancel-2", "cancel-1"),
        events);
  }
}
