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

import com.google.common.base.Strings;

/**
 * Represents an OAuth access token, which can be used to access Firebase and other qualified
 * Google APIs. Encapsulates both the token string, and its expiration time.
 *
 * @deprecated Use GoogleCredentials and associated classes.
 */
public class GoogleOAuthAccessToken {


  private final String accessToken;
  private final long expiryTime;

  /**
   * Create a new GoogleOAuthAccessToken instance
   *
   * @param accessToken JWT access token string
   * @param expiryTime Time at which the token will expire (milliseconds since epoch)
   * @throws IllegalArgumentException If the token is null or empty
   */
  public GoogleOAuthAccessToken(String accessToken, long expiryTime) {
    checkArgument(!Strings.isNullOrEmpty(accessToken), "Access token must not be null");
    this.accessToken = accessToken;
    this.expiryTime = expiryTime;
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

}
