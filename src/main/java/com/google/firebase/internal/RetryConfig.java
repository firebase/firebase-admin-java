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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.util.BackOff;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.client.util.Sleeper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Configures when and how HTTP requests should be retried.
 */
public final class RetryConfig {

  private static final int INITIAL_INTERVAL_MILLIS = 500;

  private final List<Integer> retryStatusCodes;
  private final boolean retryOnIOExceptions;
  private final int maxRetries;
  private final Sleeper sleeper;
  private final ExponentialBackOff.Builder backOffBuilder;

  private RetryConfig(Builder builder) {
    if (builder.retryStatusCodes != null) {
      this.retryStatusCodes = ImmutableList.copyOf(builder.retryStatusCodes);
    } else {
      this.retryStatusCodes = ImmutableList.of();
    }

    this.retryOnIOExceptions = builder.retryOnIOExceptions;
    checkArgument(builder.maxRetries >= 0, "maxRetries must not be negative");
    this.maxRetries = builder.maxRetries;
    this.sleeper = checkNotNull(builder.sleeper);
    this.backOffBuilder = new ExponentialBackOff.Builder()
        .setInitialIntervalMillis(INITIAL_INTERVAL_MILLIS)
        .setMaxIntervalMillis(builder.maxIntervalMillis)
        .setMultiplier(builder.backOffMultiplier)
        .setRandomizationFactor(0);

    // Force validation of arguments by building the BackOff object
    this.backOffBuilder.build();
  }

  List<Integer> getRetryStatusCodes() {
    return retryStatusCodes;
  }

  boolean isRetryOnIOExceptions() {
    return retryOnIOExceptions;
  }

  int getMaxRetries() {
    return maxRetries;
  }

  int getMaxIntervalMillis() {
    return backOffBuilder.getMaxIntervalMillis();
  }

  double getBackOffMultiplier() {
    return backOffBuilder.getMultiplier();
  }

  Sleeper getSleeper() {
    return sleeper;
  }

  BackOff newBackOff() {
    return backOffBuilder.build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private List<Integer> retryStatusCodes;
    private boolean retryOnIOExceptions;
    private int maxRetries;
    private int maxIntervalMillis = (int) TimeUnit.MINUTES.toMillis(2);
    private double backOffMultiplier = 2.0;
    private Sleeper sleeper = Sleeper.DEFAULT;

    private Builder() { }

    /**
     * Sets a list of HTTP status codes that should be retried. If null or empty, HTTP requests
     * will not be retried as long as they result in some HTTP response message.
     *
     * @param retryStatusCodes A list of status codes.
     * @return This builder.
     */
    public Builder setRetryStatusCodes(List<Integer> retryStatusCodes) {
      this.retryStatusCodes = retryStatusCodes;
      return this;
    }

    /**
     * Sets whether requests should be retried on IOExceptions.
     *
     * @param retryOnIOExceptions A boolean indicating whether to retry on IOExceptions.
     * @return This builder.
     */
    public Builder setRetryOnIOExceptions(boolean retryOnIOExceptions) {
      this.retryOnIOExceptions = retryOnIOExceptions;
      return this;
    }

    /**
     * Maximum number of retry attempts for a request. This is the cumulative total for all retries
     * regardless of their cause (I/O errors and HTTP error responses).
     *
     * @param maxRetries A non-negative integer.
     * @return This builder.
     */
    public Builder setMaxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
      return this;
    }

    /**
     * Maximum interval to wait before a request should be retried. Must be at least 500
     * milliseconds. Defaults to 2 minutes.
     *
     * @param maxIntervalMillis Interval in milliseconds.
     * @return This builder.
     */
    public Builder setMaxIntervalMillis(int maxIntervalMillis) {
      this.maxIntervalMillis = maxIntervalMillis;
      return this;
    }

    /**
     * Factor by which the retry interval is multiplied when employing exponential back
     * off to delay consecutive retries of the same request. Must be at least 1. Defaults
     * to 2.
     *
     * @param backOffMultiplier Multiplication factor for exponential back off.
     * @return This builder.
     */
    public Builder setBackOffMultiplier(double backOffMultiplier) {
      this.backOffMultiplier = backOffMultiplier;
      return this;
    }

    @VisibleForTesting
    Builder setSleeper(Sleeper sleeper) {
      this.sleeper = sleeper;
      return this;
    }

    public RetryConfig build() {
      return new RetryConfig(this);
    }
  }
}
