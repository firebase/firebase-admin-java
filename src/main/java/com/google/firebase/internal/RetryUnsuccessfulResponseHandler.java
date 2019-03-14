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

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.api.client.util.BackOff;
import com.google.api.client.util.BackOffUtils;
import com.google.api.client.util.Clock;
import com.google.api.client.util.Sleeper;
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.Date;

/**
 * An {@code HttpUnsuccessfulResponseHandler} that retries failing requests after an interval. The
 * interval is determined by checking the Retry-After header on the last response. If that
 * header is not present, uses exponential back off to delay subsequent retries.
 */
final class RetryUnsuccessfulResponseHandler implements HttpUnsuccessfulResponseHandler {

  private final RetryConfig retryConfig;
  private final BackOff backOff;
  private final Sleeper sleeper;
  private final Clock clock;

  RetryUnsuccessfulResponseHandler(RetryConfig retryConfig) {
    this(retryConfig, Clock.SYSTEM);
  }

  RetryUnsuccessfulResponseHandler(RetryConfig retryConfig, Clock clock) {
    this.retryConfig = checkNotNull(retryConfig);
    this.backOff = retryConfig.newBackOff();
    this.sleeper = retryConfig.getSleeper();
    this.clock = checkNotNull(clock);
  }

  @Override
  public boolean handleResponse(
      HttpRequest request, HttpResponse response, boolean supportsRetry) throws IOException {

    if (!supportsRetry) {
      return false;
    }

    int statusCode = response.getStatusCode();
    if (!retryConfig.getRetryStatusCodes().contains(statusCode)) {
      return false;
    }

    try {
      return waitAndRetry(response);
    } catch (InterruptedException e) {
      // ignore
    }
    return false;
  }

  RetryConfig getRetryConfig() {
    return retryConfig;
  }

  private boolean waitAndRetry(HttpResponse response) throws IOException, InterruptedException {
    String retryAfterHeader = response.getHeaders().getRetryAfter();
    if (!Strings.isNullOrEmpty(retryAfterHeader)) {
      long intervalMillis = parseRetryAfterHeaderIntoMillis(retryAfterHeader.trim());
      // Retry-after header can specify very long delay intervals (e.g. 24 hours). If we cannot
      // wait that long, we should not perform any retries at all. In general it is not correct to
      // retry earlier than what the server has recommended to us.
      if (intervalMillis > retryConfig.getMaxIntervalMillis()) {
        return false;
      }

      if (intervalMillis > 0) {
        sleeper.sleep(intervalMillis);
        return true;
      }
    }

    return BackOffUtils.next(sleeper, backOff);
  }

  private long parseRetryAfterHeaderIntoMillis(String retryAfter) {
    try {
      return Long.parseLong(retryAfter) * 1000;
    } catch (NumberFormatException e) {
      Date date = DateUtils.parseDate(retryAfter);
      if (date != null) {
        return date.getTime() - clock.currentTimeMillis();
      }
    }

    return -1L;
  }
}
