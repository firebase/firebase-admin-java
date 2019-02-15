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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.google.api.client.http.EmptyContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.testing.util.MockSleeper;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.MockGoogleCredentials;
import java.io.IOException;
import org.junit.Test;

public class CredentialsResponseHandlerDecoratorTest {

  private static final GenericUrl TEST_URL = new GenericUrl("https://firebase.google.com");
  public static final GoogleCredentials TEST_CREDENTIALS = new MockGoogleCredentials();
  private static final RetryConfig RETRY_CONFIG = RetryConfig.builder()
      .setMaxRetries(5)
      .setRetryStatusCodes(ImmutableList.of(503))
      .build();

  @Test
  public void testRetryCredentialsCheck() throws IOException {
    MockSleeper sleeper = new MockSleeper();
    HttpBackOffUnsuccessfulResponseHandler responseHandler =
        new HttpBackOffUnsuccessfulResponseHandler(RETRY_CONFIG.newBackOff())
            .setSleeper(sleeper);
    CredentialsResponseHandlerDecorator retryHandler = new CredentialsResponseHandlerDecorator(
        new MockHttpCredentialsAdapter(), responseHandler);
    CountingHttpRequest failingRequest = CountingHttpRequest
        .fromResponse(new MockLowLevelHttpResponse().setStatusCode(401).setZeroContent());
    HttpRequest request = createRequest(failingRequest);
    request.setUnsuccessfulResponseHandler(retryHandler);

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
    HttpBackOffUnsuccessfulResponseHandler responseHandler =
        new HttpBackOffUnsuccessfulResponseHandler(RETRY_CONFIG.newBackOff())
            .setSleeper(sleeper)
            .setBackOffRequired(HttpBackOffUnsuccessfulResponseHandler.BackOffRequired.ALWAYS);
    CredentialsResponseHandlerDecorator retryHandler = new CredentialsResponseHandlerDecorator(
        new MockHttpCredentialsAdapter(), responseHandler);
    CountingHttpRequest failingRequest = CountingHttpRequest
        .fromResponse(new MockLowLevelHttpResponse().setStatusCode(401).setZeroContent());
    HttpRequest request = createRequest(failingRequest);
    request.setNumberOfRetries(4);
    request.setUnsuccessfulResponseHandler(retryHandler);

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

  private static class MockHttpCredentialsAdapter extends HttpCredentialsAdapter {

    private MockHttpCredentialsAdapter() {
      super(TEST_CREDENTIALS);
    }

    @Override
    public boolean handleResponse(
        HttpRequest request, HttpResponse response, boolean supportsRetry) {
      String authorization = request.getHeaders().getAuthorization();
      if (!"Bearer retry".equals(authorization)) {
        request.getHeaders().setAuthorization("Bearer retry");
        return true;
      }
      return false;
    }
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

}
