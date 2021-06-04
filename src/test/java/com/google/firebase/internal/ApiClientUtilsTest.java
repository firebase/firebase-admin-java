/*
 * Copyright 2019 Google Inc.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableList;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.internal.RetryInitializer.RetryHandlerDecorator;
import java.io.IOException;
import org.junit.After;
import org.junit.Test;

public class ApiClientUtilsTest {

  private static final FirebaseOptions TEST_OPTIONS = FirebaseOptions.builder()
      .setCredentials(new MockGoogleCredentials("test-token"))
      .build();
  private static final GenericUrl TEST_URL = new GenericUrl("https://firebase.google.com");

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testAuthorizedHttpClient() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS);

    HttpRequestFactory requestFactory = ApiClientUtils.newAuthorizedRequestFactory(app);

    assertTrue(requestFactory.getInitializer() instanceof FirebaseRequestInitializer);
    HttpRequest request = requestFactory.buildGetRequest(TEST_URL);
    assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
    HttpUnsuccessfulResponseHandler retryHandler = request.getUnsuccessfulResponseHandler();
    assertTrue(retryHandler instanceof RetryHandlerDecorator);
    RetryConfig retryConfig = ((RetryHandlerDecorator) retryHandler).getRetryHandler()
        .getRetryConfig();
    assertEquals(4, retryConfig.getMaxRetries());
    assertEquals(60 * 1000, retryConfig.getMaxIntervalMillis());
    assertFalse(retryConfig.isRetryOnIOExceptions());
    assertEquals(retryConfig.getRetryStatusCodes(), ImmutableList.of(500, 503));
  }

  @Test
  public void testAuthorizedHttpClientWithoutRetry() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS);

    HttpRequestFactory requestFactory = ApiClientUtils.newAuthorizedRequestFactory(app, null);

    assertTrue(requestFactory.getInitializer() instanceof FirebaseRequestInitializer);
    HttpRequest request = requestFactory.buildGetRequest(TEST_URL);
    assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
    HttpUnsuccessfulResponseHandler retryHandler = request.getUnsuccessfulResponseHandler();
    assertFalse(retryHandler instanceof RetryHandlerDecorator);
  }

  @Test
  public void testUnauthorizedHttpClient() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS);

    HttpRequestFactory requestFactory = ApiClientUtils.newUnauthorizedRequestFactory(app);

    assertNull(requestFactory.getInitializer());
    HttpRequest request = requestFactory.buildGetRequest(TEST_URL);
    assertNull(request.getHeaders().getAuthorization());
    HttpUnsuccessfulResponseHandler retryHandler = request.getUnsuccessfulResponseHandler();
    assertNull(retryHandler);
  }

  @Test
  public void testDisconnect() throws IOException {
    MockLowLevelHttpResponse lowLevelResponse = new MockLowLevelHttpResponse();
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(lowLevelResponse)
        .build();
    HttpResponse response = transport.createRequestFactory().buildGetRequest(TEST_URL).execute();
    assertFalse(lowLevelResponse.isDisconnected());

    ApiClientUtils.disconnectQuietly(response);

    assertTrue(lowLevelResponse.isDisconnected());
  }

  @Test
  public void testDisconnectWithErrorSuppression() throws IOException {
    MockLowLevelHttpResponse lowLevelResponse = new MockLowLevelHttpResponse(){
      @Override
      public void disconnect() throws IOException {
        super.disconnect();
        throw new IOException("test error");
      }
    };
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(lowLevelResponse)
        .build();
    HttpResponse response = transport.createRequestFactory().buildGetRequest(TEST_URL).execute();
    assertFalse(lowLevelResponse.isDisconnected());

    ApiClientUtils.disconnectQuietly(response);

    assertTrue(lowLevelResponse.isDisconnected());
  }
}
