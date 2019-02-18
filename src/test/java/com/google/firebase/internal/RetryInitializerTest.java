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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.http.EmptyContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpBackOffIOExceptionHandler;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.testing.util.MockSleeper;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.MockGoogleCredentials;
import java.io.IOException;
import org.junit.Test;

public class RetryInitializerTest {

  private static final GenericUrl TEST_URL = new GenericUrl("https://firebase.google.com");
  private static final HttpCredentialsAdapter TEST_CREDENTIALS = new HttpCredentialsAdapter(
      new MockGoogleCredentials());
  private static final RetryConfig RETRY_CONFIG = RetryConfig.builder()
      .setMaxRetries(5)
      .setRetryStatusCodes(ImmutableList.of(503))
      .build();

  @Test
  public void testEnableRetry() throws IOException {
    RetryInitializer initializer = new RetryInitializer(TEST_CREDENTIALS, RETRY_CONFIG);
    HttpRequest request = createRequest();

    initializer.initialize(request);

    assertEquals(5, request.getNumberOfRetries());
    assertNotNull(request.getUnsuccessfulResponseHandler());
    assertTrue(request.getIOExceptionHandler() instanceof HttpBackOffIOExceptionHandler);
  }

  @Test
  public void testDisableRetry() throws IOException {
    RetryInitializer initializer = new RetryInitializer(TEST_CREDENTIALS, null);
    HttpRequest request = createRequest();

    initializer.initialize(request);

    assertEquals(0, request.getNumberOfRetries());
    assertNull(request.getUnsuccessfulResponseHandler());
    assertNull(request.getIOExceptionHandler());
  }

  @Test
  public void testRetryCredentialsCheck() throws IOException {
    MockSleeper sleeper = new MockSleeper();
    HttpCredentialsAdapter credentials = new HttpCredentialsAdapter(new MockGoogleCredentials()){
      @Override
      public boolean handleResponse(HttpRequest request, HttpResponse response, boolean
          supportsRetry) {
        String auth = request.getHeaders().getAuthorization();
        if (!"Bearer retry".equals(auth)) {
          request.getHeaders().setAuthorization("Bearer retry");
          return true;
        }
        return false;
      }
    };
    RetryInitializer initializer = new RetryInitializer(credentials, RetryConfig.builder()
        .setMaxRetries(4)
        .setRetryStatusCodes(ImmutableList.of(503))
        .setSleeper(sleeper)
        .build());

    CountingHttpRequest failingRequest = CountingHttpRequest.fromResponse(
        new MockLowLevelHttpResponse().setStatusCode(401).setZeroContent());
    HttpRequest request = createRequest(failingRequest);
    initializer.initialize(request);
    final HttpUnsuccessfulResponseHandler retryHandler = request.getUnsuccessfulResponseHandler();

    try {
      request.execute();
      fail("No exception thrown for HTTP error");
    } catch (HttpResponseException e) {
      assertEquals(401, e.getStatusCode());
    }

    assertEquals("Bearer retry", request.getHeaders().getAuthorization());
    assertEquals(0, sleeper.getCount());
    assertEquals(2, failingRequest.getCount());
    assertSame(retryHandler, request.getUnsuccessfulResponseHandler());
  }

  @Test
  public void testDelegateCalledAfterCredentials() throws IOException {
    MockSleeper sleeper = new MockSleeper();
    HttpCredentialsAdapter credentials = new HttpCredentialsAdapter(new MockGoogleCredentials()){
      @Override
      public boolean handleResponse(HttpRequest request, HttpResponse response, boolean
          supportsRetry) {
        String auth = request.getHeaders().getAuthorization();
        if (!"Bearer retry".equals(auth)) {
          request.getHeaders().setAuthorization("Bearer retry");
          return true;
        }
        return false;
      }
    };
    RetryInitializer initializer = new RetryInitializer(credentials, RetryConfig.builder()
        .setMaxRetries(4)
        .setRetryStatusCodes(ImmutableList.of(401))
        .setSleeper(sleeper)
        .build());

    CountingHttpRequest failingRequest = CountingHttpRequest.fromResponse(
        new MockLowLevelHttpResponse().setStatusCode(401).setZeroContent());
    HttpRequest request = createRequest(failingRequest);
    initializer.initialize(request);
    final HttpUnsuccessfulResponseHandler retryHandler = request.getUnsuccessfulResponseHandler();

    try {
      request.execute();
      fail("No exception thrown for HTTP error");
    } catch (HttpResponseException e) {
      assertEquals(401, e.getStatusCode());
    }

    assertEquals("Bearer retry", request.getHeaders().getAuthorization());
    assertEquals(3, sleeper.getCount());
    assertEquals(5, failingRequest.getCount());
    assertSame(retryHandler, request.getUnsuccessfulResponseHandler());
  }


  private HttpRequest createRequest(MockLowLevelHttpRequest request) throws IOException {
    HttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpRequest(request)
        .build();
    HttpRequestFactory requestFactory = transport.createRequestFactory();
    return requestFactory.buildPostRequest(TEST_URL, new EmptyContent());
  }

  private static class CountingHttpRequest extends MockLowLevelHttpRequest {

    private final LowLevelHttpResponse response;
    private final IOException exception;
    private int count;

    private CountingHttpRequest(LowLevelHttpResponse response, IOException exception) {
      this.response = response;
      this.exception = exception;
    }

    static CountingHttpRequest fromResponse(LowLevelHttpResponse response) {
      return new CountingHttpRequest(checkNotNull(response), null);
    }

    @Override
    public void addHeader(String name, String value) { }

    @Override
    public LowLevelHttpResponse execute() throws IOException {
      count++;
      if (response != null) {
        return response;
      }
      throw exception;
    }

    int getCount() {
      return count;
    }
  }

  private HttpRequest createRequest() throws IOException {
    HttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpRequest(new MockLowLevelHttpRequest())
        .build();
    HttpRequestFactory requestFactory = transport.createRequestFactory();
    return requestFactory.buildPostRequest(TEST_URL, new EmptyContent());
  }
}
