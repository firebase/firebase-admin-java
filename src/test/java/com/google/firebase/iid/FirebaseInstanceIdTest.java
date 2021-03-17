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

package com.google.firebase.iid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.OutgoingHttpRequest;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.testing.GenericFunction;
import com.google.firebase.testing.TestResponseInterceptor;
import com.google.firebase.testing.TestUtils;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Test;

public class FirebaseInstanceIdTest {

  private static final FirebaseOptions APP_OPTIONS = FirebaseOptions.builder()
      .setCredentials(new MockGoogleCredentials("test-token"))
      .setProjectId("test-project")
      .build();

  private static final Map<Integer, String> ERROR_MESSAGES = ImmutableMap.of(
      404, "Instance ID \"test-iid\": Failed to find the instance ID.",
      409, "Instance ID \"test-iid\": Already deleted.",
      429, "Instance ID \"test-iid\": Request throttled out by the backend server.",
      500, "Instance ID \"test-iid\": Internal server error.",
      501, "Unexpected HTTP response with status: 501\ntest error"
  );

  private static final Map<Integer, ErrorCode> ERROR_CODES = ImmutableMap.of(
      404, ErrorCode.NOT_FOUND,
      409, ErrorCode.CONFLICT,
      429, ErrorCode.RESOURCE_EXHAUSTED,
      500, ErrorCode.INTERNAL,
      501, ErrorCode.UNKNOWN
  );

  private static final String TEST_URL =
      "https://console.firebase.google.com/v1/project/test-project/instanceId/test-iid";

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testNoProjectId() {
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .build();
    FirebaseApp.initializeApp(options);
    try {
      FirebaseInstanceId.getInstance();
      fail("No error thrown for missing project ID");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testInvokeAfterAppDelete() {
    FirebaseApp app = FirebaseApp.initializeApp(APP_OPTIONS, "testInvokeAfterAppDelete");
    FirebaseInstanceId instanceId = FirebaseInstanceId.getInstance(app);
    assertNotNull(instanceId);
    app.delete();

    try {
      FirebaseInstanceId.getInstance(app);
      fail("No error thrown when invoking instanceId after deleting app");
    } catch (IllegalStateException ex) {
      String message = "FirebaseApp 'testInvokeAfterAppDelete' was deleted";
      assertEquals(message, ex.getMessage());
    }
  }

  @Test
  public void testInvalidInstanceId() {
    FirebaseApp.initializeApp(APP_OPTIONS);

    FirebaseInstanceId instanceId = FirebaseInstanceId.getInstance();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    instanceId.setInterceptor(interceptor);
    try {
      instanceId.deleteInstanceIdAsync(null);
      fail("No error thrown for null instance ID");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      instanceId.deleteInstanceIdAsync("");
      fail("No error thrown for empty instance ID");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    assertNull(interceptor.getResponse());
  }

  @Test
  public void testDeleteInstanceId() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse().setContent("{}");
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseOptions options = APP_OPTIONS.toBuilder()
        .setHttpTransport(transport)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    final FirebaseInstanceId instanceId = FirebaseInstanceId.getInstance();
    assertSame(instanceId, FirebaseInstanceId.getInstance(app));

    List<GenericFunction<Void>> functions = ImmutableList.of(
        new GenericFunction<Void>() {
          @Override
          public Void call(Object... args) throws Exception {
            instanceId.deleteInstanceIdAsync("test-iid").get();
            return null;
          }
        },
        new GenericFunction<Void>() {
          @Override
          public Void call(Object... args) throws Exception {
            instanceId.deleteInstanceId("test-iid");
            return null;
          }
        }
    );

    for (GenericFunction<Void> fn : functions) {
      TestResponseInterceptor interceptor = new TestResponseInterceptor();
      instanceId.setInterceptor(interceptor);
      fn.call();

      assertNotNull(interceptor.getResponse());
      HttpRequest request = interceptor.getResponse().getRequest();
      assertEquals(HttpMethods.DELETE, request.getRequestMethod());
      assertEquals(TEST_URL, request.getUrl().toString());
      assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
    }
  }

  @Test
  public void testDeleteInstanceIdError() throws Exception {
    final MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseOptions options = APP_OPTIONS.toBuilder()
        .setHttpTransport(transport)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    // Disable retries by passing a regular HttpRequestFactory.
    FirebaseInstanceId instanceId = new FirebaseInstanceId(app, transport.createRequestFactory());
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    instanceId.setInterceptor(interceptor);

    try {
      for (int statusCode : ERROR_CODES.keySet()) {
        response.setStatusCode(statusCode).setContent("test error");

        try {
          instanceId.deleteInstanceIdAsync("test-iid").get();
          fail("No error thrown for HTTP error");
        } catch (ExecutionException e) {
          assertTrue(e.getCause() instanceof FirebaseInstanceIdException);
          checkFirebaseInstanceIdException((FirebaseInstanceIdException) e.getCause(), statusCode);
        }

        assertNotNull(interceptor.getResponse());
        HttpRequest request = interceptor.getResponse().getRequest();
        assertEquals(HttpMethods.DELETE, request.getRequestMethod());
        assertEquals(TEST_URL, request.getUrl().toString());
      }
    } finally {
      app.delete();
    }
  }

  @Test
  public void testDeleteInstanceIdTransportError() throws Exception {
    HttpTransport transport = TestUtils.createFaultyHttpTransport();
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .setHttpTransport(transport)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);
    // Disable retries by passing a regular HttpRequestFactory.
    FirebaseInstanceId instanceId = new FirebaseInstanceId(app, transport.createRequestFactory());

    try {
      instanceId.deleteInstanceIdAsync("test-iid").get();
      fail("No error thrown for HTTP error");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseInstanceIdException);
      FirebaseInstanceIdException error = (FirebaseInstanceIdException) e.getCause();
      assertEquals(ErrorCode.UNKNOWN, error.getErrorCode());
      assertEquals(
          "Unknown error while making a remote service call: transport error",
          error.getMessage());
      assertTrue(error.getCause() instanceof IOException);
      assertNull(error.getHttpResponse());
    }
  }

  @Test
  public void testDeleteInstanceIdInvalidJsonIgnored() throws Exception {
    final MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .setHttpTransport(transport)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    // Disable retries by passing a regular HttpRequestFactory.
    FirebaseInstanceId instanceId = new FirebaseInstanceId(app, transport.createRequestFactory());
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    instanceId.setInterceptor(interceptor);
    response.setContent("not json");

    instanceId.deleteInstanceIdAsync("test-iid").get();

    assertNotNull(interceptor.getResponse());
  }

  private void checkFirebaseInstanceIdException(FirebaseInstanceIdException error, int statusCode) {
    assertEquals(ERROR_CODES.get(statusCode), error.getErrorCode());
    assertEquals(ERROR_MESSAGES.get(statusCode), error.getMessage());
    assertTrue(error.getCause() instanceof HttpResponseException);

    IncomingHttpResponse httpResponse = error.getHttpResponse();
    assertNotNull(httpResponse);
    assertEquals(statusCode, httpResponse.getStatusCode());
    OutgoingHttpRequest request = httpResponse.getRequest();
    assertEquals(HttpMethods.DELETE, request.getMethod());
    assertEquals(TEST_URL, request.getUrl());
  }
}
