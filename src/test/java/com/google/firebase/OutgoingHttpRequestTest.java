/*
 * Copyright 2020 Google Inc.
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

package com.google.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import org.junit.Test;

public class OutgoingHttpRequestTest {

  private static final String TEST_URL = "https://firebase.google.com/request";

  @Test(expected = NullPointerException.class)
  public void testNullHttpRequest() {
    new OutgoingHttpRequest(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNullMethod() {
    new OutgoingHttpRequest(null, TEST_URL);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEmptyMethod() {
    new OutgoingHttpRequest("", TEST_URL);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNullUrl() {
    new OutgoingHttpRequest(HttpMethods.GET, null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEmptyUrl() {
    new OutgoingHttpRequest(HttpMethods.GET, "");
  }

  @Test
  public void testOutgoingHttpRequest() {
    OutgoingHttpRequest request = new OutgoingHttpRequest(HttpMethods.GET, TEST_URL);

    assertEquals(HttpMethods.GET, request.getMethod());
    assertEquals(TEST_URL, request.getUrl());
    assertNull(request.getContent());
    assertTrue(request.getHeaders().isEmpty());
  }

  @Test
  public void testOutgoingHttpRequestWithContent() throws IOException {
    JsonHttpContent streamingContent = new JsonHttpContent(
        Utils.getDefaultJsonFactory(),
        ImmutableMap.of("key", "value"));
    HttpRequest httpRequest = new MockHttpTransport().createRequestFactory()
        .buildPostRequest(new GenericUrl(TEST_URL), streamingContent);
    httpRequest.getHeaders().set("X-Firebase-Client", "test-version");

    OutgoingHttpRequest request = new OutgoingHttpRequest(httpRequest);

    assertEquals(HttpMethods.POST, request.getMethod());
    assertEquals(TEST_URL, request.getUrl());
    assertSame(streamingContent, request.getContent());
    assertEquals("test-version", request.getHeaders().get("x-firebase-client"));
  }
}
