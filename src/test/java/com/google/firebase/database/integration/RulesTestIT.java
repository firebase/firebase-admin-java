package com.google.firebase.database.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.EventRecord;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.TestFailure;
import com.google.firebase.database.TestHelpers;
import com.google.firebase.database.TestTokenProvider;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.RepoManager;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.database.future.WriteFuture;
import com.google.firebase.tasks.Tasks;
import com.google.firebase.testing.IntegrationTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class RulesTestIT {

  private static final String DEFAULT_RULES_STRING = 
      "{\n    \"rules\": {\n        \".read\": true,\n        \".write\": true\n    }\n}";

  private static final Map<String, Object> testRules;
  private static final ObjectMapper mapper = new ObjectMapper();
  
  static {
    testRules = new MapBuilder().put("read_only", MapBuilder.of(".read", true))
        .put("write_only", MapBuilder.of(".write", true))
        .put("read_and_write",
            new MapBuilder().put(".write", true)
                .put(".read",
                    true)
                .build())
        .put("any_auth",
            new MapBuilder().put(".write", "auth != null").put(".read", "auth != null").build())
        .put("revocable", new MapBuilder().put(".write", true)
            .put(".read",
                "data.child('public').val() == true && data.child('hidden').val() != true")
            .build())
        .put("users", new MapBuilder().put(".write", true).put(".read", true)
            .put("$user", new MapBuilder().put(".validate", "newData.hasChildren(['name', 'age'])")
                .put("name",
                    new MapBuilder().put(".validate", "newData.isString() == true").build())
                .put("age",
                    new MapBuilder()
                        .put(".validate", "newData.isNumber() && newData.val() > " + "13").build())
                .build())
            .build())
        .build();
  }

  private static DatabaseReference reader;
  private static DatabaseReference writer;

  private static FirebaseApp masterApp;

  @BeforeClass
  public static void setUpClass() throws IOException {
    // Init app with non-admin privileges
    Map<String, Object> auth = MapBuilder.of("uid", "my-service-worker");
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredential(FirebaseCredentials
            .fromCertificate(IntegrationTestUtils.getServiceAccountCertificate()))
        .setDatabaseUrl(IntegrationTestUtils.getDatabaseUrl())
        .setDatabaseAuthVariableOverride(auth)
        .build();
    masterApp = FirebaseApp.initializeApp(options, "RulesTestIT");

    List<DatabaseReference> refs = IntegrationTestUtils.getRandomNode(masterApp, 2);
    reader = refs.get(0);
    writer = refs.get(1);
    String rules = mapper
        .writeValueAsString(MapBuilder.of("rules", MapBuilder.of(writer.getKey(), testRules)));
    uploadRules(rules);
    TestHelpers.waitForRoundtrip(writer.getRoot());
  }

  @AfterClass
  public static void tearDownClass() throws IOException {
    uploadRules(DEFAULT_RULES_STRING);
    TestHelpers.waitForRoundtrip(writer.getRoot());
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  private static void uploadRules(String rules) throws IOException {
    IntegrationTestUtils.AppHttpClient client = new IntegrationTestUtils.AppHttpClient(masterApp);
    IntegrationTestUtils.ResponseInfo response = client.put("/.settings/rules.json", rules);
    assertEquals(200, response.getStatus());
  }

  @Test
  public void writeOperationSetsErrorOnFailure() throws InterruptedException {
    DatabaseReference ref = writer.child("read_only");

    final Semaphore semaphore = new Semaphore(0);
    final AtomicReference<DatabaseError> result = new AtomicReference<>();
    ref.setValue("value", new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        result.compareAndSet(null, error);
        semaphore.release(1);
      }
    });

    TestHelpers.waitFor(semaphore);
    assertNotNull(result.get());

    // All of the other writing methods are just testing the server. We're just
    // testing the
    // error propagation.
  }

  @Test
  public void failedListensDontDisruptOtherListens()
      throws TestFailure, TimeoutException, InterruptedException {
    // Wait until we're connected
    ReadFuture.untilEquals(reader.getRoot().child(".info/connected"), true).timedGet();

    final Semaphore semaphore = new Semaphore(0);
    reader.addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        fail("Should not get data");
      }

      @Override
      public void onCancelled(DatabaseError error) {
        semaphore.release(1);
      }
    });

    TestHelpers.waitFor(semaphore);
    final AtomicBoolean saw42 = new AtomicBoolean(false);
    final ValueEventListener listener = reader.child("read_and_write")
        .addValueEventListener(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            // No-op
            Integer value = snapshot.getValue(Integer.class);
            if (value != null) {
              if (value == 42) {
                assertTrue(saw42.compareAndSet(false, true));
                semaphore.release(1);
              } else if (value == 84) {
                assertTrue(saw42.get());
                semaphore.release(1);
              } else {
                fail("unexpected value");
              }
            }
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("This one shouldn't fail");
          }
        });

    writer.child("read_and_write").setValue(42);
    TestHelpers.waitFor(semaphore);
    writer.child("read_and_write").setValue(84);
    TestHelpers.waitFor(semaphore);
    reader.child("read_and_write").removeEventListener(listener);
  }

  @Test
  public void failedListensDontDisruptOtherListens2()
      throws TestFailure, TimeoutException, InterruptedException {
    // Wait until we're connected
    ReadFuture.untilEquals(reader.getRoot().child(".info/connected"), true).timedGet();

    final Semaphore semaphore = new Semaphore(0);
    final AtomicBoolean saw42 = new AtomicBoolean(false);
    final ValueEventListener listener = reader.child("read_and_write")
        .addValueEventListener(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            // No-op
            Integer value = snapshot.getValue(Integer.class);
            if (value != null) {
              if (value == 42) {
                assertTrue(saw42.compareAndSet(false, true));
                semaphore.release(1);
              } else if (value == 84) {
                assertTrue(saw42.get());
                semaphore.release(1);
              } else {
                fail("unexpected value");
              }
            }
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("This one shouldn't fail");
          }
        });

    writer.child("read_and_write").setValue(42);
    TestHelpers.waitFor(semaphore);
    reader.addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        fail("Should not get data");
      }

      @Override
      public void onCancelled(DatabaseError error) {
        semaphore.release(1);
      }
    });

    TestHelpers.waitFor(semaphore);

    writer.child("read_and_write").setValue(84);
    TestHelpers.waitFor(semaphore);
    reader.child("read_and_write").removeEventListener(listener);
  }

  @Test
  public void listenRevocation()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    new WriteFuture(writer.child("revocable"),
        new MapBuilder().put("public", true).put("data", 1).build()).timedGet();

    final AtomicBoolean valueHit = new AtomicBoolean(false);
    final AtomicInteger value = new AtomicInteger(0);
    final Semaphore semaphore = new Semaphore(0);
    reader.child("revocable/data").addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        valueHit.compareAndSet(false, true);
        value.compareAndSet(0, snapshot.getValue(Integer.class));
        semaphore.release(1);
      }

      @Override
      public void onCancelled(DatabaseError error) {
        semaphore.release(1);
      }
    });

    TestHelpers.waitFor(semaphore);
    assertTrue(valueHit.get());
    assertEquals(1, value.get());

    writer.child("revocable/public").setValue(false);
    writer.child("revocable/data").setValue(2);
    TestHelpers.waitFor(semaphore);
    assertTrue(valueHit.get());

    writer.child("revocable/public").setValue(true);
    new WriteFuture(writer.child("revocable/data"), 3).timedGet();

    // Ok, the listen was cancelled, create a new one now.
    ValueEventListener listener = reader.child("revocable/data")
        .addValueEventListener(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            assertEquals(3, (int) snapshot.getValue(Integer.class));
            semaphore.release(1);
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("This listen shouldn't fail");
          }
        });

    TestHelpers.waitFor(semaphore);
    reader.child("revocable/data").removeEventListener(listener);
  }

  @Test
  public void failedSetsRolledBack()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference fredRef = reader.child("users/fred");

    new WriteFuture(fredRef, new MapBuilder().put("name", "Fred").put("age", 19).build())
        .timedGet();

    final Semaphore semaphore = new Semaphore(0);
    ReadFuture future = new ReadFuture(fredRef.child("age"), new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        if (events.size() == 1) {
          semaphore.release(1);
        }
        return events.size() == 3;
      }
    });

    // Wait for initial data
    TestHelpers.waitFor(semaphore);

    fredRef.child("age").setValue(12, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        assertNotNull(error);
        semaphore.release(1);
      }
    });

    List<EventRecord> events = future.timedGet();
    assertEquals(19, (int) events.get(0).getSnapshot().getValue(Integer.class));
    assertEquals(12, (int) events.get(1).getSnapshot().getValue(Integer.class));
    assertEquals(19, (int) events.get(2).getSnapshot().getValue(Integer.class));
    TestHelpers.waitFor(semaphore);
  }

  @Test
  public void failedUpdatesRolledBack()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference fredRef = reader.child("users/fred");

    new WriteFuture(fredRef, new MapBuilder().put("name", "Fred").put("age", 19).build())
        .timedGet();

    final Semaphore semaphore = new Semaphore(0);
    ReadFuture future = new ReadFuture(fredRef.child("age"), new ReadFuture.CompletionCondition() {
      @Override
      public boolean isComplete(List<EventRecord> events) {
        if (events.size() == 1) {
          semaphore.release(1);
        }
        return events.size() == 3;
      }
    });

    // Wait for initial data
    TestHelpers.waitFor(semaphore);

    Map<String, Object> update = MapBuilder.of("age", 12);
    fredRef.updateChildren(update, new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {
        assertNotNull(error);
        semaphore.release(1);
      }
    });

    List<EventRecord> events = future.timedGet();
    assertEquals(19, (int) events.get(0).getSnapshot().getValue(Integer.class));
    assertEquals(12, (int) events.get(1).getSnapshot().getValue(Integer.class));
    assertEquals(19, (int) events.get(2).getSnapshot().getValue(Integer.class));
    TestHelpers.waitFor(semaphore);
  }

  @Test
  public void stillAuthenticatedAfterReconnect()
      throws InterruptedException, ExecutionException, TestFailure, TimeoutException {
    DatabaseConfig config = TestHelpers.getDatabaseConfig(masterApp);
    DatabaseReference root = FirebaseDatabase.getInstance(masterApp).getReference();
    DatabaseReference ref = root.child(writer.getPath().toString());
    RepoManager.interrupt(config);
    RepoManager.resume(config);
    DatabaseError err = new WriteFuture(ref.child("any_auth"), true).timedGet();
    assertNull(err);
  }

  @Test
  public void connectionAuthenticatedImmediatelyAfterTokenChange() throws Exception {
    DatabaseConfig config = TestHelpers.getDatabaseConfig(masterApp);
    TestTokenProvider provider = new TestTokenProvider(TestHelpers.getExecutorService(config));
    config.setAuthTokenProvider(provider);

    DatabaseReference root = FirebaseDatabase.getInstance(masterApp).getReference();
    DatabaseReference ref = root.child(writer.getPath().toString());

    String token = Tasks.await(TestOnlyImplFirebaseTrampolines.getToken(masterApp, true),
        IntegrationTestUtils.ASYNC_WAIT_TIME_MS, TimeUnit.MILLISECONDS).getToken();
    provider.setToken(token);

    DatabaseError err = new WriteFuture(ref.child("any_auth"), true).timedGet();
    assertNull(err);
  }
}
