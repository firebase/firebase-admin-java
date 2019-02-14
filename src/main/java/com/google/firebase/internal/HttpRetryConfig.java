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

import com.google.api.client.util.BackOff;
import com.google.api.client.util.ExponentialBackOff;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class HttpRetryConfig {

  private final List<Integer> retryStatusCodes;
  private final int maxRetries;
  private final ExponentialBackOff.Builder backOffBuilder;

  private HttpRetryConfig(Builder builder) {
    if (builder.retryStatusCodes != null) {
      this.retryStatusCodes = ImmutableList.copyOf(builder.retryStatusCodes);
    } else {
      this.retryStatusCodes = ImmutableList.of();
    }
    checkArgument(builder.maxRetries >= 0, "maxRetries must not be negative");
    this.maxRetries = builder.maxRetries;
    this.backOffBuilder = new ExponentialBackOff.Builder()
        .setMaxIntervalMillis(builder.maxIntervalInMillis)
        .setMultiplier(builder.multiplier)
        .setRandomizationFactor(0);
  }

  BackOff newBackoff() {
    return backOffBuilder.build();
  }

  int getMaxRetries() {
    return maxRetries;
  }

  List<Integer> getRetryStatusCodes() {
    return retryStatusCodes;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private List<Integer> retryStatusCodes;
    private int maxRetries;
    private int maxIntervalInMillis = (int) TimeUnit.MINUTES.toMillis(2);
    private double multiplier = 2.0;

    private Builder() { }

    public Builder setRetryStatusCodes(List<Integer> retryStatusCodes) {
      this.retryStatusCodes = retryStatusCodes;
      return this;
    }

    public Builder setMaxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
      return this;
    }

    public Builder setMaxIntervalInMillis(int maxIntervalInMillis) {
      this.maxIntervalInMillis = maxIntervalInMillis;
      return this;
    }

    public Builder setMultiplier(double multiplier) {
      this.multiplier = multiplier;
      return this;
    }

    public HttpRetryConfig build() {
      return new HttpRetryConfig(this);
    }
  }
}
