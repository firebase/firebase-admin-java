/*
 * Copyright 2017 Google Inc.
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

package com.google.firebase.auth;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.concurrent.TimeUnit;

/**
 * A set of additional options that can be passed to
 * {@link FirebaseAuth#createSessionCookieAsync(String, SessionCookieOptions)}.
 */
public class SessionCookieOptions {

  private final long expiresIn;

  private SessionCookieOptions(Builder builder) {
    checkArgument(builder.expiresIn > TimeUnit.MINUTES.toMillis(5),
        "expiresIn duration must be at least 5 minutes");
    checkArgument(builder.expiresIn < TimeUnit.DAYS.toMillis(14),
        "expiresIn duration must be at most 14 days");
    this.expiresIn = builder.expiresIn;
  }

  long getExpiresInSeconds() {
    return TimeUnit.MILLISECONDS.toSeconds(expiresIn);
  }

  /**
   * Creates a new {@link Builder}.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private long expiresIn;

    private Builder() {}

    /**
     * Sets the duration until the cookie is expired in milliseconds. Must be between 5 minutes
     * and 14 days.
     *
     * @param expiresInMillis Time duration in milliseconds.
     * @return This builder.
     */
    public Builder setExpiresIn(long expiresInMillis) {
      this.expiresIn = expiresInMillis;
      return this;
    }

    /**
     * Creates a new {@link SessionCookieOptions} instance.
     */
    public SessionCookieOptions build() {
      return new SessionCookieOptions(this);
    }
  }

}
