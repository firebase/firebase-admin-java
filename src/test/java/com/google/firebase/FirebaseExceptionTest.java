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

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.firebase.testing.TestUtils;
import java.io.IOException;
import org.junit.Test;

@SuppressWarnings("ThrowableNotThrown")
public class FirebaseExceptionTest {

  @Test(expected = NullPointerException.class)
  public void testFirebaseExceptionWithoutErrorCode() {
    new FirebaseException(
        null,
        "Test error",
        null,
        null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFirebaseExceptionWithNullMessage() {
    new FirebaseException(
        ErrorCode.INTERNAL,
        null,
        null,
        null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFirebaseExceptionWithEmptyMessage() {
    new FirebaseException(
        ErrorCode.INTERNAL,
        "",
        null,
        null);
  }

  @Test
  public void testFirebaseExceptionWithoutResponseAndCause() {
    FirebaseException exception = new FirebaseException(
        ErrorCode.INTERNAL,
        "Test error",
        null,
        null);

    assertEquals(ErrorCode.INTERNAL, exception.getErrorCode());
    assertEquals("Test error", exception.getMessage());
    assertNull(exception.getHttpResponse());
    assertNull(exception.getCause());
  }

  @Test
  public void testFirebaseExceptionWithResponse() throws IOException {
    HttpResponseException httpError = createHttpResponseException();
    OutgoingHttpRequest request = new OutgoingHttpRequest(
        "GET", "https://firebase.google.com");
    IncomingHttpResponse response = new IncomingHttpResponse(httpError, request);

    FirebaseException exception = new FirebaseException(
        ErrorCode.INTERNAL,
        "Test error",
        null,
        response);

    assertEquals(ErrorCode.INTERNAL, exception.getErrorCode());
    assertEquals("Test error", exception.getMessage());
    assertSame(response, exception.getHttpResponse());
    assertNull(exception.getCause());
  }

  @Test
  public void testFirebaseExceptionWithCause() {
    Exception cause = new Exception("root cause");

    FirebaseException exception = new FirebaseException(
        ErrorCode.INTERNAL,
        "Test error",
        cause);

    assertEquals(ErrorCode.INTERNAL, exception.getErrorCode());
    assertEquals("Test error", exception.getMessage());
    assertNull(exception.getHttpResponse());
    assertSame(cause, exception.getCause());
  }

  private HttpResponseException createHttpResponseException() throws IOException {
    MockLowLevelHttpResponse lowLevelResponse = new MockLowLevelHttpResponse()
        .setStatusCode(HttpStatusCodes.STATUS_CODE_SERVER_ERROR)
        .setContent("{}");
    MockLowLevelHttpRequest lowLevelRequest = new MockLowLevelHttpRequest()
        .setResponse(lowLevelResponse);
    HttpRequest request = TestUtils.createRequest(lowLevelRequest);
    try {
      request.execute();
      throw new IOException("HttpResponseException not thrown");
    } catch (HttpResponseException e) {
      return e;
    }
  }
}
