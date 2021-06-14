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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.auth.oauth2.OAuth2Credentials.CredentialsChangedListener;
import com.google.common.base.Defaults;
import com.google.common.base.Strings;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp.TokenRefresher;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.internal.ApplicationDefaultCredentialsProvider;
import com.google.firebase.internal.FirebaseProcessEnvironment;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.testing.FirebaseAppRule;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link com.google.firebase.FirebaseApp}.
 */
public class FirebaseAppTest {

  private static final GoogleCredentials TEST_CREDENTIALS = TestUtils.getCertCredential(
      ServiceAccount.EDITOR.asStream());

  private static final FirebaseOptions OPTIONS =
      FirebaseOptions.builder()
          .setCredentials(TEST_CREDENTIALS)
          .build();

  @Rule public FirebaseAppRule firebaseAppRule = new FirebaseAppRule();

  @AfterClass
  public static void tearDownClass() {
    FirebaseProcessEnvironment.clearCache();
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

  @Test
  public void testGetProjectIdFromOptions() {
    FirebaseOptions options = OPTIONS.toBuilder()
        .setProjectId("explicit-project-id")
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "myApp");
    String projectId = ImplFirebaseTrampolines.getProjectId(app);
    assertEquals("explicit-project-id", projectId);
  }

  @Test
  public void testGetProjectIdFromCredential() {
    FirebaseApp app = FirebaseApp.initializeApp(OPTIONS, "myApp");
    String projectId = ImplFirebaseTrampolines.getProjectId(app);
    assertEquals("mock-project-id", projectId);
  }

  @Test
  public void testGetProjectIdFromEnvironment() {
    List<String> variables = ImmutableList.of("GCLOUD_PROJECT", "GOOGLE_CLOUD_PROJECT");
    for (String variable : variables) {
      FirebaseProcessEnvironment.setenv(variable, "project-id-1");
      FirebaseOptions options = FirebaseOptions.builder()
          .setCredentials(new MockGoogleCredentials())
          .build();
      try {
        FirebaseApp app = FirebaseApp.initializeApp(options,"myApp_" + variable);
        String projectId = ImplFirebaseTrampolines.getProjectId(app);
        assertEquals("project-id-1", projectId);
      } finally {
        FirebaseProcessEnvironment.clearCache();
      }
    }
  }

  @Test
  public void testProjectIdEnvironmentVariablePrecedence() {
    Map<String, String> currentValues = new HashMap<>();
    currentValues.put("GCLOUD_PROJECT", Strings.nullToEmpty(
        System.getenv("GCLOUD_PROJECT")));
    currentValues.put("GOOGLE_CLOUD_PROJECT", Strings.nullToEmpty(
        System.getenv("GOOGLE_CLOUD_PROJECT")));

    FirebaseProcessEnvironment.setenv("GCLOUD_PROJECT", "project-id-1");
    FirebaseProcessEnvironment.setenv("GOOGLE_CLOUD_PROJECT", "project-id-2");
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials())
        .build();
    try {
      FirebaseApp app = FirebaseApp.initializeApp(options,"myApp");
      String projectId = ImplFirebaseTrampolines.getProjectId(app);
      assertEquals("project-id-2", projectId);
    } finally {
      FirebaseProcessEnvironment.clearCache();
    }
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
  public void testUseAfterDelete() {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(OPTIONS);
    firebaseApp.delete();

    try {
      firebaseApp.getOptions();
      fail();
    } catch (IllegalStateException expected) {
      // ignore
    }

    try {
      firebaseApp.getProjectId();
      fail();
    } catch (IllegalStateException expected) {
      // ignore
    }

    try {
      ImplFirebaseTrampolines.addService(firebaseApp, new MockFirebaseService());
      fail();
    } catch (IllegalStateException expected) {
      // ignore
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
    FirebaseApp.initializeApp(OPTIONS, "app");
    try {
      FirebaseApp.getInstance(null);
      fail("Not thrown");
    } catch (NullPointerException expected) {
      // ignore
    }
  }

  @Test
  public void testToString() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(OPTIONS, "app");
    String pattern = "FirebaseApp\\{name=app}";
    assertTrue(app.toString().matches(pattern));
  }

  @Test
  public void testInvokeAfterDeleteThrows() throws Exception {
    // delete and hidden methods shouldn't throw even after delete.
    Collection<String> allowedToCallAfterDelete =
        Arrays.asList(
            "delete",
            "equals",
            "getName",
            "getPersistenceKey",
            "hashCode",
            "isDefaultApp",
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
  public void testTokenCaching() {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(getMockCredentialOptions(), "myApp");
    String token1 = TestOnlyImplFirebaseTrampolines.getToken(
        firebaseApp, false);
    String token2 = TestOnlyImplFirebaseTrampolines.getToken(
        firebaseApp, false);
    Assert.assertNotNull(token1);
    Assert.assertNotNull(token2);
    Assert.assertEquals(token1, token2);
  }

  @Test
  public void testTokenForceRefresh() {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(getMockCredentialOptions(), "myApp");
    String token1 = TestOnlyImplFirebaseTrampolines.getToken(firebaseApp, false);
    String token2 = TestOnlyImplFirebaseTrampolines.getToken(firebaseApp, true);
    Assert.assertNotNull(token1);
    Assert.assertNotNull(token2);
    Assert.assertNotEquals(token1, token2);
  }

  @Test
  public void testAddCredentialsChangedListenerWithoutInitialToken() throws IOException {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(getMockCredentialOptions(), "myApp");
    CredentialsChangedListener listener = mock(CredentialsChangedListener.class);
    ImplFirebaseTrampolines.getCredentials(firebaseApp).addChangeListener(listener);
    verify(listener, never()).onChanged(Mockito.any(OAuth2Credentials.class));
  }

  @Test
  public void testCredentialsChangedListenerOnTokenChange() throws Exception {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(getMockCredentialOptions(), "myApp");
    CredentialsChangedListener listener = mock(CredentialsChangedListener.class);
    ImplFirebaseTrampolines.getCredentials(firebaseApp).addChangeListener(listener);

    for (int i = 0; i < 5; i++) {
      TestOnlyImplFirebaseTrampolines.getToken(firebaseApp, true);
      verify(listener, times(i + 1)).onChanged(Mockito.any(OAuth2Credentials.class));
    }
  }

  @Test
  public void testCredentialsChangedListenerWithNoRefresh() throws Exception {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(getMockCredentialOptions(), "myApp");
    CredentialsChangedListener listener = mock(CredentialsChangedListener.class);
    ImplFirebaseTrampolines.getCredentials(firebaseApp).addChangeListener(listener);

    TestOnlyImplFirebaseTrampolines.getToken(firebaseApp, true);
    verify(listener, times(1)).onChanged(Mockito.any(OAuth2Credentials.class));

    reset(listener);
    TestOnlyImplFirebaseTrampolines.getToken(firebaseApp, false);
    verify(listener, never()).onChanged(Mockito.any(OAuth2Credentials.class));
  }

  @Test
  public void testProactiveTokenRefresh() throws Exception {
    MockTokenRefresherFactory factory = new MockTokenRefresherFactory();
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(getMockCredentialOptions(), "myApp",
        factory);
    MockTokenRefresher tokenRefresher = factory.instance;
    Assert.assertNotNull(tokenRefresher);

    CredentialsChangedListener listener = mock(CredentialsChangedListener.class);
    ImplFirebaseTrampolines.getCredentials(firebaseApp).addChangeListener(listener);

    firebaseApp.startTokenRefresher();

    // Since there was no token to begin with, the refresher should refresh the credential
    // immediately.
    tokenRefresher.simulateDelay(0);
    verify(listener, times(1)).onChanged(Mockito.any(OAuth2Credentials.class));

    tokenRefresher.simulateDelay(55);
    verify(listener, times(2)).onChanged(Mockito.any(OAuth2Credentials.class));

    tokenRefresher.simulateDelay(20);
    verify(listener, times(2)).onChanged(Mockito.any(OAuth2Credentials.class));

    tokenRefresher.simulateDelay(35);
    verify(listener, times(3)).onChanged(Mockito.any(OAuth2Credentials.class));
  }

  @Test
  public void testProactiveTokenRefreshWithInitialToken() throws Exception {
    MockTokenRefresherFactory factory = new MockTokenRefresherFactory();
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(getMockCredentialOptions(), "myApp",
        factory);
    MockTokenRefresher tokenRefresher = factory.instance;
    Assert.assertNotNull(tokenRefresher);

    // Get the initial token
    TestOnlyImplFirebaseTrampolines.getToken(firebaseApp, true);

    CredentialsChangedListener listener = mock(CredentialsChangedListener.class);
    ImplFirebaseTrampolines.getCredentials(firebaseApp).addChangeListener(listener);

    firebaseApp.startTokenRefresher();

    // Since there is already a valid token, which won't expire for another hour, the refresher
    // should not refresh the credential at this point in time.
    tokenRefresher.simulateDelay(0);
    verify(listener, never()).onChanged(Mockito.any(OAuth2Credentials.class));

    tokenRefresher.simulateDelay(55);
    verify(listener, times(1)).onChanged(Mockito.any(OAuth2Credentials.class));

    tokenRefresher.simulateDelay(20);
    verify(listener, times(1)).onChanged(Mockito.any(OAuth2Credentials.class));

    tokenRefresher.simulateDelay(35);
    verify(listener, times(2)).onChanged(Mockito.any(OAuth2Credentials.class));
  }

  @Test
  public void testTokenRefresherStateMachine() {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(getMockCredentialOptions(), "myApp");
    CountingTokenRefresher refresher = new CountingTokenRefresher(firebaseApp);
    assertEquals(0, refresher.scheduleCalls);

    // Both schedule and cancel should be called.
    // (we call cancel before each new scheduling attempt)
    refresher.start();
    assertEquals(1, refresher.scheduleCalls);
    assertEquals(1, refresher.cancelCalls);

    // Shouldn't change state
    refresher.start();
    assertEquals(1, refresher.scheduleCalls);
    assertEquals(1, refresher.cancelCalls);

    refresher.stop();
    assertEquals(1, refresher.scheduleCalls);
    assertEquals(2, refresher.cancelCalls);

    // Shouldn't change state
    refresher.start();
    assertEquals(1, refresher.scheduleCalls);
    assertEquals(2, refresher.cancelCalls);

    refresher.stop();
    assertEquals(1, refresher.scheduleCalls);
    assertEquals(2, refresher.cancelCalls);

    // Test for the case where the refresher is stopped without ever starting.
    refresher = new CountingTokenRefresher(firebaseApp);
    // stop() is allowed here, but since we didn't start(), no measurable state change
    // should take place.
    refresher.stop();
    assertEquals(0, refresher.scheduleCalls);
    assertEquals(0, refresher.cancelCalls);
  }

  @Test
  public void testAppWithAuthVariableOverrides() {
    Map<String, Object> authVariableOverrides = ImmutableMap.<String, Object>of("uid", "uid1");
    FirebaseOptions options = getMockCredentialOptions().toBuilder()
        .setDatabaseAuthVariableOverride(authVariableOverrides)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "testGetAppWithUid");
    assertEquals("uid1", app.getOptions().getDatabaseAuthVariableOverride().get("uid"));
    String token = TestOnlyImplFirebaseTrampolines.getToken(app, false);
    Assert.assertFalse(token.isEmpty());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEmptyFirebaseConfigFile() {
    setFirebaseConfigEnvironmentVariable("firebase_config_empty.json");
    FirebaseApp.initializeApp();
  }

  @Test
  public void testEmptyFirebaseConfigString() throws IOException {
    setFirebaseConfigEnvironmentVariable("");
    ApplicationDefaultCredentialsProvider.setApplicationDefault(TEST_CREDENTIALS);
    try {
      FirebaseApp firebaseApp = FirebaseApp.initializeApp();
      assertNull(firebaseApp.getOptions().getProjectId());
      assertNull(firebaseApp.getOptions().getStorageBucket());
      assertNull(firebaseApp.getOptions().getDatabaseUrl());
      assertTrue(firebaseApp.getOptions().getDatabaseAuthVariableOverride().isEmpty());
    } finally {
      ApplicationDefaultCredentialsProvider.setApplicationDefault(null);
    }
  }

  @Test
  public void testEmptyFirebaseConfigJSONObject() throws IOException {
    setFirebaseConfigEnvironmentVariable("{}");
    ApplicationDefaultCredentialsProvider.setApplicationDefault(TEST_CREDENTIALS);
    try {
      FirebaseApp firebaseApp = FirebaseApp.initializeApp();
      assertNull(firebaseApp.getOptions().getProjectId());
      assertNull(firebaseApp.getOptions().getStorageBucket());
      assertNull(firebaseApp.getOptions().getDatabaseUrl());
      assertTrue(firebaseApp.getOptions().getDatabaseAuthVariableOverride().isEmpty());
    } finally {
      ApplicationDefaultCredentialsProvider.setApplicationDefault(null);
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidFirebaseConfigFile() {
    setFirebaseConfigEnvironmentVariable("firebase_config_invalid.json");
    FirebaseApp.initializeApp();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidFirebaseConfigString() {
    setFirebaseConfigEnvironmentVariable("{,,");
    FirebaseApp.initializeApp();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFirebaseConfigMissingFile() {
    setFirebaseConfigEnvironmentVariable("no_such.json");
    FirebaseApp.initializeApp();
  }

  @Test
  public void testFirebaseConfigFileWithSomeKeysMissing() throws IOException {
    setFirebaseConfigEnvironmentVariable("firebase_config_partial.json");
    ApplicationDefaultCredentialsProvider.setApplicationDefault(TEST_CREDENTIALS);
    try {
      FirebaseApp firebaseApp = FirebaseApp.initializeApp();
      assertEquals("hipster-chat-mock", firebaseApp.getOptions().getProjectId());
      assertEquals("https://hipster-chat.firebaseio.mock", firebaseApp.getOptions().getDatabaseUrl());
    } finally {
      ApplicationDefaultCredentialsProvider.setApplicationDefault(null);
    }
  }

  @Test
  public void testValidFirebaseConfigFile() throws IOException {
    setFirebaseConfigEnvironmentVariable("firebase_config.json");
    ApplicationDefaultCredentialsProvider.setApplicationDefault(TEST_CREDENTIALS);
    try {
      FirebaseApp firebaseApp = FirebaseApp.initializeApp();
      assertEquals("hipster-chat-mock", firebaseApp.getOptions().getProjectId());
      assertEquals("hipster-chat.appspot.mock", firebaseApp.getOptions().getStorageBucket());
      assertEquals(
          "https://hipster-chat.firebaseio.mock", firebaseApp.getOptions().getDatabaseUrl());
      assertEquals(
          "testuser", firebaseApp.getOptions().getDatabaseAuthVariableOverride().get("uid"));
    } finally {
      ApplicationDefaultCredentialsProvider.setApplicationDefault(null);
    }
  }

  @Test
  public void testEnvironmentVariableIgnored() {
    setFirebaseConfigEnvironmentVariable("firebase_config.json");
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(OPTIONS);
    assertNull(firebaseApp.getOptions().getProjectId());
    assertNull(firebaseApp.getOptions().getStorageBucket());
    assertNull(firebaseApp.getOptions().getDatabaseUrl());
    assertTrue(firebaseApp.getOptions().getDatabaseAuthVariableOverride().isEmpty());
  }

  @Test
  public void testValidFirebaseConfigString() throws IOException {
    setFirebaseConfigEnvironmentVariable("{"
        + "\"databaseAuthVariableOverride\": {"
        +   "\"uid\":"
        +   "\"testuser\""
        + "},"
        + "\"databaseUrl\": \"https://hipster-chat.firebaseio.mock\","
        + "\"projectId\": \"hipster-chat-mock\","
        + "\"storageBucket\": \"hipster-chat.appspot.mock\""
        + "}");
    ApplicationDefaultCredentialsProvider.setApplicationDefault(TEST_CREDENTIALS);
    try {
      FirebaseApp firebaseApp = FirebaseApp.initializeApp();
      assertEquals("hipster-chat-mock", firebaseApp.getOptions().getProjectId());
      assertEquals("hipster-chat.appspot.mock", firebaseApp.getOptions().getStorageBucket());
      assertEquals("https://hipster-chat.firebaseio.mock", firebaseApp.getOptions().getDatabaseUrl());
      assertEquals("testuser",
          firebaseApp.getOptions().getDatabaseAuthVariableOverride().get("uid"));
    } finally {
      ApplicationDefaultCredentialsProvider.setApplicationDefault(null);
    }
  }

  @Test
  public void testFirebaseConfigFileIgnoresInvalidKey() throws IOException {
    setFirebaseConfigEnvironmentVariable("firebase_config_invalid_key.json");
    ApplicationDefaultCredentialsProvider.setApplicationDefault(TEST_CREDENTIALS);
    try {
      FirebaseApp firebaseApp = FirebaseApp.initializeApp();
      assertEquals("hipster-chat-mock", firebaseApp.getOptions().getProjectId());
    } finally {
      ApplicationDefaultCredentialsProvider.setApplicationDefault(null);
    }
  }

  @Test
  public void testFirebaseConfigStringIgnoresInvalidKey() throws IOException {
    setFirebaseConfigEnvironmentVariable("{"
        + "\"databaseUareL\": \"https://hipster-chat.firebaseio.mock\","
        + "\"projectId\": \"hipster-chat-mock\""
        + "}");
    ApplicationDefaultCredentialsProvider.setApplicationDefault(TEST_CREDENTIALS);
    try {
      FirebaseApp firebaseApp = FirebaseApp.initializeApp();
      assertEquals("hipster-chat-mock", firebaseApp.getOptions().getProjectId());
    } finally {
      ApplicationDefaultCredentialsProvider.setApplicationDefault(null);
    }
  }

  @Test
  public void testFirebaseAppCreationWithEmptySupplier() {
    FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setDatabaseUrl("https://test-ns.firebaseio.com")
        .setCredentials(Suppliers.<GoogleCredentials>ofInstance(null)).build());
  }

  private static void setFirebaseConfigEnvironmentVariable(String configJSON) {
    String configValue;
    if (configJSON.isEmpty() || configJSON.startsWith("{")) {
      configValue = configJSON;
    } else {
      configValue = new File("src/test/resources", configJSON).getAbsolutePath();
    }

    FirebaseProcessEnvironment.setenv(FirebaseApp.FIREBASE_CONFIG_ENV_VAR, configValue);
  }

  private static FirebaseOptions getMockCredentialOptions() {
    return FirebaseOptions.builder().setCredentials(new MockGoogleCredentials()).build();
  }

  private static void invokePublicInstanceMethodWithDefaultValues(Object instance, Method method)
      throws InvocationTargetException, IllegalAccessException {
    List<Object> parameters = new ArrayList<>(method.getParameterTypes().length);
    for (Class<?> parameterType : method.getParameterTypes()) {
      parameters.add(Defaults.defaultValue(parameterType));
    }
    method.invoke(instance, parameters.toArray());
  }

  private static class MockTokenRefresher extends TokenRefresher {

    private Callable<Void> task;
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
    protected void scheduleNext(Callable<Void> task, long delayMillis) {
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
      synchronized (this) {
        time += TimeUnit.MINUTES.toMillis(delayMinutes);
        if (task != null && time >= executeAt) {
          task.call();
        }
      }
    }
  }

  private static class CountingTokenRefresher extends TokenRefresher {

    private int cancelCalls = 0;
    private int scheduleCalls = 0;

    CountingTokenRefresher(FirebaseApp firebaseApp) {
      super(firebaseApp);
    }

    @Override
    protected void cancelPrevious() {
      cancelCalls++;
    }

    @Override
    protected void scheduleNext(Callable<Void> task, long delayMillis) {
      scheduleCalls++;
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

  private static class MockGoogleCredentials extends GoogleCredentials {

    @Override
    public AccessToken refreshAccessToken() {
      Date expiry = new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1));
      return new AccessToken(UUID.randomUUID().toString(), expiry);
    }
  }

  private static class MockFirebaseService extends FirebaseService<Object> {
    MockFirebaseService() {
      super("MockFirebaseService", new Object());
    }
  }
}
