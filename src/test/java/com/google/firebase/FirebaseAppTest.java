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

package com.google.firebase;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.base.Defaults;
import com.google.common.io.BaseEncoding;
import com.google.firebase.FirebaseApp.Clock;
import com.google.firebase.FirebaseApp.TokenRefresher;
import com.google.firebase.FirebaseOptions.Builder;
import com.google.firebase.auth.FirebaseCredential;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.auth.GoogleOAuthAccessToken;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.internal.AuthStateListener;
import com.google.firebase.internal.GetTokenResult;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.TaskCompletionSource;
import com.google.firebase.tasks.Tasks;
import com.google.firebase.testing.FirebaseAppRule;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

/** 
 * Unit tests for {@link com.google.firebase.FirebaseApp}.
 */
// TODO: uncomment lines when Firebase API targets are in integ.
public class FirebaseAppTest {

  private static final FirebaseOptions OPTIONS =
      new FirebaseOptions.Builder()
          .setCredential(TestUtils.getCertCredential(ServiceAccount.EDITOR.asStream()))
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

  @Test(expected = NullPointerException.class)
  public void testNullAppName() {
    FirebaseApp.initializeApp(OPTIONS, null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEmptyAppName() {
    FirebaseApp.initializeApp(OPTIONS, "");
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
  public void testDeleteDefaultApp() {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(OPTIONS);
    assertEquals(firebaseApp, FirebaseApp.getInstance());
    firebaseApp.delete();
    try {
      FirebaseApp.getInstance();
      fail();
    } catch (IllegalStateException expected) {
      // ignore
    } finally {
      TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    }
  }

  @Test
  public void testDeleteApp() {
    final String name = "myApp";
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(OPTIONS, name);
    assertSame(firebaseApp, FirebaseApp.getInstance(name));
    firebaseApp.delete();

    try {
      FirebaseApp.getInstance(name);
      fail();
    } catch (IllegalStateException expected) {
      // ignore
    }

    try {
      // Verify we can reuse the same app name.
      FirebaseApp firebaseApp2 = FirebaseApp.initializeApp(OPTIONS, name);
      assertSame(firebaseApp2, FirebaseApp.getInstance(name));
      assertNotSame(firebaseApp, firebaseApp2);
    } finally {
      TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    }
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
  public void testGetNullApp() {
    FirebaseApp app1 = FirebaseApp.initializeApp(OPTIONS, "app");
    try {
      FirebaseApp.getInstance(null);
      fail("Not thrown");
    } catch (NullPointerException expected) {
      // ignore
    }
  }

  @Test
  public void testToString() throws IOException {
    FirebaseOptions options =
        new FirebaseOptions.Builder()
            .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
            .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "app");
    String pattern = "FirebaseApp\\{name=app}";
    assertTrue(app.toString().matches(pattern));
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
  public void testTokenCaching() throws ExecutionException, InterruptedException, IOException {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(MOCK_CREDENTIAL_OPTIONS, "myApp");
    GetTokenResult token1 = Tasks.await(TestOnlyImplFirebaseTrampolines.getToken(
        firebaseApp, false));
    GetTokenResult token2 = Tasks.await(TestOnlyImplFirebaseTrampolines.getToken(
        firebaseApp, false));
    Assert.assertNotNull(token1);
    Assert.assertNotNull(token2);
    Assert.assertEquals(token1, token2);
  }

  @Test
  public void testTokenForceRefresh() throws ExecutionException, InterruptedException, IOException {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(MOCK_CREDENTIAL_OPTIONS, "myApp");
    GetTokenResult token1 = Tasks.await(TestOnlyImplFirebaseTrampolines.getToken(
        firebaseApp, false));
    GetTokenResult token2 = Tasks.await(TestOnlyImplFirebaseTrampolines.getToken(
        firebaseApp, true));
    Assert.assertNotNull(token1);
    Assert.assertNotNull(token2);
    Assert.assertNotEquals(token1, token2);
  }

  @Test
  public void testTokenExpiration() throws ExecutionException, InterruptedException, IOException {
    TestClock clock = new TestClock();
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(new Builder()
        .setCredential(new ClockedMockFirebaseCredential(clock)).build(), "myApp",
        FirebaseApp.DEFAULT_TOKEN_REFRESHER_FACTORY, clock);
    GetTokenResult token1 = Tasks.await(TestOnlyImplFirebaseTrampolines.getToken(
        firebaseApp, false));
    GetTokenResult token2 = Tasks.await(TestOnlyImplFirebaseTrampolines.getToken(
        firebaseApp, false));
    Assert.assertNotNull(token1);
    Assert.assertNotNull(token2);
    Assert.assertEquals(token1, token2);

    clock.timestamp += TimeUnit.HOURS.toMillis(1);
    GetTokenResult token3 = Tasks.await(TestOnlyImplFirebaseTrampolines.getToken(
        firebaseApp, false));
    Assert.assertNotNull(token3);
    Assert.assertNotEquals(token1, token3);
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
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(MOCK_CREDENTIAL_OPTIONS, "myApp",
        factory, FirebaseApp.DEFAULT_CLOCK);
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

  @Test(expected = IllegalArgumentException.class)
  public void testFirebaseExceptionNullDetail() {
    new FirebaseException(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFirebaseExceptionEmptyDetail() {
    new FirebaseException("");
  }


  private static class MockFirebaseCredential implements FirebaseCredential {
    @Override
    public Task<GoogleOAuthAccessToken> getAccessToken() {
      return Tasks.forResult(new GoogleOAuthAccessToken(UUID.randomUUID().toString(),
          System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)));
    }
  }

  private static class ClockedMockFirebaseCredential implements FirebaseCredential {

    private final TestClock clock;

    ClockedMockFirebaseCredential(TestClock clock) {
      this.clock = clock;
    }

    @Override
    public Task<GoogleOAuthAccessToken> getAccessToken() {
      return Tasks.forResult(new GoogleOAuthAccessToken(UUID.randomUUID().toString(),
          clock.now() + TimeUnit.HOURS.toMillis(1)));
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

  private static class TestClock extends FirebaseApp.Clock {

    private long timestamp;

    @Override
    long now() {
      return timestamp;
    }
  }
}
