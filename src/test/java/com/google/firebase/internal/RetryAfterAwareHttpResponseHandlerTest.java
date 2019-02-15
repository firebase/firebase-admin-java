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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.http.EmptyContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.FixedClock;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.testing.util.MockSleeper;
import com.google.api.client.util.Clock;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Longs;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;

public class RetryAfterAwareHttpResponseHandlerTest {

  private static final GenericUrl TEST_URL = new GenericUrl("https://firebase.google.com");
  private static final HttpRetryConfig TEST_RETRY_CONFIG = HttpRetryConfig.builder()
      .setRetryStatusCodes(ImmutableList.of(503))
      .build();

  @Test
  public void testRetryWithBackoffWhenRetryAfterIsAbsent() throws IOException {
    RetryAfterAwareHttpResponseHandler handler = new RetryAfterAwareHttpResponseHandler(
        TEST_RETRY_CONFIG);
    MultipleCallSleeper sleeper = new MultipleCallSleeper();
    handler.setSleeper(sleeper);
    CountingHttpRequest failingRequest = CountingHttpRequest.fromResponse(
        new MockLowLevelHttpResponse().setStatusCode(503).setZeroContent());
    HttpRequest request = createRequest(failingRequest);
    request.setNumberOfRetries(4);
    request.setUnsuccessfulResponseHandler(handler);

    try {
      request.execute();
      fail("No exception thrown for HTTP error");
    } catch (HttpResponseException e) {
      assertEquals(503, e.getStatusCode());
    }

    assertEquals(4, sleeper.getCount());
    assertArrayEquals(new long[]{500, 1000, 2000, 4000}, sleeper.getDelays());
    assertEquals(5, failingRequest.getCount());
  }

  @Test
  public void testRetryWithRetryAfterGivenAsSeconds() throws IOException {
    RetryAfterAwareHttpResponseHandler handler = new RetryAfterAwareHttpResponseHandler(
        TEST_RETRY_CONFIG);
    MultipleCallSleeper sleeper = new MultipleCallSleeper();
    handler.setSleeper(sleeper);
    CountingHttpRequest failingRequest = CountingHttpRequest.fromResponse(
        new MockLowLevelHttpResponse()
            .addHeader("retry-after", "2")
            .setStatusCode(503)
            .setZeroContent());
    HttpRequest request = createRequest(failingRequest);
    request.setNumberOfRetries(4);
    request.setUnsuccessfulResponseHandler(handler);

    try {
      request.execute();
      fail("No exception thrown for HTTP error");
    } catch (HttpResponseException e) {
      assertEquals(503, e.getStatusCode());
    }

    assertEquals(4, sleeper.getCount());
    assertArrayEquals(new long[]{2000, 2000, 2000, 2000}, sleeper.getDelays());
    assertEquals(5, failingRequest.getCount());
  }

  @Test
  public void testRetryWithRetryAfterGivenAsDate() throws IOException {
    SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    Date date = new Date(1000);
    Clock clock = new FixedClock(date.getTime());
    String retryAfter = dateFormat.format(new Date(date.getTime() + 30000));

    RetryAfterAwareHttpResponseHandler handler = new RetryAfterAwareHttpResponseHandler(
        TEST_RETRY_CONFIG, clock);
    MultipleCallSleeper sleeper = new MultipleCallSleeper();
    handler.setSleeper(sleeper);
    CountingHttpRequest failingRequest = CountingHttpRequest.fromResponse(
        new MockLowLevelHttpResponse()
            .addHeader("retry-after", retryAfter)
            .setStatusCode(503)
            .setZeroContent());
    HttpRequest request = createRequest(failingRequest);
    request.setNumberOfRetries(4);
    request.setUnsuccessfulResponseHandler(handler);

    try {
      request.execute();
      fail("No exception thrown for HTTP error");
    } catch (HttpResponseException e) {
      assertEquals(503, e.getStatusCode());
    }

    assertEquals(4, sleeper.getCount());
    assertArrayEquals(new long[]{30000, 30000, 30000, 30000}, sleeper.getDelays());
    assertEquals(5, failingRequest.getCount());
  }

  @Test
  public void testDoesNotRetryWhenRetryAfterIsTooLong() throws IOException {
    RetryAfterAwareHttpResponseHandler handler = new RetryAfterAwareHttpResponseHandler(
        TEST_RETRY_CONFIG);
    MultipleCallSleeper sleeper = new MultipleCallSleeper();
    handler.setSleeper(sleeper);
    CountingHttpRequest failingRequest = CountingHttpRequest.fromResponse(
        new MockLowLevelHttpResponse()
            .addHeader("retry-after", "121")
            .setStatusCode(503)
            .setZeroContent());
    HttpRequest request = createRequest(failingRequest);
    request.setNumberOfRetries(4);
    request.setUnsuccessfulResponseHandler(handler);

    try {
      request.execute();
      fail("No exception thrown for HTTP error");
    } catch (HttpResponseException e) {
      assertEquals(503, e.getStatusCode());
    }

    assertEquals(0, sleeper.getCount());
    assertEquals(1, failingRequest.getCount());
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

    static CountingHttpRequest fromException(IOException exception) {
      return new CountingHttpRequest(null, checkNotNull(exception));
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

  private static class MultipleCallSleeper extends MockSleeper {

    private final List<Long> delays = new ArrayList<>();

    @Override
    public void sleep(long millis) throws InterruptedException {
      super.sleep(millis);
      delays.add(millis);
    }

    long[] getDelays() {
      return Longs.toArray(delays);
    }
  }
}
