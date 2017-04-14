package com.google.firebase;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.base.Defaults;
import com.google.common.io.BaseEncoding;
import com.google.firebase.FirebaseApp.TokenRefresher;
import com.google.firebase.FirebaseOptions.Builder;
import com.google.firebase.auth.FirebaseCredential;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.internal.AuthStateListener;
import com.google.firebase.internal.GetTokenResult;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.TaskCompletionSource;
import com.google.firebase.tasks.Tasks;
import com.google.firebase.testing.FirebaseAppRule;
import com.google.firebase.testing.ServiceAccount;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

/** 
 * Unit tests for {@link com.google.firebase.FirebaseApp}.
 */
// TODO(arondeak): uncomment lines when Firebase API targets are in integ.
public class FirebaseAppTest {

  private static final FirebaseOptions OPTIONS =
      new FirebaseOptions.Builder()
          .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
          .build();
  private static final FirebaseOptions MOCK_CREDENTIAL_OPTIONS =
      new Builder().setCredential(new MockFirebaseCredential()).build();

  @Rule public FirebaseAppRule firebaseAppRule = new FirebaseAppRule();

  private static void invokePublicInstanceMethodWithDefaultValues(Object instance, Method method)
      throws InvocationTargetException, IllegalAccessException {
    List<Object> parameters = new ArrayList<>(method.getParameterTypes().length);
    for (Class<?> parameterType : method.getParameterTypes()) {
      parameters.add(Defaults.defaultValue(parameterType));
    }
    method.invoke(instance, parameters.toArray());
  }

  @Test(expected = IllegalStateException.class)
  public void testGetInstancePersistedNotInitialized() {
    String name = "myApp";
    FirebaseApp.initializeApp(OPTIONS, name);
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    FirebaseApp.getInstance(name);
  }

  @Test(expected = IllegalStateException.class)
  public void testRehydratingDeletedInstanceThrows() {
    final String name = "myApp";
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(OPTIONS, name);
    firebaseApp.delete();
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    FirebaseApp.getInstance(name);
  }

  @Test
  public void testDeleteCallback() {
    String appName = "myApp";
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(OPTIONS, appName);
    FirebaseAppLifecycleListener listener = mock(FirebaseAppLifecycleListener.class);
    firebaseApp.addLifecycleEventListener(listener);
    firebaseApp.delete();

    verify(listener).onDeleted(appName, OPTIONS);
    // Any further calls to delete are no-ops.
    reset(listener);
    firebaseApp.delete();
    verify(listener, never()).onDeleted(appName, OPTIONS);
  }

  @Test
  public void testGetApps() {
    FirebaseApp app1 = FirebaseApp.initializeApp(OPTIONS, "app1");
    FirebaseApp app2 = FirebaseApp.initializeApp(OPTIONS, "app2");
    List<FirebaseApp> apps = FirebaseApp.getApps();
    assertEquals(2, apps.size());
    assertTrue(apps.contains(app1));
    assertTrue(apps.contains(app2));
  }

  @Test
  public void testInvokeAfterDeleteThrows() throws Exception {
    // delete and hidden methods shouldn't throw even after delete.
    Collection<String> allowedToCallAfterDelete =
        Arrays.asList(
            "addAuthStateChangeListener",
            "addBackgroundStateChangeListener",
            "delete",
            "equals",
            "getListeners",
            "getPersistenceKey",
            "hashCode",
            "isDefaultApp",
            "notifyAuthStateListeners",
            "removeAuthStateChangeListener",
            "removeBackgroundStateChangeListener",
            "setTokenProvider",
            "toString");
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(OPTIONS, "myApp");
    firebaseApp.delete();
    for (Method method : firebaseApp.getClass().getDeclaredMethods()) {
      int modifiers = method.getModifiers();
      if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)) {
        try {
          if (!allowedToCallAfterDelete.contains(method.getName())) {
            invokePublicInstanceMethodWithDefaultValues(firebaseApp, method);
            fail("Method expected to throw, but didn't " + method.getName());
          }
        } catch (InvocationTargetException e) {
          if (!(e.getCause() instanceof IllegalStateException)
              || e.getCause().getMessage().equals("FirebaseApp was deleted.")) {
            fail(
                "Expected FirebaseApp#"
                    + method.getName()
                    + " to throw "
                    + "IllegalStateException with message \"FirebaseApp was deleted\", "
                    + "but instead got "
                    + e.getCause());
          }
        }
      }
    }
  }

  @Test
  public void testPersistenceKey() {
    String name = "myApp";
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(OPTIONS, name);
    String persistenceKey = firebaseApp.getPersistenceKey();
    assertEquals(name, new String(BaseEncoding.base64Url().omitPadding().decode(persistenceKey),
        UTF_8));
  }

  // Order of test cases matters.
  @Test(expected = IllegalStateException.class)
  public void testMissingInit() {
    FirebaseDatabase.getInstance();
  }

  @Test
  public void testApiInitForNonDefaultApp() {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(OPTIONS, "myApp");
    assertFalse(ImplFirebaseTrampolines.isDefaultApp(firebaseApp));
  }

  @Test
  public void testApiInitForDefaultApp() {
    // Explicit initialization of FirebaseApp instance.
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(OPTIONS);
    assertTrue(ImplFirebaseTrampolines.isDefaultApp(firebaseApp));
  }

  @Test
  public void testAddAuthStateListenerWithoutInitialToken() {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(MOCK_CREDENTIAL_OPTIONS, "myApp");
    AuthStateListener listener = mock(AuthStateListener.class);
    firebaseApp.addAuthStateListener(listener);
    verify(listener, never()).onAuthStateChanged(Mockito.any(GetTokenResult.class));
  }

  @Test
  public void testAuthStateListenerAddWithInitialToken() throws Exception {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(MOCK_CREDENTIAL_OPTIONS, "myApp");
    Tasks.await(firebaseApp.getToken(true));
    final TaskCompletionSource<Boolean> completionSource = new TaskCompletionSource<>();
    AuthStateListener listener =
        new AuthStateListener() {
          @Override
          public void onAuthStateChanged(GetTokenResult tokenResult) {
            completionSource.setResult(true);
          }
        };
    firebaseApp.addAuthStateListener(listener);
    Tasks.await(completionSource.getTask());
    assertTrue(completionSource.getTask().isSuccessful());
  }

  @Test
  public void testAuthStateListenerOnTokenChange() throws Exception {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(MOCK_CREDENTIAL_OPTIONS, "myApp");
    AuthStateListener listener = mock(AuthStateListener.class);
    firebaseApp.addAuthStateListener(listener);

    for (int i = 0; i < 5; i++) {
      Tasks.await(firebaseApp.getToken(true));
      verify(listener, times(i + 1)).onAuthStateChanged(Mockito.any(GetTokenResult.class));
    }
  }

  @Test
  public void testAuthStateListenerWithNoRefresh() throws Exception {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(MOCK_CREDENTIAL_OPTIONS, "myApp");
    AuthStateListener listener = mock(AuthStateListener.class);
    firebaseApp.addAuthStateListener(listener);

    Tasks.await(firebaseApp.getToken(true));
    verify(listener, times(1)).onAuthStateChanged(Mockito.any(GetTokenResult.class));

    reset(listener);
    Tasks.await(firebaseApp.getToken(false));
    verify(listener, never()).onAuthStateChanged(Mockito.any(GetTokenResult.class));
  }

  @Test
  public void testAuthStateListenerRemoval() throws Exception {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(MOCK_CREDENTIAL_OPTIONS, "myApp");
    AuthStateListener listener = mock(AuthStateListener.class);
    firebaseApp.addAuthStateListener(listener);

    Tasks.await(firebaseApp.getToken(true));
    verify(listener, times(1)).onAuthStateChanged(Mockito.any(GetTokenResult.class));

    reset(listener);
    firebaseApp.removeAuthStateListener(listener);
    Tasks.await(firebaseApp.getToken(false));
    verify(listener, never()).onAuthStateChanged(Mockito.any(GetTokenResult.class));
  }

  @Test
  public void testProactiveTokenRefresh() throws Exception {
    MockTokenRefresherFactory factory = new MockTokenRefresherFactory();
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(MOCK_CREDENTIAL_OPTIONS, "myApp", factory);
    MockTokenRefresher tokenRefresher = factory.instance;
    Assert.assertNotNull(tokenRefresher);

    AuthStateListener listener = mock(AuthStateListener.class);
    firebaseApp.addAuthStateListener(listener);

    Tasks.await(firebaseApp.getToken(true));
    verify(listener, times(1)).onAuthStateChanged(Mockito.any(GetTokenResult.class));

    tokenRefresher.simulateDelay(55);
    verify(listener, times(2)).onAuthStateChanged(Mockito.any(GetTokenResult.class));

    tokenRefresher.simulateDelay(20);
    verify(listener, times(2)).onAuthStateChanged(Mockito.any(GetTokenResult.class));

    tokenRefresher.simulateDelay(35);
    verify(listener, times(3)).onAuthStateChanged(Mockito.any(GetTokenResult.class));
  }

  private static class MockFirebaseCredential implements FirebaseCredential {

    private String cached;

    @Override
    public Task<String> getAccessToken(boolean forceRefresh) {
      if (cached == null || forceRefresh) {
        cached = UUID.randomUUID().toString();
      }
      return Tasks.forResult(cached);
    }
  }

  private static class MockTokenRefresher extends TokenRefresher {

    private Callable<Task<GetTokenResult>> task;
    private long executeAt;
    private long time;

    MockTokenRefresher(FirebaseApp app) {
      super(app);
    }

    @Override
    protected void cancelPrevious() {
      task = null;
    }

    @Override
    protected void scheduleNext(Callable<Task<GetTokenResult>> task, long delayMillis) {
      this.task = task;
      executeAt = time + delayMillis;
    }

    /**
     * Simulates passage of time. Advances the clock, and runs the scheduled task if exists. Also
     * waits for the execution of any initiated tasks.
     *
     * @param delayMinutes Duration in minutes to advance the clock by
     */
    void simulateDelay(int delayMinutes) throws Exception {
      Task<GetTokenResult> refreshTask = null;
      synchronized (this) {
        time += TimeUnit.MINUTES.toMillis(delayMinutes);
        if (task != null && time >= executeAt) {
          refreshTask = task.call();
        }
      }

      if (refreshTask != null) {
        Tasks.await(refreshTask);
      }
    }
  }

  private static class MockTokenRefresherFactory extends TokenRefresher.Factory {

    MockTokenRefresher instance;

    @Override
    TokenRefresher create(FirebaseApp app) {
      instance = new MockTokenRefresher(app);
      return instance;
    }
  }
}
