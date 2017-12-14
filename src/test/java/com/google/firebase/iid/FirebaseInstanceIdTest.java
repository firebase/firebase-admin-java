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

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.testing.TestResponseInterceptor;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Test;

public class FirebaseInstanceIdTest {

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testNoProjectId() {
    FirebaseOptions options = new FirebaseOptions.Builder()
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
  public void testInvalidInstanceId() throws Exception {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .build();
    FirebaseApp.initializeApp(options);

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
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .setHttpTransport(transport)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    FirebaseInstanceId instanceId = FirebaseInstanceId.getInstance();
    assertSame(instanceId, FirebaseInstanceId.getInstance(app));

    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    instanceId.setInterceptor(interceptor);
    instanceId.deleteInstanceIdAsync("test-iid").get();

    assertNotNull(interceptor.getResponse());
    HttpRequest request = interceptor.getResponse().getRequest();
    assertEquals("DELETE", request.getRequestMethod());
    String url = "https://console.firebase.google.com/v1/project/test-project/instanceId/test-iid";
    assertEquals(url, request.getUrl().toString());
    assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
  }

  @Test
  public void testDeleteInstanceIdError() throws Exception {
    Map<Integer, String> errors = ImmutableMap.of(
        404, "Instance ID \"test-iid\": Failed to find the instance ID.",
        429, "Instance ID \"test-iid\": Request throttled out by the backend server.",
        500, "Instance ID \"test-iid\": Internal server error.",
        501, "Error while invoking instance ID service."
    );

    String url = "https://console.firebase.google.com/v1/project/test-project/instanceId/test-iid";
    for (Map.Entry<Integer, String> entry : errors.entrySet()) {
      MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
          .setStatusCode(entry.getKey())
          .setContent("test error");
      MockHttpTransport transport = new MockHttpTransport.Builder()
          .setLowLevelHttpResponse(response)
          .build();
      FirebaseOptions options = new FirebaseOptions.Builder()
          .setCredentials(new MockGoogleCredentials("test-token"))
          .setProjectId("test-project")
          .setHttpTransport(transport)
          .build();
      final FirebaseApp app = FirebaseApp.initializeApp(options);

      FirebaseInstanceId instanceId = FirebaseInstanceId.getInstance();
      TestResponseInterceptor interceptor = new TestResponseInterceptor();
      instanceId.setInterceptor(interceptor);
      try {
        instanceId.deleteInstanceIdAsync("test-iid").get();
        fail("No error thrown for HTTP error");
      } catch (ExecutionException e) {
        assertTrue(e.getCause() instanceof FirebaseInstanceIdException);
        assertEquals(entry.getValue(), e.getCause().getMessage());
        assertTrue(e.getCause().getCause() instanceof HttpResponseException);
      }

      assertNotNull(interceptor.getResponse());
      HttpRequest request = interceptor.getResponse().getRequest();
      assertEquals("DELETE", request.getRequestMethod());
      assertEquals(url, request.getUrl().toString());
      assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
      app.delete();
    }
  }
}
