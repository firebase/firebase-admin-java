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

package com.google.firebase.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonParser;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableList;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.testing.TestResponseInterceptor;
import com.google.firebase.testing.TestUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;

public class InstanceIdClientImplTest {

  private static final String TEST_IID_SUBSCRIBE_URL =
      "https://iid.googleapis.com/iid/v1:batchAdd";

  private static final String TEST_IID_UNSUBSCRIBE_URL =
      "https://iid.googleapis.com/iid/v1:batchRemove";

  private static final List<Integer> HTTP_ERRORS = ImmutableList.of(401, 404, 500);

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testSubscribe() throws Exception {
    final String responseString = "{\"results\": [{}, {\"error\": \"error_reason\"}]}";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setContent(responseString);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    final InstanceIdClient client = initInstanceIdClient(response, interceptor);

    TopicManagementResponse result = client.subscribeToTopic(
        "test-topic", ImmutableList.of("id1", "id2"));

    checkTopicManagementRequestHeader(
        interceptor.getLastRequest(), TEST_IID_SUBSCRIBE_URL);
    checkTopicManagementRequest(interceptor.getLastRequest(), result);
  }

  @Test
  public void testSubscribeWithPrefixedTopic() throws Exception {
    final String responseString = "{\"results\": [{}, {\"error\": \"error_reason\"}]}";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setContent(responseString);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    final InstanceIdClient client = initInstanceIdClient(response, interceptor);

    TopicManagementResponse result = client.subscribeToTopic(
        "/topics/test-topic", ImmutableList.of("id1", "id2"));

    checkTopicManagementRequestHeader(
        interceptor.getLastRequest(), TEST_IID_SUBSCRIBE_URL);
    checkTopicManagementRequest(interceptor.getLastRequest(), result);
  }

  @Test
  public void testSubscribeError() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    InstanceIdClient client = initInstanceIdClient(response, interceptor);

    for (int statusCode : HTTP_ERRORS) {
      response.setStatusCode(statusCode).setContent("{\"error\": \"test error\"}");

      try {
        client.subscribeToTopic("test-topic", ImmutableList.of("id1", "id2"));
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        checkExceptionFromHttpResponse(error, statusCode, "test error");
      }

      checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_SUBSCRIBE_URL);
    }
  }

  @Test
  public void testSubscribeEmptyPayloadError() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(500).setContent("{}");
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    InstanceIdClient client = initInstanceIdClient(response, interceptor);

    try {
      client.subscribeToTopic("test-topic", ImmutableList.of("id1", "id2"));
      fail("No error thrown for HTTP error");
    } catch (FirebaseMessagingException error) {
      checkExceptionFromHttpResponse(error, 500,
          "Unexpected HTTP response with status: 500; body: {}");
    }

    checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_SUBSCRIBE_URL);
  }

  @Test
  public void testSubscribeMalformedError() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(500).setContent("not json");
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    InstanceIdClient client = initInstanceIdClient(response, interceptor);

    try {
      client.subscribeToTopic("test-topic", ImmutableList.of("id1", "id2"));
      fail("No error thrown for HTTP error");
    } catch (FirebaseMessagingException error) {
      checkExceptionFromHttpResponse(error, 500,
          "Unexpected HTTP response with status: 500; body: not json");
    }

    checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_SUBSCRIBE_URL);
  }

  @Test
  public void testSubscribeZeroContentError() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(500).setZeroContent();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    InstanceIdClient client = initInstanceIdClient(response, interceptor);

    try {
      client.subscribeToTopic("test-topic", ImmutableList.of("id1", "id2"));
      fail("No error thrown for HTTP error");
    } catch (FirebaseMessagingException error) {
      checkExceptionFromHttpResponse(error, 500,
          "Unexpected HTTP response with status: 500; body: null");
    }

    checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_SUBSCRIBE_URL);
  }

  @Test
  public void testSubscribeTransportError() {
    InstanceIdClient client = initClientWithFaultyTransport();

    try {
      client.subscribeToTopic("test-topic", ImmutableList.of("id1", "id2"));
      fail("No error thrown for HTTP error");
    } catch (FirebaseMessagingException error) {
      assertEquals("internal-error", error.getErrorCode());
      assertEquals("Error while calling IID backend service", error.getMessage());
      assertTrue(error.getCause() instanceof IOException);
    }
  }

  @Test
  public void testUnsubscribe() throws Exception {
    final String responseString = "{\"results\": [{}, {\"error\": \"error_reason\"}]}";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setContent(responseString);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    final InstanceIdClient client = initInstanceIdClient(response, interceptor);

    TopicManagementResponse result = client.unsubscribeFromTopic(
        "test-topic", ImmutableList.of("id1", "id2"));

    checkTopicManagementRequestHeader(
        interceptor.getLastRequest(), TEST_IID_UNSUBSCRIBE_URL);
    checkTopicManagementRequest(interceptor.getLastRequest(), result);
  }

  @Test
  public void testUnsubscribeWithPrefixedTopic() throws Exception {
    final String responseString = "{\"results\": [{}, {\"error\": \"error_reason\"}]}";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setContent(responseString);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    final InstanceIdClient client = initInstanceIdClient(response, interceptor);

    TopicManagementResponse result = client.unsubscribeFromTopic(
        "/topics/test-topic", ImmutableList.of("id1", "id2"));

    checkTopicManagementRequestHeader(
        interceptor.getLastRequest(), TEST_IID_UNSUBSCRIBE_URL);
    checkTopicManagementRequest(interceptor.getLastRequest(), result);
  }

  @Test
  public void testUnsubscribeError() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    InstanceIdClient client = initInstanceIdClient(response, interceptor);

    for (int statusCode : HTTP_ERRORS) {
      response.setStatusCode(statusCode).setContent("{\"error\": \"test error\"}");

      try {
        client.unsubscribeFromTopic("test-topic", ImmutableList.of("id1", "id2"));
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        checkExceptionFromHttpResponse(error, statusCode, "test error");
      }

      checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_UNSUBSCRIBE_URL);
    }
  }

  @Test
  public void testUnsubscribeEmptyPayloadError() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(500).setContent("{}");
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    InstanceIdClient client = initInstanceIdClient(response, interceptor);

    try {
      client.unsubscribeFromTopic("test-topic", ImmutableList.of("id1", "id2"));
      fail("No error thrown for HTTP error");
    } catch (FirebaseMessagingException error) {
      checkExceptionFromHttpResponse(error, 500,
          "Unexpected HTTP response with status: 500; body: {}");
    }

    checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_UNSUBSCRIBE_URL);
  }

  @Test
  public void testUnsubscribeMalformedError() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(500).setContent("not json");
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    InstanceIdClient client = initInstanceIdClient(response, interceptor);

    try {
      client.unsubscribeFromTopic("test-topic", ImmutableList.of("id1", "id2"));
      fail("No error thrown for HTTP error");
    } catch (FirebaseMessagingException error) {
      checkExceptionFromHttpResponse(error, 500,
          "Unexpected HTTP response with status: 500; body: not json");
    }

    checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_UNSUBSCRIBE_URL);
  }

  @Test
  public void testUnsubscribeZeroContentError() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(500).setZeroContent();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    InstanceIdClient client = initInstanceIdClient(response, interceptor);

    try {
      client.unsubscribeFromTopic("test-topic", ImmutableList.of("id1", "id2"));
      fail("No error thrown for HTTP error");
    } catch (FirebaseMessagingException error) {
      checkExceptionFromHttpResponse(error, 500,
          "Unexpected HTTP response with status: 500; body: null");
    }

    checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_UNSUBSCRIBE_URL);
  }

  @Test
  public void testUnsubscribeTransportError() {
    InstanceIdClient client = initClientWithFaultyTransport();

    try {
      client.unsubscribeFromTopic("test-topic", ImmutableList.of("id1", "id2"));
      fail("No error thrown for HTTP error");
    } catch (FirebaseMessagingException error) {
      assertEquals("internal-error", error.getErrorCode());
      assertEquals("Error while calling IID backend service", error.getMessage());
      assertTrue(error.getCause() instanceof IOException);
    }
  }

  @Test(expected = NullPointerException.class)
  public void testRequestFactoryIsNull() {
    new InstanceIdClientImpl(null, Utils.getDefaultJsonFactory());
  }

  @Test(expected = NullPointerException.class)
  public void testJsonFactoryIsNull() {
    new InstanceIdClientImpl(Utils.getDefaultTransport().createRequestFactory(), null);
  }

  @Test
  public void testFromApp() throws IOException {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    try {
      InstanceIdClientImpl client = InstanceIdClientImpl.fromApp(app);

      assertSame(options.getJsonFactory(), client.getJsonFactory());
      HttpRequest request = client.getRequestFactory().buildGetRequest(
          new GenericUrl("https://example.com"));
      assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
    } finally {
      app.delete();
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testTopicManagementResponseWithNullList() {
    new TopicManagementResponse(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testTopicManagementResponseWithEmptyList() {
    new TopicManagementResponse(ImmutableList.<GenericJson>of());
  }

  @Test
  public void testTopicManagementResponseErrorToString() {
    ImmutableList<GenericJson> jsonList = ImmutableList.of(new GenericJson().set("error", "test error"));
    TopicManagementResponse topicManagementResponse = new TopicManagementResponse(jsonList);
    assertEquals("[Error{index=0, reason=unknown-error}]", topicManagementResponse.getErrors().toString());
  }

  private static InstanceIdClientImpl initInstanceIdClient(
      final MockLowLevelHttpResponse mockResponse,
      final HttpResponseInterceptor interceptor) {

    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(mockResponse)
        .build();
    return new InstanceIdClientImpl(
        transport.createRequestFactory(),
        Utils.getDefaultJsonFactory(),
        interceptor);
  }

  private void checkTopicManagementRequest(
      HttpRequest request, TopicManagementResponse result) throws IOException {
    assertEquals(1, result.getSuccessCount());
    assertEquals(1, result.getFailureCount());
    assertEquals(1, result.getErrors().size());
    assertEquals(1, result.getErrors().get(0).getIndex());
    assertEquals("unknown-error", result.getErrors().get(0).getReason());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    request.getContent().writeTo(out);
    Map<String, Object> parsed = new HashMap<>();
    JsonParser parser = Utils.getDefaultJsonFactory().createJsonParser(out.toString());
    parser.parseAndClose(parsed);
    assertEquals(2, parsed.size());
    assertEquals("/topics/test-topic", parsed.get("to"));
    assertEquals(ImmutableList.of("id1", "id2"), parsed.get("registration_tokens"));
  }

  private void checkTopicManagementRequestHeader(
      HttpRequest request, String expectedUrl) {
    assertEquals("POST", request.getRequestMethod());
    assertEquals(expectedUrl, request.getUrl().toString());
  }

  private void checkExceptionFromHttpResponse(FirebaseMessagingException error,
      int expectedCode, String expectedMessage) {
    assertEquals(getTopicManagementErrorCode(expectedCode), error.getErrorCode());
    assertEquals(expectedMessage, error.getMessage());
    assertTrue(error.getCause() instanceof HttpResponseException);
  }

  private InstanceIdClient initClientWithFaultyTransport() {
    return new InstanceIdClientImpl(
        TestUtils.createFaultyHttpTransport().createRequestFactory(),
        Utils.getDefaultJsonFactory());
  }

  private String getTopicManagementErrorCode(int statusCode) {
    String code = InstanceIdClientImpl.IID_ERROR_CODES.get(statusCode);
    if (code == null) {
      code = "unknown-error";
    }
    return code;
  }
}
