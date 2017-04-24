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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;

/**
 * Represents an OAuth access token, which can be used to access Firebase and other qualified
 * Google APIs. Encapsulates both the token string, and its expiration time.
 */
public class GoogleOAuthAccessToken {

  private static final Clock DEFAULT_CLOCK = new Clock();

  private final String accessToken;
  private final long expiryTime;
  private final Clock clock;

  /**
   * Create a new GoogleOAuthAccessToken instance
   *
   * @param accessToken JWT access token string
   * @param expiryTime Time at which the token will expire (milliseconds since epoch)
   * @throws IllegalArgumentException If the token is null or empty
   */
  public GoogleOAuthAccessToken(String accessToken, long expiryTime) {
    this(accessToken, expiryTime, DEFAULT_CLOCK);
  }

  GoogleOAuthAccessToken(String accessToken, long expiryTime, Clock clock) {
    checkArgument(!Strings.isNullOrEmpty(accessToken), "Access token must not be null");
    this.accessToken = accessToken;
    this.expiryTime = expiryTime;
    this.clock = checkNotNull(clock, "Clock must not be null");
  }

  /**
   * Returns the JWT access token.
   */
  public String getAccessToken() {
    return accessToken;
  }

  /**
   * Returns the expiration time as a milliseconds since epoch timestamp.
   */
  public long getExpiryTime() {
    return expiryTime;
  }

  /**
   * Returns true if the token is already expired, and false otherwise.
   */
  public boolean isExpired() {
    return expiryTime <= clock.now();
  }

  static class Clock {
    public long now() {
      return System.currentTimeMillis();
    }
  }

}
