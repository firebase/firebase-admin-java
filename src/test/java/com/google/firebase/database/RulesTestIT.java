package com.google.firebase.database;

import com.firebase.security.token.TokenGenerator;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.RepoManager;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.database.future.WriteFuture;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class RulesTestIT {

  private static final Map<String, Object> testRules;
  private static final ObjectMapper mapper = new ObjectMapper();

  static {
    testRules =
        new MapBuilder()
            .put("read_only", new MapBuilder().put(".read", true).build())
            .put("write_only", new MapBuilder().put(".write", true).build())
            .put("read_and_write", new MapBuilder().put(".write", true).put(".read", true)
                .build())
            .put(
                "any_auth",
                new MapBuilder().put(".write", "auth != null").put(".read", "auth != null")
                    .build())
            .put(
                "revocable",
                new MapBuilder()
                    .put(".write", true)
                    .put(
                        ".read",
                        "data.child('public').val() == true && data.child('hidden').val() != " +
                            "true")
                    .build())
            .put(
                "users",
                new MapBuilder()
                    .put(".write", true)
                    .put(".read", true)
                    .put(
                        "$user",
                        new MapBuilder()
                            .put(".validate", "newData.hasChildren(['name', 'age'])")
                            .put(
                                "name",
                                new MapBuilder()
                                    .put(".validate", "newData.isString() == true")
                                    .build())
                            .put(
                                "age",
                                new MapBuilder()
                                    .put(".validate", "newData.isNumber() && newData.val() > " +
                                        "13")
                                    .build())
                            .build())
                    .build())
            .build();
    /*@"home": @{
            @"$user": @{
                @".read": @"auth != null && auth.user == $user",
                @".write": @"auth != null && auth.user == $user"
            }
        },
        @"has_priority": @{
            @".write": @YES,
            @".validate": @"newData.getPriority() != null"
        },
        @"increment_only": @{
            @".read": @YES,
            @".write": @"(!data.exists() && newData.val() == 0) || (newData.val() == data.val() + 1)
        },
        @"equal_children": @{
            @".write": @YES,
            @".validate": @"newData.child('a').val() == newData.child('b').val()"
        }
    };*/
  }

  private DatabaseReference reader;

  private DatabaseReference writer;

  @Before
  public void setUp() throws IOException {
    List<DatabaseReference> refs = TestHelpers.getRandomNode(2);
    reader = refs.get(0);
    writer = refs.get(1);
    String rules =
        mapper.writeValueAsString(
            new MapBuilder()
                .put("rules", new MapBuilder().put(writer.getKey(), testRules).build())
                .build());

    TestHelpers.uploadRules(writer, rules);
    TestHelpers.waitForRoundtrip(writer.getRoot());
  }

  @After
  public void tearDown() throws IOException {
    TestHelpers.uploadRules(writer, TestConstants.DEFAULT_RULES_STRING);
    TestHelpers.waitForRoundtrip(writer.getRoot());
    TestHelpers.failOnFirstUncaughtException();
  }

  @Test
  public void writeOperationSetsErrorOnFailure() throws InterruptedException {
    DatabaseReference ref = writer.child("read_only");

    final Semaphore semaphore = new Semaphore(0);
    ref.setValue(
        "value",
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            assertNotNull(error);
            semaphore.release(1);
          }
        });

    TestHelpers.waitFor(semaphore);

    // All of the other writing methods are just testing the server. We're just testing the
    // error
    // propagation
  }

  @Test
  public void failedListensDontDisruptOtherListens()
      throws TestFailure, TimeoutException, InterruptedException {
    // Wait until we're connected
    ReadFuture.untilEquals(reader.getRoot().child(".info/connected"), true).timedGet();

    final Semaphore semaphore = new Semaphore(0);
    reader.addValueEventListener(
        new ValueEventListener() {
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
    ValueEventListener listener =
        reader
            .child("read_and_write")
            .addValueEventListener(
                new ValueEventListener() {
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
    ValueEventListener listener =
        reader
            .child("read_and_write")
            .addValueEventListener(
                new ValueEventListener() {
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
    reader.addValueEventListener(
        new ValueEventListener() {
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
    new WriteFuture(
        writer.child("revocable"), new MapBuilder().put("public", true).put("data", 1).build())
        .timedGet();

    final AtomicBoolean valueHit = new AtomicBoolean(false);
    final Semaphore semaphore = new Semaphore(0);
    reader
        .child("revocable/data")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                assertTrue(valueHit.compareAndSet(false, true));
                assertEquals(1, (int) snapshot.getValue(Integer.class));
                semaphore.release(1);
              }

              @Override
              public void onCancelled(DatabaseError error) {
                semaphore.release(1);
              }
            });

    TestHelpers.waitFor(semaphore);
    writer.child("revocable/public").setValue(false);
    writer.child("revocable/data").setValue(2);
    TestHelpers.waitFor(semaphore);
    assertTrue(valueHit.get());

    writer.child("revocable/public").setValue(true);
    new WriteFuture(writer.child("revocable/data"), 3).timedGet();

    // Ok, the listen was cancelled, create a new one now.
    ValueEventListener listener =
        reader
            .child("revocable/data")
            .addValueEventListener(
                new ValueEventListener() {
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
    ReadFuture future =
        new ReadFuture(
            fredRef.child("age"),
            new ReadFuture.CompletionCondition() {
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

    fredRef
        .child("age")
        .setValue(
            12,
            new DatabaseReference.CompletionListener() {
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
    ReadFuture future =
        new ReadFuture(
            fredRef.child("age"),
            new ReadFuture.CompletionCondition() {
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

    Map<String, Object> update = new MapBuilder().put("age", 12).build();
    fredRef.updateChildren(
        update,
        new DatabaseReference.CompletionListener() {
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
  public void stillAuthedAfterReconnect()
      throws InterruptedException, ExecutionException, TestFailure, TimeoutException,
      JSONException {
    DatabaseConfig config = TestHelpers.newTestConfig();
    TestTokenProvider provider = new TestTokenProvider(TestHelpers.getExecutorService(config));
    config.setAuthTokenProvider(provider);

    DatabaseReference root = TestHelpers.rootWithConfig(config);

    TokenGenerator generator = new TokenGenerator(TestHelpers.getTestSecret());
    Map<String, Object> authObj = new HashMap<>();
    authObj.put("uid", "1");
    authObj.put("foo", true);
    String token = generator.createToken(authObj);
    TestHelpers.assertSuccessfulAuth(root, provider, token);

    DatabaseReference ref = root.child(writer.getPath().toString());

    RepoManager.interrupt(config);

    RepoManager.resume(config);

    DatabaseError err = new WriteFuture(ref.child("any_auth"), true).timedGet();
    assertNull(err);
  }

  @Test
  public void connectionIsAuthedImmediatelyAfterTokenChange() throws Exception {
    DatabaseConfig config = TestHelpers.newTestConfig();
    TestTokenProvider provider = new TestTokenProvider(TestHelpers.getExecutorService(config));
    config.setAuthTokenProvider(provider);

    DatabaseReference root = TestHelpers.rootWithConfig(config);
    DatabaseReference ref = root.child(writer.getPath().toString());

    TokenGenerator generator = new TokenGenerator(TestHelpers.getTestSecret());
    Map<String, Object> authObj = new HashMap<>();
    authObj.put("uid", "1");
    String token = generator.createToken(authObj);
    provider.setToken(token);

    DatabaseError err = new WriteFuture(ref.child("any_auth"), true).timedGet();
    assertNull(err);
  }
}
