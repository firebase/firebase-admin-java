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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.testing.http.FixedClock;
import com.google.api.client.testing.util.MockSleeper;
import com.google.api.client.util.Clock;
import com.google.api.client.util.Sleeper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Longs;
import com.google.firebase.testing.TestUtils;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Test;

public class RetryUnsuccessfulResponseHandlerTest {

  private static final int MAX_RETRIES = 4;
  private static final RetryConfig.Builder TEST_RETRY_CONFIG = RetryConfig.builder()
      .setRetryStatusCodes(ImmutableList.of(429, 503))
      .setMaxIntervalMillis(120 * 1000);

  @Test
  public void testDoesNotRetryOnUnspecifiedHttpStatus() throws IOException {
    MultipleCallSleeper sleeper = new MultipleCallSleeper();
    RetryUnsuccessfulResponseHandler handler = new RetryUnsuccessfulResponseHandler(
        testRetryConfig(sleeper));
    CountingLowLevelHttpRequest failingRequest = CountingLowLevelHttpRequest.fromStatus(404);
    HttpRequest request = TestUtils.createRequest(failingRequest);
    request.setUnsuccessfulResponseHandler(handler);
    request.setNumberOfRetries(MAX_RETRIES);

    try {
      request.execute();
      fail("No exception thrown for HTTP error");
    } catch (HttpResponseException e) {
      assertEquals(404, e.getStatusCode());
    }

    assertEquals(0, sleeper.getCount());
    assertEquals(1, failingRequest.getCount());
  }

  @Test
  public void testRetryOnHttpClientErrorWhenSpecified() throws IOException {
    MultipleCallSleeper sleeper = new MultipleCallSleeper();
    RetryUnsuccessfulResponseHandler handler = new RetryUnsuccessfulResponseHandler(
        testRetryConfig(sleeper));
    CountingLowLevelHttpRequest failingRequest = CountingLowLevelHttpRequest.fromStatus(429);
    HttpRequest request = TestUtils.createRequest(failingRequest);
    request.setUnsuccessfulResponseHandler(handler);
    request.setNumberOfRetries(MAX_RETRIES);

    try {
      request.execute();
      fail("No exception thrown for HTTP error");
    } catch (HttpResponseException e) {
      assertEquals(429, e.getStatusCode());
    }

    assertEquals(MAX_RETRIES, sleeper.getCount());
    assertArrayEquals(new long[]{500, 1000, 2000, 4000}, sleeper.getDelays());
    assertEquals(MAX_RETRIES + 1, failingRequest.getCount());
  }

  @Test
  public void testExponentialBackOffDoesNotExceedMaxInterval() throws IOException {
    MultipleCallSleeper sleeper = new MultipleCallSleeper();
    RetryUnsuccessfulResponseHandler handler = new RetryUnsuccessfulResponseHandler(
        testRetryConfig(sleeper));
    CountingLowLevelHttpRequest failingRequest = CountingLowLevelHttpRequest.fromStatus(503);
    HttpRequest request = TestUtils.createRequest(failingRequest);
    request.setUnsuccessfulResponseHandler(handler);
    request.setNumberOfRetries(10);

    try {
      request.execute();
      fail("No exception thrown for HTTP error");
    } catch (HttpResponseException e) {
      assertEquals(503, e.getStatusCode());
    }

    assertEquals(10, sleeper.getCount());
    assertArrayEquals(
        new long[]{500, 1000, 2000, 4000, 8000, 16000, 32000, 64000, 120000, 120000},
        sleeper.getDelays());
    assertEquals(11, failingRequest.getCount());
  }

  @Test
  public void testRetryAfterGivenAsSeconds() throws IOException {
    MultipleCallSleeper sleeper = new MultipleCallSleeper();
    RetryUnsuccessfulResponseHandler handler = new RetryUnsuccessfulResponseHandler(
        testRetryConfig(sleeper));
    CountingLowLevelHttpRequest failingRequest = CountingLowLevelHttpRequest.fromStatus(
        503, ImmutableMap.of("retry-after", "2"));
    HttpRequest request = TestUtils.createRequest(failingRequest);
    request.setUnsuccessfulResponseHandler(handler);
    request.setNumberOfRetries(MAX_RETRIES);

    try {
      request.execute();
      fail("No exception thrown for HTTP error");
    } catch (HttpResponseException e) {
      assertEquals(503, e.getStatusCode());
    }

    assertEquals(MAX_RETRIES, sleeper.getCount());
    assertArrayEquals(new long[]{2000, 2000, 2000, 2000}, sleeper.getDelays());
    assertEquals(MAX_RETRIES + 1, failingRequest.getCount());
  }

  @Test
  public void testRetryAfterGivenAsDate() throws IOException {
    SimpleDateFormat dateFormat =
            new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    Date date = new Date(1000);
    Clock clock = new FixedClock(date.getTime());
    String retryAfter = dateFormat.format(new Date(date.getTime() + 30000));

    MultipleCallSleeper sleeper = new MultipleCallSleeper();
    RetryUnsuccessfulResponseHandler handler = new RetryUnsuccessfulResponseHandler(
        testRetryConfig(sleeper), clock);
    CountingLowLevelHttpRequest failingRequest = CountingLowLevelHttpRequest.fromStatus(
        503, ImmutableMap.of("retry-after", retryAfter));
    HttpRequest request = TestUtils.createRequest(failingRequest);
    request.setUnsuccessfulResponseHandler(handler);
    request.setNumberOfRetries(4);

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
  public void testInvalidRetryAfterFailsOverToExpBackOff() throws IOException {
    MultipleCallSleeper sleeper = new MultipleCallSleeper();
    RetryUnsuccessfulResponseHandler handler = new RetryUnsuccessfulResponseHandler(
        testRetryConfig(sleeper));
    CountingLowLevelHttpRequest failingRequest = CountingLowLevelHttpRequest.fromStatus(
        503, ImmutableMap.of("retry-after", "not valid"));
    HttpRequest request = TestUtils.createRequest(failingRequest);
    request.setUnsuccessfulResponseHandler(handler);
    request.setNumberOfRetries(4);

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
  public void testDoesNotRetryWhenRetryAfterIsTooLong() throws IOException {
    MultipleCallSleeper sleeper = new MultipleCallSleeper();
    RetryUnsuccessfulResponseHandler handler = new RetryUnsuccessfulResponseHandler(
        testRetryConfig(sleeper));
    CountingLowLevelHttpRequest failingRequest = CountingLowLevelHttpRequest.fromStatus(
        503, ImmutableMap.of("retry-after", "121"));
    HttpRequest request = TestUtils.createRequest(failingRequest);
    request.setUnsuccessfulResponseHandler(handler);
    request.setNumberOfRetries(MAX_RETRIES);

    try {
      request.execute();
      fail("No exception thrown for HTTP error");
    } catch (HttpResponseException e) {
      assertEquals(503, e.getStatusCode());
    }

    assertEquals(0, sleeper.getCount());
    assertEquals(1, failingRequest.getCount());
  }

  @Test
  public void testDoesNotRetryAfterInterruption() throws IOException {
    MockSleeper sleeper = new MockSleeper() {
      @Override
      public void sleep(long millis) throws InterruptedException {
        super.sleep(millis);
        throw new InterruptedException();
      }
    };
    RetryUnsuccessfulResponseHandler handler = new RetryUnsuccessfulResponseHandler(
        testRetryConfig(sleeper));
    CountingLowLevelHttpRequest failingRequest = CountingLowLevelHttpRequest.fromStatus(503);
    HttpRequest request = TestUtils.createRequest(failingRequest);
    request.setUnsuccessfulResponseHandler(handler);
    request.setNumberOfRetries(MAX_RETRIES);

    try {
      request.execute();
      fail("No exception thrown for HTTP error");
    } catch (HttpResponseException e) {
      assertEquals(503, e.getStatusCode());
    }

    assertEquals(1, sleeper.getCount());
    assertEquals(1, failingRequest.getCount());
  }

  private RetryConfig testRetryConfig(Sleeper sleeper) {
    return TEST_RETRY_CONFIG.setSleeper(sleeper).build();
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
