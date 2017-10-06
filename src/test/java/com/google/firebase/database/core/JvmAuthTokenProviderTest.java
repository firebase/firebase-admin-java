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

package com.google.firebase.database.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.cedarsoftware.util.DeepEquals;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.testing.TestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

public class JvmAuthTokenProviderTest {

  private static final Executor DIRECT_EXECUTOR = MoreExecutors.directExecutor();

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGetToken() throws IOException, InterruptedException {
    MockGoogleCredentials credentials = new MockGoogleCredentials("mock-token");
    TokenRefreshDetector refreshDetector = new TokenRefreshDetector();
    credentials.addChangeListener(refreshDetector);
    credentials.refresh();
    assertEquals(1, refreshDetector.count);

    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(credentials)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    JvmAuthTokenProvider provider = new JvmAuthTokenProvider(app, DIRECT_EXECUTOR);
    TestGetTokenListener listener = new TestGetTokenListener();
    provider.getToken(true, listener);
    assertToken(listener.get(), "mock-token", ImmutableMap.<String, Object>of());
    assertEquals(2, refreshDetector.count);
  }

  @Test
  public void testGetTokenNoRefresh() throws IOException, InterruptedException {
    MockGoogleCredentials credentials = new MockGoogleCredentials("mock-token");
    TokenRefreshDetector refreshDetector = new TokenRefreshDetector();
    credentials.addChangeListener(refreshDetector);
    credentials.refresh();
    assertEquals(1, refreshDetector.count);

    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(credentials)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    JvmAuthTokenProvider provider = new JvmAuthTokenProvider(app, DIRECT_EXECUTOR);
    TestGetTokenListener listener = new TestGetTokenListener();
    provider.getToken(false, listener);
    assertToken(listener.get(), "mock-token", ImmutableMap.<String, Object>of());
    assertEquals(1, refreshDetector.count);
  }

  @Test
  public void testGetTokenWithAuthOverrides() throws InterruptedException {
    MockGoogleCredentials credentials = new MockGoogleCredentials("mock-token");
    Map<String, Object> auth = ImmutableMap.<String, Object>of("uid", "test");
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(credentials)
        .setDatabaseAuthVariableOverride(auth)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    JvmAuthTokenProvider provider = new JvmAuthTokenProvider(app, DIRECT_EXECUTOR);
    TestGetTokenListener listener = new TestGetTokenListener();
    provider.getToken(true, listener);
    assertToken(listener.get(), "mock-token", auth);
  }

  @Test
  public void testGetTokenError() throws InterruptedException {
    MockGoogleCredentials credentials = new MockGoogleCredentials("mock-token") {
      @Override
      public AccessToken refreshAccessToken() throws IOException {
        throw new RuntimeException("Test error");
      }
    };
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(credentials)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    JvmAuthTokenProvider provider = new JvmAuthTokenProvider(app, DIRECT_EXECUTOR);
    TestGetTokenListener listener = new TestGetTokenListener();
    provider.getToken(true, listener);
    assertEquals("java.lang.RuntimeException: Test error", listener.get());
  }

  @Test
  public void testAddTokenChangeListener() throws IOException {
    final AtomicInteger counter = new AtomicInteger(0);
    MockGoogleCredentials credentials = new MockGoogleCredentials() {
      @Override
      public AccessToken refreshAccessToken() throws IOException {
        Date expiry = new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1));
        return new AccessToken("token-" + counter.getAndIncrement(), expiry);
      }
    };

    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(credentials)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    // Disable proactive token refresh, so only explicit refresh events are in play.
    JvmAuthTokenProvider provider = new JvmAuthTokenProvider(app, DIRECT_EXECUTOR, false);
    final List<String> tokens = new ArrayList<>();
    provider.addTokenChangeListener(new AuthTokenProvider.TokenChangeListener() {
      @Override
      public void onTokenChange(String token) {
        tokens.add(token);
      }
    });

    for (int i = 0; i < 10; i++) {
      // Each refresh event should notify the TokenChangeListener. And since we are using a
      // direct executor, the notification fires on the same thread in a blocking manner.
      credentials.refresh();
      assertEquals(i + 1, tokens.size());
      assertToken(tokens.get(i), "token-" + i, ImmutableMap.<String, Object>of());
    }
  }

  @Test
  public void testTokenChangeListenerThread() throws InterruptedException, IOException {
    MockGoogleCredentials credentials = new MockGoogleCredentials();
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(credentials)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    // Disable proactive token refresh, so only explicit refresh events are in play.
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setNameFormat("auth-token-provider-thread")
        .setDaemon(true)
        .build();
    ExecutorService executor = Executors.newSingleThreadExecutor(threadFactory);
    try {
      JvmAuthTokenProvider provider = new JvmAuthTokenProvider(app, executor, false);

      final AtomicReference<String> result = new AtomicReference<>();
      final Semaphore semaphore = new Semaphore(0);
      provider.addTokenChangeListener(new AuthTokenProvider.TokenChangeListener() {
        @Override
        public void onTokenChange(String token) {
          result.set(Thread.currentThread().getName());
          semaphore.release();
        }
      });

      credentials.refresh();
      assertTrue(semaphore.tryAcquire(TestUtils.TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
      assertEquals("auth-token-provider-thread", result.get());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void testTokenAutoRefresh() throws InterruptedException {
    MockGoogleCredentials credentials = new MockGoogleCredentials();
    final Semaphore semaphore = new Semaphore(0);
    credentials.addChangeListener(new OAuth2Credentials.CredentialsChangedListener() {
      @Override
      public void onChanged(OAuth2Credentials credentials) throws IOException {
        semaphore.release();
      }
    });

    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(credentials)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    // Creating JvmAuthTokenProvider should start the proactive token refresher, which should
    // immediately refresh the credential once.
    new JvmAuthTokenProvider(app, DIRECT_EXECUTOR);
    assertTrue(semaphore.tryAcquire(TestUtils.TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
  }

  private void assertToken(String token, String expectedToken, Map<String, Object> expectedAuth) {
    assertTrue(token.startsWith("gauth|"));
    String jsonString = token.substring(6);
    JSONObject json = new JSONObject(jsonString);
    assertEquals(expectedToken, json.getString("token"));

    Map<String, Object> auth = json.getJSONObject("auth").toMap();
    DeepEquals.deepEquals(expectedAuth, auth);
  }

  private static class TestGetTokenListener
      implements AuthTokenProvider.GetTokenCompletionListener {

    private final Semaphore semaphore = new Semaphore(0);
    private final AtomicReference<String> result = new AtomicReference<>(null);

    @Override
    public void onSuccess(String token) {
      result.set(token);
      semaphore.release();
    }

    @Override
    public void onError(String error) {
      result.set(error);
      semaphore.release();
    }

    String get() throws InterruptedException {
      if (semaphore.tryAcquire(TestUtils.TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
        return result.get();
      }
      fail("Timed out while waiting for GetTokenCompletionListener");
      return null;
    }
  }

  private static class TokenRefreshDetector
      implements OAuth2Credentials.CredentialsChangedListener {

    private int count = 0;

    @Override
    public void onChanged(OAuth2Credentials credentials) throws IOException {
      count++;
    }
  }

}
