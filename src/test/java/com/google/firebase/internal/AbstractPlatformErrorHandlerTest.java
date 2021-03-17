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

package com.google.firebase.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.util.GenericData;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import com.google.firebase.IncomingHttpResponse;
import java.io.IOException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import org.junit.Test;

public class AbstractPlatformErrorHandlerTest {

  private static final HttpRequestInfo TEST_REQUEST = HttpRequestInfo.buildGetRequest(
      "https://firebase.google.com");

  @Test
  public void testPlatformError() {
    String payload = "{\"error\": {\"status\": \"UNAVAILABLE\", \"message\": \"Test error\"}}";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(HttpStatusCodes.STATUS_CODE_SERVER_ERROR)
        .setContent(payload);
    ErrorHandlingHttpClient<MockFirebaseException> client = createHttpClient(response);

    try {
      client.sendAndParse(TEST_REQUEST, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (MockFirebaseException e) {
      assertEquals(ErrorCode.UNAVAILABLE, e.getErrorCode());
      assertEquals("Test error", e.getMessage());
      assertHttpResponse(e, HttpStatusCodes.STATUS_CODE_SERVER_ERROR, payload);
      assertNotNull(e.getCause());
    }
  }

  @Test
  public void testNonJsonError() {
    String payload = "not json";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(HttpStatusCodes.STATUS_CODE_SERVER_ERROR)
        .setContent(payload);
    ErrorHandlingHttpClient<MockFirebaseException> client = createHttpClient(response);

    try {
      client.sendAndParse(TEST_REQUEST, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (MockFirebaseException e) {
      assertEquals(ErrorCode.INTERNAL, e.getErrorCode());
      assertEquals("Unexpected HTTP response with status: 500\nnot json", e.getMessage());
      assertHttpResponse(e, HttpStatusCodes.STATUS_CODE_SERVER_ERROR, payload);
      assertNotNull(e.getCause());
    }
  }

  @Test
  public void testPlatformErrorWithoutCode() {
    String payload = "{\"error\": {\"message\": \"Test error\"}}";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(HttpStatusCodes.STATUS_CODE_SERVER_ERROR)
        .setContent(payload);
    ErrorHandlingHttpClient<MockFirebaseException> client = createHttpClient(response);

    try {
      client.sendAndParse(TEST_REQUEST, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (MockFirebaseException e) {
      assertEquals(ErrorCode.INTERNAL, e.getErrorCode());
      assertEquals("Test error", e.getMessage());
      assertHttpResponse(e, HttpStatusCodes.STATUS_CODE_SERVER_ERROR, payload);
      assertNotNull(e.getCause());
    }
  }

  @Test
  public void testPlatformErrorWithoutMessage() {
    String payload = "{\"error\": {\"status\": \"INVALID_ARGUMENT\"}}";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(HttpStatusCodes.STATUS_CODE_SERVER_ERROR)
        .setContent(payload);
    ErrorHandlingHttpClient<MockFirebaseException> client = createHttpClient(response);

    try {
      client.sendAndParse(TEST_REQUEST, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (MockFirebaseException e) {
      assertEquals(ErrorCode.INVALID_ARGUMENT, e.getErrorCode());
      assertEquals("Unexpected HTTP response with status: 500\n" + payload, e.getMessage());
      assertHttpResponse(e, HttpStatusCodes.STATUS_CODE_SERVER_ERROR, payload);
      assertNotNull(e.getCause());
    }
  }

  @Test
  public void testPlatformErrorWithoutCodeOrMessage() {
    String payload = "{}";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(HttpStatusCodes.STATUS_CODE_SERVER_ERROR)
        .setContent(payload);
    ErrorHandlingHttpClient<MockFirebaseException> client = createHttpClient(response);

    try {
      client.sendAndParse(TEST_REQUEST, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (MockFirebaseException e) {
      assertEquals(ErrorCode.INTERNAL, e.getErrorCode());
      assertEquals("Unexpected HTTP response with status: 500\n" + payload, e.getMessage());
      assertHttpResponse(e, HttpStatusCodes.STATUS_CODE_SERVER_ERROR, payload);
      assertNotNull(e.getCause());
    }
  }

  @Test
  public void testGenericIOException() {
    IOException exception = new IOException("Test");
    ErrorHandlingHttpClient<MockFirebaseException> client = createHttpClient(exception);

    try {
      client.sendAndParse(TEST_REQUEST, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (MockFirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals(
          "Unknown error while making a remote service call: Test", e.getMessage());
      assertNull(e.getHttpResponse());
      assertSame(exception, e.getCause());
    }
  }

  @Test
  public void testTimeoutError() {
    IOException exception = new IOException("Test", new SocketTimeoutException());
    ErrorHandlingHttpClient<MockFirebaseException> client = createHttpClient(exception);

    try {
      client.sendAndParse(TEST_REQUEST, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (MockFirebaseException e) {
      assertEquals(ErrorCode.DEADLINE_EXCEEDED, e.getErrorCode());
      assertEquals(
          "Timed out while making an API call: Test", e.getMessage());
      assertNull(e.getHttpResponse());
      assertSame(exception, e.getCause());
    }
  }

  @Test
  public void testNoRouteToHostError() {
    IOException exception = new IOException("Test", new NoRouteToHostException());
    ErrorHandlingHttpClient<MockFirebaseException> client = createHttpClient(exception);

    try {
      client.sendAndParse(TEST_REQUEST, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (MockFirebaseException e) {
      assertEquals(ErrorCode.UNAVAILABLE, e.getErrorCode());
      assertEquals(
          "Failed to establish a connection: Test", e.getMessage());
      assertNull(e.getHttpResponse());
      assertSame(exception, e.getCause());
    }
  }

  @Test
  public void testUnknownHostError() {
    IOException exception = new IOException("Test", new UnknownHostException());
    ErrorHandlingHttpClient<MockFirebaseException> client = createHttpClient(exception);

    try {
      client.sendAndParse(TEST_REQUEST, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (MockFirebaseException e) {
      assertEquals(ErrorCode.UNAVAILABLE, e.getErrorCode());
      assertEquals(
          "Failed to establish a connection: Test", e.getMessage());
      assertNull(e.getHttpResponse());
      assertSame(exception, e.getCause());
    }
  }

  @Test
  public void testParseError() {
    String payload = "not json";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setContent(payload);
    ErrorHandlingHttpClient<MockFirebaseException> client = createHttpClient(response);

    try {
      client.sendAndParse(TEST_REQUEST, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (MockFirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertTrue(e.getMessage().startsWith("Error while parsing HTTP response: "));
      assertHttpResponse(e, HttpStatusCodes.STATUS_CODE_OK, payload);
      assertNotNull(e.getCause());
    }
  }

  @Test
  public void testUnknownHttpError() {
    String payload = "{\"error\": {\"message\": \"Test error\"}}";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(512)
        .setContent(payload);
    ErrorHandlingHttpClient<MockFirebaseException> client = createHttpClient(response);

    try {
      client.sendAndParse(TEST_REQUEST, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (MockFirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals("Test error", e.getMessage());
      assertHttpResponse(e, 512, payload);
      assertNotNull(e.getCause());
    }
  }

  private ErrorHandlingHttpClient<MockFirebaseException> createHttpClient(
      MockLowLevelHttpResponse response) {
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    return new ErrorHandlingHttpClient<>(
        transport.createRequestFactory(),
        Utils.getDefaultJsonFactory(),
        new TestPlatformErrorHandler());
  }

  private ErrorHandlingHttpClient<MockFirebaseException> createHttpClient(
      final IOException exception) {
    MockHttpTransport transport = new MockHttpTransport(){
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
        throw exception;
      }
    };
    return new ErrorHandlingHttpClient<>(
        transport.createRequestFactory(),
        Utils.getDefaultJsonFactory(),
        new TestPlatformErrorHandler());
  }

  private void assertHttpResponse(FirebaseException e, int statusCode, String content) {
    IncomingHttpResponse httpResponse = e.getHttpResponse();
    assertNotNull(httpResponse);
    assertEquals(statusCode, httpResponse.getStatusCode());
    assertEquals(content, httpResponse.getContent());
    assertEquals(HttpMethods.GET, httpResponse.getRequest().getMethod());
  }

  private static class TestPlatformErrorHandler extends
      AbstractPlatformErrorHandler<MockFirebaseException> {

    TestPlatformErrorHandler() {
      super(Utils.getDefaultJsonFactory());
    }

    @Override
    protected MockFirebaseException createException(FirebaseException base) {
      return new MockFirebaseException(base);
    }
  }

  private static class MockFirebaseException extends FirebaseException {
    MockFirebaseException(FirebaseException base) {
      super(base.getErrorCode(), base.getMessage(), base.getCause(), base.getHttpResponse());
    }
  }
}
