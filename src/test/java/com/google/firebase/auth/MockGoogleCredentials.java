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

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * A flexible GoogleCredentials implementation fortesting purposes.
 */
public class MockGoogleCredentials extends GoogleCredentials {

  private String tokenValue;
  private long expiryTime;

  public MockGoogleCredentials() {
    this(null);
  }

  public MockGoogleCredentials(String tokenValue) {
    this(tokenValue, System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1));
  }

  public MockGoogleCredentials(String tokenValue, long expiryTime) {
    this.tokenValue = tokenValue;
    this.expiryTime = expiryTime;
  }

  @Override
  public AccessToken refreshAccessToken() throws IOException {
    return new AccessToken(tokenValue, new Date(expiryTime));
  }

  public void setExpiryTime(long expiryTime) {
    this.expiryTime = expiryTime;
  }
}
