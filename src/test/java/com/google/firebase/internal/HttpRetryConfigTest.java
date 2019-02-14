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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import com.google.api.client.util.ExponentialBackOff;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Test;

public class HttpRetryConfigTest {

  @Test
  public void testEmptyBuilder() {
    HttpRetryConfig config = HttpRetryConfig.builder().build();

    assertTrue(config.getRetryStatusCodes().isEmpty());
    assertEquals(0, config.getMaxRetries());
    ExponentialBackOff backoff = (ExponentialBackOff) config.newBackoff();
    assertEquals(500, backoff.getInitialIntervalMillis());
    assertEquals(2 * 60 * 1000, backoff.getMaxIntervalMillis());
    assertEquals(2.0, backoff.getMultiplier(), 0.01);
    assertEquals(0.0, backoff.getRandomizationFactor(), 0.01);
    assertNotSame(backoff, config.newBackoff());
  }

  @Test
  public void testBuilder() {
    ImmutableList<Integer> statusCodes = ImmutableList.of(500, 503);
    HttpRetryConfig config = HttpRetryConfig.builder()
        .setMaxRetries(4)
        .setRetryStatusCodes(statusCodes)
        .setMaxIntervalInMillis(5 * 60 * 1000)
        .setMultiplier(1.5)
        .build();

    assertEquals(2, config.getRetryStatusCodes().size());
    assertEquals(statusCodes.get(0), config.getRetryStatusCodes().get(0));
    assertEquals(statusCodes.get(1), config.getRetryStatusCodes().get(1));
    assertEquals(4, config.getMaxRetries());
    ExponentialBackOff backoff = (ExponentialBackOff) config.newBackoff();
    assertEquals(500, backoff.getInitialIntervalMillis());
    assertEquals(5 * 60 * 1000, backoff.getMaxIntervalMillis());
    assertEquals(1.5, backoff.getMultiplier(), 0.01);
    assertEquals(0.0, backoff.getRandomizationFactor(), 0.01);
    assertNotSame(backoff, config.newBackoff());
  }

  @Test
  public void testExponentialBackoff() throws IOException {
    HttpRetryConfig config = HttpRetryConfig.builder().build();

    ExponentialBackOff backoff = (ExponentialBackOff) config.newBackoff();

    assertEquals(500, backoff.nextBackOffMillis());
    assertEquals(1000, backoff.nextBackOffMillis());
    assertEquals(2000, backoff.nextBackOffMillis());
    assertEquals(4000, backoff.nextBackOffMillis());
  }
}
