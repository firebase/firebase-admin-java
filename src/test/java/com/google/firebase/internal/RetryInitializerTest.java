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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.http.HttpBackOffIOExceptionHandler;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.testing.util.MockSleeper;
import com.google.api.client.util.Sleeper;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.internal.RetryInitializer.RetryHandlerDecorator;
import com.google.firebase.testing.TestUtils;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class RetryInitializerTest {

  private static final int MAX_RETRIES = 4;

  @Test
  public void testEnableRetry() throws IOException {
    RetryConfig retryConfig = retryOnIOAndServiceUnavailableErrors(new MockSleeper());
    RetryInitializer initializer = new RetryInitializer(retryConfig);
    HttpRequest request = TestUtils.createRequest();

    initializer.initialize(request);

    assertEquals(MAX_RETRIES, request.getNumberOfRetries());
    assertTrue(request.getUnsuccessfulResponseHandler() instanceof RetryHandlerDecorator);
    RetryUnsuccessfulResponseHandler retryHandler =
        ((RetryHandlerDecorator) request.getUnsuccessfulResponseHandler()).getRetryHandler();
    assertSame(retryConfig, retryHandler.getRetryConfig());
    assertTrue(request.getIOExceptionHandler() instanceof HttpBackOffIOExceptionHandler);
  }

  @Test
  public void testRetryOnIOExceptionDisabled() throws IOException {
    RetryInitializer initializer = new RetryInitializer(RetryConfig.builder()
        .setMaxRetries(MAX_RETRIES)
        .setRetryOnIOExceptions(false)
        .setRetryStatusCodes(ImmutableList.of(503))
        .build());
    HttpRequest request = TestUtils.createRequest();

    initializer.initialize(request);

    assertEquals(MAX_RETRIES, request.getNumberOfRetries());
    assertNotNull(request.getUnsuccessfulResponseHandler());
    assertNull(request.getIOExceptionHandler());
  }

  @Test(expected = NullPointerException.class)
  public void testRetryConfigCannotBeNull() {
    new RetryInitializer(null);
  }

  @Test
  public void testRetryOnIOException() throws IOException {
    MockSleeper sleeper = new MockSleeper();
    RetryInitializer initializer = new RetryInitializer(
        retryOnIOAndServiceUnavailableErrors(sleeper));

    CountingLowLevelHttpRequest failingRequest = CountingLowLevelHttpRequest.fromException(
        new IOException("test error"));
    HttpRequest request = TestUtils.createRequest(failingRequest);
    initializer.initialize(request);
    final HttpUnsuccessfulResponseHandler retryHandler = request.getUnsuccessfulResponseHandler();

    try {
      request.execute();
      fail("No exception thrown for HTTP error");
    } catch (IOException e) {
      assertEquals("test error", e.getMessage());
    }

    assertEquals(MAX_RETRIES, sleeper.getCount());
    assertEquals(MAX_RETRIES + 1, failingRequest.getCount());
    assertSame(retryHandler, request.getUnsuccessfulResponseHandler());
  }

  @Test
  public void testRetryOnHttpError() throws IOException {
    MockSleeper sleeper = new MockSleeper();
    RetryInitializer initializer = new RetryInitializer(
        retryOnIOAndServiceUnavailableErrors(sleeper));
    CountingLowLevelHttpRequest failingRequest = CountingLowLevelHttpRequest.fromStatus(503);
    HttpRequest request = TestUtils.createRequest(failingRequest);
    initializer.initialize(request);
    final HttpUnsuccessfulResponseHandler retryHandler = request.getUnsuccessfulResponseHandler();

    try {
      request.execute();
      fail("No exception thrown for HTTP error");
    } catch (HttpResponseException e) {
      assertEquals(503, e.getStatusCode());
    }

    assertEquals(MAX_RETRIES, sleeper.getCount());
    assertEquals(MAX_RETRIES + 1, failingRequest.getCount());
    assertSame(retryHandler, request.getUnsuccessfulResponseHandler());
  }

  @Test
  public void testMaxRetriesCountIsCumulative() throws IOException {
    MockSleeper sleeper = new MockSleeper();
    RetryInitializer initializer = new RetryInitializer(
        retryOnIOAndServiceUnavailableErrors(sleeper));

    final AtomicInteger counter = new AtomicInteger(0);
    MockLowLevelHttpRequest failingRequest = new MockLowLevelHttpRequest(){
      @Override
      public LowLevelHttpResponse execute() throws IOException {
        if (counter.getAndIncrement() < 2) {
          throw new IOException("test error");
        } else {
          return new MockLowLevelHttpResponse().setStatusCode(503).setZeroContent();
        }
      }
    };
    HttpRequest request = TestUtils.createRequest(failingRequest);
    initializer.initialize(request);
    final HttpUnsuccessfulResponseHandler retryHandler = request.getUnsuccessfulResponseHandler();

    try {
      request.execute();
      fail("No exception thrown for HTTP error");
    } catch (HttpResponseException e) {
      assertEquals(503, e.getStatusCode());
    }

    assertEquals(MAX_RETRIES, sleeper.getCount());
    assertEquals(MAX_RETRIES + 1, counter.get());
    assertSame(retryHandler, request.getUnsuccessfulResponseHandler());
  }

  @Test
  public void testOtherErrorHandlersCalledBeforeRetry() throws IOException {
    final AtomicInteger otherErrorHandlerCalls = new AtomicInteger(0);
    HttpCredentialsAdapter credentials = new HttpCredentialsAdapter(new MockGoogleCredentials()) {
      @Override
      public boolean handleResponse(
          HttpRequest request, HttpResponse response, boolean supportsRetry) {
        otherErrorHandlerCalls.incrementAndGet();
        return super.handleResponse(request, response, supportsRetry);
      }
    };
    MockSleeper sleeper = new MockSleeper();
    RetryInitializer initializer = new RetryInitializer(RetryConfig.builder()
        .setMaxRetries(MAX_RETRIES)
        .setRetryStatusCodes(ImmutableList.of(503))
        .setSleeper(sleeper)
        .build());
    CountingLowLevelHttpRequest failingRequest = CountingLowLevelHttpRequest.fromStatus(503);
    HttpRequest request = TestUtils.createRequest(failingRequest);
    credentials.initialize(request);
    initializer.initialize(request);
    final HttpUnsuccessfulResponseHandler retryHandler = request.getUnsuccessfulResponseHandler();

    try {
      request.execute();
      fail("No exception thrown for HTTP error");
    } catch (HttpResponseException e) {
      assertEquals(503, e.getStatusCode());
    }

    assertEquals(MAX_RETRIES, sleeper.getCount());
    assertEquals(MAX_RETRIES + 1, failingRequest.getCount());
    assertEquals(MAX_RETRIES + 1, otherErrorHandlerCalls.get());
    assertSame(retryHandler, request.getUnsuccessfulResponseHandler());
  }

  @Test
  public void testRetryHandlerDoesNotGetOverwritten() throws IOException {
    final AtomicInteger otherErrorHandlerCalls = new AtomicInteger(0);
    HttpUnsuccessfulResponseHandler credentials = new HttpUnsuccessfulResponseHandler() {
      @Override
      public boolean handleResponse(
          HttpRequest request, HttpResponse response, boolean supportsRetry) throws IOException {
        otherErrorHandlerCalls.incrementAndGet();
        request.setUnsuccessfulResponseHandler(this);
        throw new IOException("test");
      }
    };
    MockSleeper sleeper = new MockSleeper();
    RetryInitializer initializer = new RetryInitializer(RetryConfig.builder()
        .setMaxRetries(MAX_RETRIES)
        .setRetryStatusCodes(ImmutableList.of(503))
        .setSleeper(sleeper)
        .build());
    CountingLowLevelHttpRequest failingRequest = CountingLowLevelHttpRequest.fromStatus(503);
    HttpRequest request = TestUtils.createRequest(failingRequest);
    request.setUnsuccessfulResponseHandler(credentials);
    initializer.initialize(request);
    final HttpUnsuccessfulResponseHandler retryHandler = request.getUnsuccessfulResponseHandler();

    try {
      request.execute();
      fail("No exception thrown for HTTP error");
    } catch (Exception e) {
      assertEquals("test", e.getMessage());
    }

    assertEquals(1, otherErrorHandlerCalls.get());
    assertSame(retryHandler, request.getUnsuccessfulResponseHandler());
  }

  private RetryConfig retryOnIOAndServiceUnavailableErrors(Sleeper sleeper) {
    return RetryConfig.builder()
        .setMaxRetries(MAX_RETRIES)
        .setRetryStatusCodes(ImmutableList.of(503))
        .setRetryOnIOExceptions(true)
        .setSleeper(sleeper)
        .build();
  }
}
