package com.google.firebase.database;

import static com.cedarsoftware.util.DeepEquals.deepEquals;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.core.CoreTestHelpers;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.future.WriteFuture;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.util.JsonMapper;
import com.google.firebase.database.utilities.DefaultRunLoop;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

  public static void wrapForErrorHandling(FirebaseApp app) {
    DatabaseConfig context = getDatabaseConfig(app);
    CoreTestHelpers.freezeContext(context);
    DefaultRunLoop runLoop = (DefaultRunLoop) context.getRunLoop();
    context.setRunLoop(new ErrorHandlingRunLoop(runLoop));
    CoreTestHelpers.setEventTargetExceptionHandler(context, new TestExceptionHandler());
  }

  public static void assertAndUnwrapErrorHandlers(FirebaseApp app) {
    DatabaseConfig context = getDatabaseConfig(app);
    ErrorHandlingRunLoop runLoop = (ErrorHandlingRunLoop) context.getRunLoop();
    try {
      Throwable error = runLoop.throwable.get();
      if (error != null) {
        throw new RuntimeException(error);
      }

      TestExceptionHandler handler = (TestExceptionHandler) CoreTestHelpers
          .getEventTargetExceptionHandler(context);
      error = handler.throwable.get();
      if (error != null) {
        throw new RuntimeException(error);
      }
    } finally {
      context.setRunLoop(runLoop.wrapped);
      CoreTestHelpers.setEventTargetExceptionHandler(context, null);
    }
  }

  private static class TestExceptionHandler implements UncaughtExceptionHandler {

    private final AtomicReference<Throwable> throwable = new AtomicReference<>();

    @Override
    public void uncaughtException(Thread t, Throwable e) {
      throwable.compareAndSet(null, e);
    }
  }

  private static class ErrorHandlingRunLoop extends DefaultRunLoop {

    private final DefaultRunLoop wrapped;
    private final AtomicReference<Throwable> throwable = new AtomicReference<>();

    ErrorHandlingRunLoop(DefaultRunLoop wrapped) {
      this.wrapped = checkNotNull(wrapped);
    }

    @Override
    public void handleException(Throwable e) {
      try {
        throwable.compareAndSet(null, e);
      } finally {
        wrapped.handleException(e);
      }
    }

    @Override
    public ScheduledExecutorService getExecutorService() {
      return wrapped.getExecutorService();
    }

    @Override
    public void scheduleNow(Runnable runnable) {
      wrapped.scheduleNow(runnable);
    }

    @Override
    public ScheduledFuture schedule(Runnable runnable, long milliseconds) {
      return wrapped.schedule(runnable, milliseconds);
    }

    @Override
    public void shutdown() {
      wrapped.shutdown();
    }

    @Override
    public void restart() {
      super.restart();
    }
  }
}
