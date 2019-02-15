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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.api.client.http.EmptyContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpBackOffIOExceptionHandler;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.MockGoogleCredentials;
import java.io.IOException;
import org.junit.Test;

public class RetryInitializerTest {

  private static final GenericUrl TEST_URL = new GenericUrl("https://firebase.google.com");
  private static final HttpCredentialsAdapter TEST_CREDENTIALS = new HttpCredentialsAdapter(
      new MockGoogleCredentials());
  private static final RetryConfig RETRY_CONFIG = RetryConfig.builder()
      .setMaxRetries(5)
      .setRetryStatusCodes(ImmutableList.of(503))
      .build();

  @Test
  public void testEnableRetry() throws IOException {
    RetryInitializer initializer = new RetryInitializer(TEST_CREDENTIALS, RETRY_CONFIG);
    HttpRequest request = createRequest();

    initializer.initialize(request);

    assertEquals(5, request.getNumberOfRetries());
    assertTrue(
        request.getUnsuccessfulResponseHandler() instanceof CredentialsResponseHandlerDecorator);
    assertTrue(request.getIOExceptionHandler() instanceof HttpBackOffIOExceptionHandler);
  }

  @Test
  public void testDisableRetry() throws IOException {
    RetryInitializer initializer = new RetryInitializer(TEST_CREDENTIALS, null);
    HttpRequest request = createRequest();

    initializer.initialize(request);

    assertEquals(0, request.getNumberOfRetries());
    assertNull(request.getUnsuccessfulResponseHandler());
    assertNull(request.getIOExceptionHandler());
  }

  private HttpRequest createRequest() throws IOException {
    HttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpRequest(new MockLowLevelHttpRequest())
        .build();
    HttpRequestFactory requestFactory = transport.createRequestFactory();
    return requestFactory.buildPostRequest(TEST_URL, new EmptyContent());
  }
}
