/*
 * Copyright 2021 Google Inc.
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

import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;

/**
 * Provides a hook to override application default credentials (ADC) lookup for tests. ADC has
 * a dependency on environment variables, and Java famously doesn't support environment variable
 * manipulation at runtime. With this class, the test cases that require ADC has a way to register
 * their own mock credentials as ADC.
 *
 * <p>Once we are able to upgrade to Mockito 3.x (requires Java 8+), we can drop this class
 * altogether, and use Mockito tools to mock the behavior of the GoogleCredentials static methods.
 */
public class ApplicationDefaultCredentialsProvider {

  private static GoogleCredentials cachedCredentials;

  public static GoogleCredentials getApplicationDefault() throws IOException {
    if (cachedCredentials != null) {
      return cachedCredentials;
    }

    return GoogleCredentials.getApplicationDefault();
  }

  public static void setApplicationDefault(GoogleCredentials credentials) {
    cachedCredentials = credentials;
  }
}
