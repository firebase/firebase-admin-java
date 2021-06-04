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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.testing.TestUtils;
import java.io.IOException;
import java.util.Map;
import org.junit.Test;

public class IncomingHttpResponseTest {

  private static final String TEST_URL = "https://firebase.google.com/response";
  private static final OutgoingHttpRequest REQUEST = new OutgoingHttpRequest("GET", TEST_URL);
  private static final Map<String, Object> RESPONSE_HEADERS =
      ImmutableMap.<String, Object>of("x-firebase-client", ImmutableList.of("test-version"));
  private static final String RESPONSE_BODY = "test response";

  @Test(expected = NullPointerException.class)
  public void testNullHttpResponse() {
    new IncomingHttpResponse(null, "content");
  }

  @Test
  public void testNullHttpResponseException() throws IOException {
    try {
      new IncomingHttpResponse(null, REQUEST);
      fail("No exception thrown for null HttpResponseException");
    } catch (NullPointerException ignore) {
      // expected
    }

    HttpRequest request = createHttpRequest();
    try {
      new IncomingHttpResponse(null, request);
      fail("No exception thrown for null HttpResponseException");
    } catch (NullPointerException ignore) {
      // expected
    }
  }

  @Test
  public void testIncomingHttpResponse() throws IOException {
    HttpResponseException httpError = createHttpResponseException();

    IncomingHttpResponse response = new IncomingHttpResponse(httpError, REQUEST);

    assertEquals(HttpStatusCodes.STATUS_CODE_SERVER_ERROR, response.getStatusCode());
    assertEquals(RESPONSE_BODY, response.getContent());
    assertEquals(RESPONSE_HEADERS, response.getHeaders());
    assertFalse(response.getHeaders().isEmpty());
    assertSame(REQUEST, response.getRequest());
  }

  @Test
  public void testIncomingHttpResponseWithRequest() throws IOException {
    HttpResponseException httpError = createHttpResponseException();
    HttpRequest httpRequest = createHttpRequest();

    IncomingHttpResponse response = new IncomingHttpResponse(httpError, httpRequest);

    assertEquals(HttpStatusCodes.STATUS_CODE_SERVER_ERROR, response.getStatusCode());
    assertEquals(RESPONSE_BODY, response.getContent());
    assertEquals(RESPONSE_HEADERS, response.getHeaders());
    OutgoingHttpRequest request = response.getRequest();
    assertEquals(HttpMethods.POST, request.getMethod());
    assertEquals(TEST_URL, request.getUrl());
  }

  @Test
  public void testIncomingHttpResponseWithResponse() throws IOException {
    HttpResponse httpResponse = createHttpResponse();

    IncomingHttpResponse response = new IncomingHttpResponse(httpResponse, RESPONSE_BODY);

    assertEquals(HttpStatusCodes.STATUS_CODE_OK, response.getStatusCode());
    assertEquals(RESPONSE_BODY, response.getContent());
    assertTrue(response.getHeaders().isEmpty());
    OutgoingHttpRequest request = response.getRequest();
    assertEquals(HttpMethods.POST, request.getMethod());
    assertEquals(TEST_URL, request.getUrl());
  }

  private HttpResponseException createHttpResponseException() throws IOException {
    MockLowLevelHttpResponse lowLevelResponse = new MockLowLevelHttpResponse()
        .setStatusCode(HttpStatusCodes.STATUS_CODE_SERVER_ERROR)
        .addHeader("X-Firebase-Client", "test-version")
        .setContent(RESPONSE_BODY);
    MockLowLevelHttpRequest lowLevelRequest = new MockLowLevelHttpRequest()
        .setResponse(lowLevelResponse);
    HttpRequest request = TestUtils.createRequest(lowLevelRequest, new GenericUrl(TEST_URL));
    try {
      request.execute();
      throw new IOException("HttpResponseException not thrown");
    } catch (HttpResponseException e) {
      return e;
    }
  }

  private HttpRequest createHttpRequest() throws IOException {
    MockLowLevelHttpResponse lowLevelResponse = new MockLowLevelHttpResponse()
        .setContent("{}");
    MockLowLevelHttpRequest lowLevelRequest = new MockLowLevelHttpRequest()
        .setResponse(lowLevelResponse);
    return TestUtils.createRequest(lowLevelRequest, new GenericUrl(TEST_URL));
  }

  private HttpResponse createHttpResponse() throws IOException {
    HttpRequest request = createHttpRequest();
    return request.execute();
  }
}
