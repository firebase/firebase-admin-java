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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.util.GenericData;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseHttpResponse;
import java.io.IOException;
import org.junit.Test;

public class PlatformErrorHandlerTest {

  @Test
  public void testPlatformError() {
    String payload = "{\"error\": {\"status\": \"UNAVAILABLE\", \"message\": \"Test error\"}}";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(HttpStatusCodes.STATUS_CODE_SERVER_ERROR)
        .setContent(payload);
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    ErrorHandlingHttpClient<FirebaseException> client = new ErrorHandlingHttpClient<>(
        transport.createRequestFactory(),
        Utils.getDefaultJsonFactory(),
        new TestPlatformErrorHandler());

    HttpRequestInfo requestInfo = HttpRequestInfo.buildGetRequest("https://firebase.google.com");
    try {
      client.sendAndParse(requestInfo, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNAVAILABLE, e.getCode());
      assertEquals("Test error", e.getMessage());
      FirebaseHttpResponse httpResponse = e.getHttpResponse();
      assertNotNull(httpResponse);
      assertEquals(HttpStatusCodes.STATUS_CODE_SERVER_ERROR, httpResponse.getStatusCode());
      assertEquals(payload, httpResponse.getContent());
      assertEquals("GET", httpResponse.getRequest().getMethod());
      assertNotNull(e.getCause());
    }
  }

  @Test
  public void testNonJsonError() {
    String payload = "not json";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(HttpStatusCodes.STATUS_CODE_SERVER_ERROR)
        .setContent(payload);
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    ErrorHandlingHttpClient<FirebaseException> client = new ErrorHandlingHttpClient<>(
        transport.createRequestFactory(),
        Utils.getDefaultJsonFactory(),
        new TestPlatformErrorHandler());

    HttpRequestInfo requestInfo = HttpRequestInfo.buildGetRequest("https://firebase.google.com");
    try {
      client.sendAndParse(requestInfo, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.INTERNAL, e.getCode());
      assertEquals("Unexpected HTTP response with status: 500\nnot json", e.getMessage());
      FirebaseHttpResponse httpResponse = e.getHttpResponse();
      assertNotNull(httpResponse);
      assertEquals(HttpStatusCodes.STATUS_CODE_SERVER_ERROR, httpResponse.getStatusCode());
      assertEquals(payload, httpResponse.getContent());
      assertEquals("GET", httpResponse.getRequest().getMethod());
      assertNotNull(e.getCause());
    }
  }

  @Test
  public void testPlatformErrorWithoutCode() {
    String payload = "{\"error\": {\"message\": \"Test error\"}}";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(HttpStatusCodes.STATUS_CODE_SERVER_ERROR)
        .setContent(payload);
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    ErrorHandlingHttpClient<FirebaseException> client = new ErrorHandlingHttpClient<>(
        transport.createRequestFactory(),
        Utils.getDefaultJsonFactory(),
        new TestPlatformErrorHandler());

    HttpRequestInfo requestInfo = HttpRequestInfo.buildGetRequest("https://firebase.google.com");
    try {
      client.sendAndParse(requestInfo, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.INTERNAL, e.getCode());
      assertEquals("Test error", e.getMessage());
      FirebaseHttpResponse httpResponse = e.getHttpResponse();
      assertNotNull(httpResponse);
      assertEquals(HttpStatusCodes.STATUS_CODE_SERVER_ERROR, httpResponse.getStatusCode());
      assertEquals(payload, httpResponse.getContent());
      assertEquals("GET", httpResponse.getRequest().getMethod());
      assertNotNull(e.getCause());
    }
  }

  @Test
  public void testPlatformErrorWithoutMessage() {
    String payload = "{\"error\": {\"status\": \"INVALID_ARGUMENT\"}}";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(HttpStatusCodes.STATUS_CODE_SERVER_ERROR)
        .setContent(payload);
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    ErrorHandlingHttpClient<FirebaseException> client = new ErrorHandlingHttpClient<>(
        transport.createRequestFactory(),
        Utils.getDefaultJsonFactory(),
        new TestPlatformErrorHandler());

    HttpRequestInfo requestInfo = HttpRequestInfo.buildGetRequest("https://firebase.google.com");
    try {
      client.sendAndParse(requestInfo, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.INVALID_ARGUMENT, e.getCode());
      assertEquals("Unexpected HTTP response with status: 500\n" + payload, e.getMessage());
      FirebaseHttpResponse httpResponse = e.getHttpResponse();
      assertNotNull(httpResponse);
      assertEquals(HttpStatusCodes.STATUS_CODE_SERVER_ERROR, httpResponse.getStatusCode());
      assertEquals(payload, httpResponse.getContent());
      assertEquals("GET", httpResponse.getRequest().getMethod());
      assertNotNull(e.getCause());
    }
  }

  @Test
  public void testPlatformErrorWithoutCodeOrMessage() {
    String payload = "{}";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(HttpStatusCodes.STATUS_CODE_SERVER_ERROR)
        .setContent(payload);
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    ErrorHandlingHttpClient<FirebaseException> client = new ErrorHandlingHttpClient<>(
        transport.createRequestFactory(),
        Utils.getDefaultJsonFactory(),
        new TestPlatformErrorHandler());

    HttpRequestInfo requestInfo = HttpRequestInfo.buildGetRequest("https://firebase.google.com");
    try {
      client.sendAndParse(requestInfo, GenericData.class);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.INTERNAL, e.getCode());
      assertEquals("Unexpected HTTP response with status: 500\n" + payload, e.getMessage());
      FirebaseHttpResponse httpResponse = e.getHttpResponse();
      assertNotNull(httpResponse);
      assertEquals(HttpStatusCodes.STATUS_CODE_SERVER_ERROR, httpResponse.getStatusCode());
      assertEquals(payload, httpResponse.getContent());
      assertEquals("GET", httpResponse.getRequest().getMethod());
      assertNotNull(e.getCause());
    }
  }

  private static class TestPlatformErrorHandler extends PlatformErrorHandler<FirebaseException> {

    TestPlatformErrorHandler() {
      super(Utils.getDefaultJsonFactory());
    }

    @Override
    protected FirebaseException createException(ErrorParams params) {
      return new FirebaseException(params.getErrorCode(), params.getMessage(),
          params.getResponse(), params.getException());
    }

    @Override
    public FirebaseException handleIOException(IOException e) {
      return new FirebaseException(ErrorCode.UNKNOWN, "IO error", null, e);
    }

    @Override
    public FirebaseException handleParseException(IOException e, FirebaseHttpResponse response) {
      return new FirebaseException(ErrorCode.UNKNOWN, "Parse error", response, e);
    }
  }
}
