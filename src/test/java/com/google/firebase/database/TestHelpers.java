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

package com.google.firebase.database;

import static com.cedarsoftware.util.DeepEquals.deepEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.connection.ConnectionAuthTokenProvider;
import com.google.firebase.database.connection.ConnectionContext;
import com.google.firebase.database.core.CoreTestHelpers;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.Repo;
import com.google.firebase.database.core.RepoManager;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.future.WriteFuture;
import com.google.firebase.database.logging.DefaultLogger;
import com.google.firebase.database.logging.Logger.Level;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.util.GAuthToken;
import com.google.firebase.database.util.JsonMapper;
import com.google.firebase.database.utilities.DefaultRunLoop;
import com.google.firebase.internal.NonNull;
import com.google.firebase.testing.TestUtils;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class TestHelpers {

  public static DatabaseConfig newFrozenTestConfig(FirebaseApp app) {
    DatabaseConfig cfg = newTestConfig(app);
    CoreTestHelpers.freezeContext(cfg);
    return cfg;
  }

  public static DatabaseConfig newTestConfig(FirebaseApp app) {
    DatabaseConfig config = new DatabaseConfig();
    config.setLogLevel(Logger.Level.WARN);
    config.setFirebaseApp(app);
    return config;
  }

  public static void interruptConfig(final DatabaseConfig config) throws InterruptedException {
    RepoManager.interrupt(config);
    long now = System.currentTimeMillis();
    synchronized (config) {
      while (System.currentTimeMillis() - now < TestUtils.TEST_TIMEOUT_MILLIS) {
        if (config.isStopped()) {
          break;
        }
        config.wait(10);
      }
    }
  }

  public static DatabaseConfig getDatabaseConfig(FirebaseApp app) {
    return FirebaseDatabase.getInstance(app).getConfig();
  }

  public static ScheduledExecutorService getExecutorService(DatabaseConfig config) {
    DefaultRunLoop runLoop = (DefaultRunLoop) config.getRunLoop();
    return runLoop.getExecutorService();
  }

  public static void setLogger(
      DatabaseConfig ctx, com.google.firebase.database.logging.Logger logger) {
    ctx.setLogger(logger);
  }

  public static void waitFor(Semaphore semaphore) throws InterruptedException {
    waitFor(semaphore, 1);
  }

  public static void waitFor(Semaphore semaphore, int count) throws InterruptedException {
    waitFor(semaphore, count, TestUtils.TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
  }

  public static void waitFor(Semaphore semaphore, int count, long timeout, TimeUnit unit)
      throws InterruptedException {
    boolean success = semaphore.tryAcquire(count, timeout, unit);
    assertTrue("Operation timed out", success);
  }

  public static DataSnapshot getSnap(Query ref) throws InterruptedException {

    final Semaphore semaphore = new Semaphore(0);

    // Hack to get around final reference issue
    final List<DataSnapshot> snapshotList = new ArrayList<>(1);

    ref.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            snapshotList.add(snapshot);
            semaphore.release(1);
          }

          @Override
          public void onCancelled(DatabaseError error) {
            semaphore.release(1);
          }
        });

    semaphore.tryAcquire(1, TestUtils.TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    return snapshotList.get(0);
  }

  public static Map<String, Object> fromJsonString(String json) {
    try {
      return JsonMapper.parseJson(json);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Map<String, Object> fromSingleQuotedString(String json) {
    return fromJsonString(json.replace("'", "\""));
  }

  public static void waitForRoundtrip(DatabaseReference reader) {
    try {
      new WriteFuture(reader.getRoot().child(UUID.randomUUID().toString()), null, null).timedGet();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void waitForQueue(DatabaseReference ref) {
    try {
      final Semaphore semaphore = new Semaphore(0);
      ref.getRepo()
          .scheduleNow(
              new Runnable() {
                @Override
                public void run() {
                  semaphore.release();
                }
              });
      semaphore.acquire();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static String repeatedString(String s, int n) {
    StringBuilder result = new StringBuilder("");

    for (int i = 0; i < n; i++) {
      result.append(s);
    }
    return result.toString();
  }

  // Create a (test) object which places a test value at the end of the
  // object path (e.g., a/b/c would yield {a: {b: {c: "test_value"}}}
  public static Map<String, Object> buildObjFromPath(Path path, Object testValue) {
    final HashMap<String, Object> result = new HashMap<>();

    HashMap<String, Object> parent = result;
    for (Iterator<ChildKey> i = path.iterator(); i.hasNext(); ) {
      ChildKey key = i.next();
      if (i.hasNext()) {
        HashMap<String, Object> child = new HashMap<>();
        parent.put(key.asString(), child);
        parent = child;
      } else {
        parent.put(key.asString(), testValue);
      }
    }

    return result;
  }

  // Lookup the value at the path in HashMap (e.g., "a/b/c").
  public static Object applyPath(Object value, Path path) {
    for (ChildKey key : path) {
      value = ((Map) value).get(key.asString());
    }
    return value;
  }

  public static void assertContains(String str, String substr) {
    assertTrue("'" + str + "' does not contain '" + substr + "'.", str.contains(substr));
  }

  public static ChildKey ck(String childKey) {
    return ChildKey.fromString(childKey);
  }

  public static Path path(String path) {
    return new Path(path);
  }

  public static QuerySpec defaultQueryAt(String path) {
    return QuerySpec.defaultQueryAtPath(new Path(path));
  }

  public static Set<ChildKey> childKeySet(String... stringKeys) {
    Set<ChildKey> childKeys = new HashSet<>();
    for (String k : stringKeys) {
      childKeys.add(ChildKey.fromString(k));
    }
    return childKeys;
  }

  public static void setHijackHash(DatabaseReference ref, boolean hijackHash) {
    ref.setHijackHash(hijackHash);
  }

  /**
   * Deeply compares two (2) objects. This method will call any overridden equals() methods if they
   * exist. If not, it will then proceed to do a field-by-field comparison, and when a non-primitive
   * field is encountered, recursively continue the deep comparison. When an array is found, it will
   * also ensure that the array contents are deeply equal, not requiring the array instance
   * (container) to be identical. This method will successfully compare object graphs that have
   * cycles (A->B->C->A). There is no need to ever use the Arrays.deepEquals() method as this is
   * a true and more effective super set.
   */
  public static void assertDeepEquals(Object a, Object b) {
    if (!deepEquals(a, b)) {
      fail("Values different.\nExpected: " + a + "\nActual: " + b);
    }
  }

  /**
   * Instruments the given FirebaseApp instance to catch exceptions that are thrown by background
   * threads. More specifically, it registers error handlers with the RunLoop and EventTarget
   * of the FirebaseDatabase. These components run asynchronously, and therefore any exceptions
   * (including assertion failures) encountered by them do not typically cause the test runner
   * to fail. The error handlers added by this method help to catch those exceptions, and
   * propagate them to the test runner's main thread, thus causing tests to fail on async errors.
   * Integration tests, particularly the ones that interact with FirebaseDatabase, should
   * call this method in a Before test fixture.
   *
   * @param app A FirebaseApp instance to be instrumented
   */
  public static void wrapForErrorHandling(@NonNull FirebaseApp app) {
    DatabaseConfig context = getDatabaseConfig(app);
    CoreTestHelpers.freezeContext(context);
    DefaultRunLoop runLoop = (DefaultRunLoop) context.getRunLoop();
    runLoop.setExceptionHandler(new TestExceptionHandler());
    CoreTestHelpers.setEventTargetExceptionHandler(context, new TestExceptionHandler());
  }

  /**
   * Checks to see if any asynchronous error handlers added to the given FirebaseApp instance
   * have been activated. If so, this method will re-throw the root cause exception as a new
   * RuntimeException. Finally, this method also removes any error handlers added previously by
   * the wrapForErrorHandling method. Invoke this method in integration tests from an After
   * test fixture.
   *
   * @param app AFireabseApp instance already instrumented by wrapForErrorHandling
   */
  public static void assertAndUnwrapErrorHandlers(FirebaseApp app) {
    DatabaseConfig context = getDatabaseConfig(app);
    DefaultRunLoop runLoop = (DefaultRunLoop) context.getRunLoop();
    try {
      TestExceptionHandler handler = (TestExceptionHandler) runLoop.getExceptionHandler();
      Throwable error = handler.throwable.get();
      if (error != null) {
        throw new RuntimeException(error);
      }

      handler = (TestExceptionHandler) CoreTestHelpers.getEventTargetExceptionHandler(context);
      error = handler.throwable.get();
      if (error != null) {
        throw new RuntimeException(error);
      }
    } finally {
      CoreTestHelpers.setEventTargetExceptionHandler(context, null);
      runLoop.setExceptionHandler(null);
    }
  }

  public static void assertTimeDelta(long timestamp) {
    assertTrue(Math.abs(System.currentTimeMillis() - timestamp) < TestUtils.TEST_TIMEOUT_MILLIS);
  }

  public static Repo mockRepo() {
    Repo repo = Mockito.mock(Repo.class);
    Mockito.doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Runnable runnable = invocation.getArgument(0);
        runnable.run();
        return null;
      }
    }).when(repo).scheduleNow(Mockito.any(Runnable.class));
    return repo;
  }

  public static ConnectionContext newConnectionContext(ScheduledExecutorService executor) {
    com.google.firebase.database.logging.Logger logger = new DefaultLogger(
        Level.NONE, ImmutableList.<String>of());
    ConnectionAuthTokenProvider tokenProvider = new ConnectionAuthTokenProvider() {
      @Override
      public void getToken(boolean forceRefresh, GetTokenCallback callback) {
        callback.onSuccess("gauth|{\"token\":\"test-token\"}");
      }
    };
    return new ConnectionContext(logger, tokenProvider, executor, false, "testVersion",
        "testUserAgent", Executors.defaultThreadFactory());
  }

  private static class TestExceptionHandler implements UncaughtExceptionHandler {

    private final AtomicReference<Throwable> throwable = new AtomicReference<>();

    @Override
    public void uncaughtException(Thread t, Throwable e) {
      throwable.compareAndSet(null, e);
    }
  }
}
