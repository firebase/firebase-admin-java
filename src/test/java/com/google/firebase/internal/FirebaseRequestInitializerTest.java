/*
 * Copyright 2018 Google Inc.
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

package com.google.firebase.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.api.client.http.HttpBackOffIOExceptionHandler;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponseException;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.testing.TestUtils;
import org.junit.After;
import org.junit.Test;

public class FirebaseRequestInitializerTest {

  private static final int MAX_RETRIES = 5;
  private static final int CONNECT_TIMEOUT_MILLIS = 30000;
  private static final int READ_TIMEOUT_MILLIS = 60000;

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testDefaultSettings() throws Exception {
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("token"))
        .build());
    HttpRequest request = TestUtils.createRequest();

    FirebaseRequestInitializer initializer = new FirebaseRequestInitializer(app);
    initializer.initialize(request);


    assertEquals(0, request.getConnectTimeout());
    assertEquals(0, request.getReadTimeout());
    assertEquals("Bearer token", request.getHeaders().getAuthorization());
    assertEquals(HttpRequest.DEFAULT_NUMBER_OF_RETRIES, request.getNumberOfRetries());
    assertNull(request.getIOExceptionHandler());
    assertTrue(request.getUnsuccessfulResponseHandler() instanceof HttpCredentialsAdapter);
  }

  @Test
  public void testExplicitTimeouts() throws Exception {
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("token"))
        .setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
        .setReadTimeout(READ_TIMEOUT_MILLIS)
        .build());
    HttpRequest request = TestUtils.createRequest();

    FirebaseRequestInitializer initializer = new FirebaseRequestInitializer(app);
    initializer.initialize(request);

    assertEquals(CONNECT_TIMEOUT_MILLIS, request.getConnectTimeout());
    assertEquals(READ_TIMEOUT_MILLIS, request.getReadTimeout());
    assertEquals("Bearer token", request.getHeaders().getAuthorization());
    assertEquals(HttpRequest.DEFAULT_NUMBER_OF_RETRIES, request.getNumberOfRetries());
    assertNull(request.getIOExceptionHandler());
    assertTrue(request.getUnsuccessfulResponseHandler() instanceof HttpCredentialsAdapter);
  }

  @Test
  public void testRetryConfig() throws Exception {
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("token"))
        .build());
    RetryConfig retryConfig = RetryConfig.builder()
        .setMaxRetries(MAX_RETRIES)
        .build();
    HttpRequest request = TestUtils.createRequest();

    FirebaseRequestInitializer initializer = new FirebaseRequestInitializer(app, retryConfig);
    initializer.initialize(request);

    assertEquals(0, request.getConnectTimeout());
    assertEquals(0, request.getReadTimeout());
    assertEquals("Bearer token", request.getHeaders().getAuthorization());
    assertEquals(MAX_RETRIES, request.getNumberOfRetries());
    assertNull(request.getIOExceptionHandler());
    assertNotNull(request.getUnsuccessfulResponseHandler());
  }

  @Test
  public void testRetryConfigWithIOExceptionHandling() throws Exception {
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("token"))
        .build());
    RetryConfig retryConfig = RetryConfig.builder()
        .setMaxRetries(MAX_RETRIES)
        .setRetryOnIOExceptions(true)
        .build();
    HttpRequest request = TestUtils.createRequest();

    FirebaseRequestInitializer initializer = new FirebaseRequestInitializer(app, retryConfig);
    initializer.initialize(request);

    assertEquals(0, request.getConnectTimeout());
    assertEquals(0, request.getReadTimeout());
    assertEquals("Bearer token", request.getHeaders().getAuthorization());
    assertEquals(MAX_RETRIES, request.getNumberOfRetries());
    assertTrue(request.getIOExceptionHandler() instanceof HttpBackOffIOExceptionHandler);
    assertNotNull(request.getUnsuccessfulResponseHandler());
  }

  @Test
  public void testCredentialsRetryHandler() throws Exception {
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("token"))
        .build());
    RetryConfig retryConfig = RetryConfig.builder()
        .setMaxRetries(MAX_RETRIES)
        .build();
    CountingLowLevelHttpRequest countingRequest = CountingLowLevelHttpRequest.fromStatus(401);
    HttpRequest request = TestUtils.createRequest(countingRequest);
    FirebaseRequestInitializer initializer = new FirebaseRequestInitializer(app, retryConfig);
    initializer.initialize(request);
    request.getHeaders().setAuthorization((String) null);

    try {
      request.execute();
    } catch (HttpResponseException e) {
      assertEquals(401, e.getStatusCode());
    }

    assertEquals("Bearer token", request.getHeaders().getAuthorization());
    assertEquals(MAX_RETRIES + 1, countingRequest.getCount());
  }
}
