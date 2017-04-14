package com.google.firebase.database;

import static com.cedarsoftware.util.DeepEquals.deepEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.database.core.CoreTestHelpers;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.EventTarget;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.future.WriteFuture;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.util.JsonMapper;
import com.google.firebase.database.utilities.DefaultRunLoop;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class TestHelpers {

  private static boolean appInitialized = false;

  public static DatabaseConfig newFrozenTestConfig() {
    DatabaseConfig cfg = newTestConfig();
    CoreTestHelpers.freezeContext(cfg);
    return cfg;
  }

  public static DatabaseConfig newTestConfig() {
    if (!appInitialized) {
      appInitialized = true;
      FirebaseApp.initializeApp(
          new FirebaseOptions.Builder()
              .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
              .setDatabaseUrl("http://admin-java-sdk.firebaseio.com")
              .build());
    }
    return newTestConfig(FirebaseApp.getInstance());
  }

  private static DatabaseConfig newTestConfig(FirebaseApp app) {
    TestRunLoop runLoop = new TestRunLoop();
    DatabaseConfig config = new DatabaseConfig();
    config.setLogLevel(Logger.Level.WARN);
    config.setEventTarget(new TestEventTarget());
    config.setRunLoop(runLoop);
    config.setFirebaseApp(app);
    config.setAuthTokenProvider(new TestTokenProvider(runLoop.getExecutorService()));
    return config;
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

  public static <T> Set<T> asSet(List<T> list) {
    return new HashSet<>(list);
  }

  public static <T> Set<T> asSet(T... objects) {
    return new HashSet<>(Arrays.asList(objects));
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

  private static class TestEventTarget implements EventTarget {

    AtomicReference<Throwable> caughtException = new AtomicReference<>(null);

    int poolSize = 1;
    BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(poolSize, poolSize, 0, TimeUnit.NANOSECONDS, queue,
          new ThreadFactory() {
            ThreadFactory wrappedFactory = Executors.defaultThreadFactory();

            @Override
            public Thread newThread(Runnable r) {
              Thread thread = wrappedFactory.newThread(r);
              thread.setName("FirebaseDatabaseTestsEventTarget");
              // TODO: should we set an uncaught exception handler here? Probably want to let
              // exceptions happen...
              thread.setUncaughtExceptionHandler(
                  new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread t, Throwable e) {
                      e.printStackTrace();
                      caughtException.set(e);
                    }
                  });
              return thread;
            }
          });

    @Override
    public void postEvent(Runnable r) {
      executor.execute(r);
    }

    @Override
    public void shutdown() {}

    @Override
    public void restart() {}
  }

  private static class TestRunLoop extends DefaultRunLoop {

    AtomicReference<Throwable> caughtException = new AtomicReference<>(null);

    @Override
    public void handleException(Throwable e) {
      e.printStackTrace();
      caughtException.set(e);
    }
  }
}
