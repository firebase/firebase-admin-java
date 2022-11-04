/*
 * Copyright 2022 Google Inc.
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

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A Mock Credentials implementation that can be used with the Emulator Suite
 */
public final class EmulatorCredentials extends GoogleCredentials {

  public EmulatorCredentials() {
    super(newToken());
  }

  private static AccessToken newToken() {
    return new AccessToken("owner",
            new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)));
  }

  @Override
  public AccessToken refreshAccessToken() {
    return newToken();
  }

  @Override
  public Map<String, List<String>> getRequestMetadata() throws IOException {
    return ImmutableMap.of();
  }
}
