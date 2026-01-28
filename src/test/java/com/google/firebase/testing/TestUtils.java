/*
 * Copyright 2017 Google LLC
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

package com.google.firebase.testing;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.EmptyContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.PublicKey;
import java.util.List;

/** Test Utils for use by all tests (both unit and integration tests). */
public class TestUtils {

  public static final long TEST_TIMEOUT_MILLIS = 7 * 1000;
  private static final GenericUrl TEST_URL = new GenericUrl("https://firebase.google.com");

  public static boolean verifySignature(JsonWebSignature token, List<PublicKey> keys)
      throws Exception {
    for (PublicKey key : keys) {
      if (token.verifySignature(key)) {
        return true;
      }
    }
    return false;
  }

  public static String loadResource(String path) {
    InputStream stream = TestUtils.class.getClassLoader().getResourceAsStream(path);
    checkNotNull(stream, "Failed to load resource: %s", path);
    try (InputStreamReader reader = new InputStreamReader(stream)) {
      return CharStreams.toString(reader);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static GoogleCredentials getCertCredential(InputStream stream) {
    try {
      return GoogleCredentials.fromStream(stream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static HttpRequest createRequest() throws IOException {
    return createRequest(new MockLowLevelHttpRequest());
  }

  public static HttpRequest createRequest(MockLowLevelHttpRequest request) throws IOException {
    return createRequest(request, TEST_URL);
  }

  /**
   * Creates a test HTTP POST request for the given target URL.
   */
  public static HttpRequest createRequest(
      MockLowLevelHttpRequest request, GenericUrl url) throws IOException {
    HttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpRequest(request)
        .build();
    HttpRequestFactory requestFactory = transport.createRequestFactory();
    return requestFactory.buildPostRequest(url, new EmptyContent());
  }

  public static HttpTransport createFaultyHttpTransport() {
    return new HttpTransport() {
      @Override
      protected LowLevelHttpRequest buildRequest(String s, String s1) throws IOException {
        throw new IOException("transport error");
      }
    };
  }
}
