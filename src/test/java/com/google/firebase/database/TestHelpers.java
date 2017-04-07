package com.google.firebase.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.database.core.Context;
import com.google.firebase.database.core.CoreTestHelpers;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.EventTarget;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.persistence.PersistenceManager;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.future.WriteFuture;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.utilities.DefaultRunLoop;
import com.google.firebase.testing.ServiceAccount;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
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
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

public class TestHelpers {

  private static List<DatabaseConfig> contexts = new ArrayList<>();
  private static String testSecret = null;
  private static boolean appInitialized = false;

  public static void failOnFirstUncaughtException() {
    for (Context ctx : contexts) {
      TestEventTarget eventTarget = (TestEventTarget) ctx.getEventTarget();
      Throwable t = eventTarget.caughtException.getAndSet(null);
      if (t != null) {
        t.printStackTrace();
        fail("Found error on event target");
      }
      TestRunLoop runLoop = (TestRunLoop) ctx.getRunLoop();
      t = runLoop.caughtException.getAndSet(null);
      if (t != null) {
        t.printStackTrace();
        fail("Found error on run loop");
      }
    }
  }

  public static DatabaseConfig getContext(int i) {
    ensureContexts(i + 1);
    return contexts.get(i);
  }

  public static DatabaseReference rootWithNewContext() throws DatabaseException {
    return rootWithConfig(newTestConfig());
  }

  public static DatabaseReference rootWithConfig(DatabaseConfig config) {
    return new DatabaseReference(TestConstants.TEST_NAMESPACE, config);
  }

  public static DatabaseReference getRandomNode() throws DatabaseException {
    return getRandomNode(1).get(0);
  }

  public static List<DatabaseReference> getRandomNode(int count) throws DatabaseException {
    ensureContexts(count);

    List<DatabaseReference> results = new ArrayList<>(count);
    String name = null;
    for (int i = 0; i < count; ++i) {
      DatabaseReference ref = new DatabaseReference(TestConstants.TEST_NAMESPACE, contexts.get(i));
      if (name == null) {
        name = ref.push().getKey();
      }
      results.add(ref.child(name));
    }
    return results;
  }

  public static void goOffline(DatabaseConfig cfg) {
    DatabaseReference.goOffline(cfg);
  }

  public static void goOnline(DatabaseConfig cfg) {
    DatabaseReference.goOnline(cfg);
  }

  public static DatabaseConfig newFrozenTestConfig() {
    DatabaseConfig cfg = newTestConfig();
    CoreTestHelpers.freezeContext(cfg);
    return cfg;
  }

  public static DatabaseConfig newTestConfig() {
    TestHelpers.ensureAppInitialized();
    return newTestConfig(FirebaseApp.getInstance());
  }

  public static DatabaseConfig newTestConfig(FirebaseApp app) {
    TestRunLoop runLoop = new TestRunLoop();
    DatabaseConfig config = new DatabaseConfig();
    config.setLogLevel(Logger.Level.DEBUG);
    config.setEventTarget(new TestEventTarget());
    config.setRunLoop(runLoop);
    config.setFirebaseApp(app);
    config.setAuthTokenProvider(new TestTokenProvider(runLoop.getExecutorService()));
    return config;
  }

  public static ScheduledExecutorService getExecutorService(DatabaseConfig config) {
    DefaultRunLoop runLoop = (DefaultRunLoop) config.getRunLoop();
    return runLoop.getExecutorService();
  }

  public static void setForcedPersistentCache(Context ctx, PersistenceManager manager) {
    try {
      Method method =
          Context.class.getDeclaredMethod("forcePersistenceManager", PersistenceManager.class);
      method.setAccessible(true);
      method.invoke(ctx, manager);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void setLogger(
      DatabaseConfig ctx, com.google.firebase.database.logging.Logger logger) {
    ctx.setLogger(logger);
  }

  private static void ensureContexts(int count) {
    for (int i = contexts.size(); i < count; ++i) {
      contexts.add(newTestConfig());
    }
  }

  public static String getTestSecret() {
    if (testSecret == null) {
      try {
        InputStream response =
            new URL(TestConstants.TEST_NAMESPACE + "/.nsadmin/.json?key=1234").openStream();
        TypeReference<Map<String, Object>> t = new TypeReference<Map<String, Object>>() {};
        Map<String, Object> data = new ObjectMapper().readValue(response, t);
        testSecret = (String) ((List) data.get("secrets")).get(0);
      } catch (Throwable e) {
        fail("Could not get test secret.");
      }
    }
    return testSecret;
  }

  public static void waitFor(Semaphore semaphore) throws InterruptedException {
    waitFor(semaphore, 1);
  }

  public static void waitFor(Semaphore semaphore, int count) throws InterruptedException {
    waitFor(semaphore, count, TestConstants.TEST_TIMEOUT, TimeUnit.MILLISECONDS);
  }

  public static void waitFor(Semaphore semaphore, int count, long timeout, TimeUnit unit)
      throws InterruptedException {
    boolean success = semaphore.tryAcquire(count, timeout, unit);
    failOnFirstUncaughtException();
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

    semaphore.tryAcquire(1, TestConstants.TEST_TIMEOUT, TimeUnit.MILLISECONDS);
    return snapshotList.get(0);
  }

  public static void uploadRules(DatabaseReference ref, String rules) throws IOException {
    // TODO: There's some weird flakiness in the rules tests where sometimes most of the tests
    // fail in such a way that it *seems* like the rules didn't successfully upload or got
    // replaced mid-test.  Until we've figured it out, I've added some dumb upload logging.
    System.out.println("UPLOADING RULES.");
    HttpClient httpClient = new DefaultHttpClient();
    HttpEntity entity = new StringEntity(rules, "UTF-8");
    String url = ref.getRoot().toString() + "/.settings/rules.json?auth=" + getTestSecret();
    HttpPut put = new HttpPut(url);
    put.setEntity(entity);

    HttpParams httpParameters = httpClient.getParams();
    httpParameters.setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, false);
    HttpConnectionParams.setTcpNoDelay(httpParameters, true);

    HttpResponse response = httpClient.execute(put);
    assertTrue(response.getStatusLine().getStatusCode() == 200);
    if (rules.length() < 100) {
      System.out.println("UPLOADED default rules.");
    } else {
      System.out.println("UPLOADED custom rules.");
    }
  }

  public static void assertSuccessfulAuth(
      DatabaseReference ref, TestTokenProvider provider, String credential) {
    DatabaseReference authRef = ref.getRoot().child(".info/authenticated");
    final Semaphore semaphore = new Semaphore(0);
    final List<Boolean> authStates = new ArrayList<>();
    ValueEventListener listener =
        authRef.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                authStates.add(snapshot.getValue(Boolean.class));
                semaphore.release();
              }

              @Override
              public void onCancelled(DatabaseError error) {
                throw new RuntimeException("Should not happen");
              }
            });

    provider.setToken(credential);

    try {
      TestHelpers.waitFor(semaphore, 2);
      assertEquals(Arrays.asList(false, true), authStates);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    authRef.removeEventListener(listener);
  }

  public static Map<String, Object> fromJsonString(String json) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
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

  public static void waitForEvents(DatabaseReference ref) {
    try {
      // Make sure queue is done and all events are queued
      TestHelpers.waitForQueue(ref);
      // Next, all events were queued, make sure all events are done raised
      final Semaphore semaphore = new Semaphore(0);
      ref.getRepo()
          .postEvent(
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
    String result = "";

    for (int i = 0; i < n; i++) {
      result += s;
    }
    return result;
  }

  // Create a (test) object which places a test value at then end of the
  // object path (e.g., a/b/c would yield {a: {b: {c: "test_value"}}}
  public static HashMap<String, Object> buildObjFromPath(Path path, Object testValue) {
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
  @SuppressWarnings("unchecked")
  public static Object applyPath(Object value, Path path) {
    for (ChildKey key : path) {
      value = ((HashMap<String, Object>) value).get(key.asString());
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
    HashSet<ChildKey> childKeys = new HashSet<>();
    for (String k : stringKeys) {
      childKeys.add(ChildKey.fromString(k));
    }
    return childKeys;
  }

  public static void ensureAppInitialized() {
    if (!appInitialized) {
      appInitialized = true;
      FirebaseApp.initializeApp(
          new FirebaseOptions.Builder()
              .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
              .setDatabaseUrl(TestConstants.TEST_NAMESPACE)
              .build());
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
