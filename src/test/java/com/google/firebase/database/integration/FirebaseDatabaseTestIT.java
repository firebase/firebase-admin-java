package com.google.firebase.database.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.TestFailure;
import com.google.firebase.database.TestHelpers;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.tasks.Tasks;
import com.google.firebase.testing.IntegrationTestUtils;
import com.google.firebase.testing.TestUtils;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class FirebaseDatabaseTestIT {

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
  public void testGetDefaultInstance() {
    FirebaseDatabase db = FirebaseDatabase.getInstance();
    assertEquals(FirebaseApp.getInstance().getOptions().getDatabaseUrl(), 
        db.getReference().toString());
  }
  
  @Test
  public void testGetInstanceForApp() throws InterruptedException, TestFailure, TimeoutException {
    FirebaseDatabase db = FirebaseDatabase.getInstance(masterApp);
    assertEquals(masterApp.getOptions().getDatabaseUrl(), db.getReference().toString());
  }

  @Test
  public void testNullDatabaseUrl() {
    FirebaseApp app = appWithDbUrl(null, "nullDbUrl");
    try {
      FirebaseDatabase.getInstance(app);
      fail("no error thrown for getInstance() with null URL");
    } catch (DatabaseException expected) { // ignore
    }
  }

  @Test
  public void testMalformedDatabaseUrlInOptions() {
    FirebaseApp app = appWithDbUrl("not-a-url", "malformedDbUrlInOptions");
    try {
      FirebaseDatabase.getInstance(app);
      fail("no error thrown for getInstance() with malformed URL");
    } catch (DatabaseException expected) { // ignore
    }
  }
  
  @Test
  public void testMalformedDatabaseUrlInGetInstance() {
    FirebaseApp app = appWithoutDbUrl("malformedDbUrlInGetInstance");
    try {
      FirebaseDatabase.getInstance(app, "not-a-url");
      fail("no error thrown for getInstance() with malformed URL");
    } catch (DatabaseException expected) { // ignore
    }
  }
  
  @Test
  public void testDatabaseUrlWithPathInOptions() {
    FirebaseApp app = appWithDbUrl(IntegrationTestUtils.getDatabaseUrl() 
        + "/paths/are/not/allowed", "dbUrlWithPathInOptions");
    try {      
      FirebaseDatabase.getInstance(app);
      fail("no error thrown for DB URL with path");
    } catch (DatabaseException expected) { // ignore
    }
  }
  
  @Test
  public void testDatabaseUrlWithPathInGetInstance() {
    FirebaseApp app = appWithoutDbUrl("dbUrlWithPathInGetInstance");
    try {      
      FirebaseDatabase.getInstance(app, IntegrationTestUtils.getDatabaseUrl() 
          + "/paths/are/not/allowed");
      fail("no error thrown for DB URL with path");
    } catch (DatabaseException expected) { // ignore
    }
  }
  
  @Test
  public void testGetReference() {
    FirebaseDatabase db = FirebaseDatabase.getInstance(masterApp);
    assertEquals(IntegrationTestUtils.getDatabaseUrl() + "/foo", 
        db.getReference("foo").toString());
  }

  @Test
  public void testGetReferenceFromURLWithoutPath() {
    String dbUrl = IntegrationTestUtils.getDatabaseUrl();
    FirebaseDatabase db = FirebaseDatabase.getInstance(masterApp);
    DatabaseReference ref = db.getReferenceFromUrl(dbUrl);
    assertEquals(dbUrl, ref.toString());
  }

  @Test
  public void testGetReferenceFromURLWithPath() {
    String dbUrl = IntegrationTestUtils.getDatabaseUrl();
    FirebaseDatabase db = FirebaseDatabase.getInstance(masterApp);
    DatabaseReference ref = db.getReferenceFromUrl(dbUrl + "/foo/bar");
    assertEquals(dbUrl + "/foo/bar", ref.toString());
  }

  @Test(expected = DatabaseException.class)
  public void testGetReferenceThrowsWithBadUrl() {
    FirebaseDatabase db = FirebaseDatabase.getInstance();
    db.getReferenceFromUrl("https://tests2.fake-firebaseio.com:9000");
  }

  @Test
  public void testSetValue() throws InterruptedException, ExecutionException, TimeoutException,
      TestFailure {
    FirebaseDatabase db = FirebaseDatabase.getInstance(masterApp);
    DatabaseReference ref = db.getReference("testSetValue");
    Tasks.await(ref.setValue("foo"), TestUtils.TEST_TIMEOUT_MILLIS,
        TimeUnit.MILLISECONDS);
    ReadFuture readFuture = ReadFuture.untilEquals(ref, "foo");
    readFuture.timedWait();
  }

  @Test
  public void testDeleteApp() throws InterruptedException, TestFailure, TimeoutException,
      ExecutionException {
    FirebaseApp app = IntegrationTestUtils.initApp("testDeleteApp");
    List<DatabaseReference> ref = IntegrationTestUtils.getRandomNode(app, 2);
    DatabaseReference writer = ref.get(0);
    DatabaseReference reader = ref.get(1);
    writer.setValue("test");
    TestHelpers.waitForRoundtrip(writer.getRoot());
    ReadFuture.untilEquals(reader, "test").timedWait();

    app.delete();
    try {
      IntegrationTestUtils.getRandomNode(app);
      fail("No error thrown for deleted app");
    } catch (IllegalStateException expected) {
      // ignore
    }

    try {
      writer.setValue("foo");
      fail("No error thrown for deleted app");
    } catch (IllegalStateException expected) {
      // ignore
    }

    try {
      writer.updateChildren(MapBuilder.of("a", 1));
      fail("No error thrown for deleted app");
    } catch (IllegalStateException expected) {
      // ignore
    }

    try {
      writer.addValueEventListener(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot snapshot) {
        }

        @Override
        public void onCancelled(DatabaseError error) {
        }
      });
      fail("No error thrown for deleted app");
    } catch (IllegalStateException expected) {
      // ignore
    }

    try {
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
        public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        }

        @Override
        public void onCancelled(DatabaseError error) {
        }
      });
      fail("No error thrown for deleted app");
    } catch (IllegalStateException expected) {
      // ignore
    }

    app = IntegrationTestUtils.initApp("testDeleteApp");
    ref = IntegrationTestUtils.getRandomNode(app, 2);
    writer = ref.get(0);
    reader = ref.get(1);
    writer.setValue("test2");
    TestHelpers.waitForRoundtrip(writer.getRoot());
    ReadFuture.untilEquals(reader, "test2").timedWait();
  }
  
  private static FirebaseApp appWithDbUrl(String dbUrl, String name) {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setDatabaseUrl(dbUrl)
        .setCredential(FirebaseCredentials.fromCertificate(
            IntegrationTestUtils.getServiceAccountCertificate()))
        .build();
    return FirebaseApp.initializeApp(options, name);
  }
  
  private static FirebaseApp appWithoutDbUrl(String name) {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredential(FirebaseCredentials.fromCertificate(
            IntegrationTestUtils.getServiceAccountCertificate()))
        .build();
    return FirebaseApp.initializeApp(options, name);
  }
}
