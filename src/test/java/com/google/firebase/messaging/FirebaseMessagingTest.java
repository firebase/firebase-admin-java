/*
 * Copyright 2018 Google Inc.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.JsonParser;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.messaging.WebpushNotification.Action;
import com.google.firebase.messaging.WebpushNotification.Direction;
import com.google.firebase.testing.GenericFunction;
import com.google.firebase.testing.TestResponseInterceptor;
import com.google.firebase.testing.TestUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;

public class FirebaseMessagingTest {

  private static final String TEST_FCM_URL =
      "https://fcm.googleapis.com/v1/projects/test-project/messages:send";

  private static final String TEST_IID_SUBSCRIBE_URL =
      "https://iid.googleapis.com/iid/v1:batchAdd";

  private static final String TEST_IID_UNSUBSCRIBE_URL =
      "https://iid.googleapis.com/iid/v1:batchRemove";

  private static final List<Integer> HTTP_ERRORS = ImmutableList.of(401, 404, 500);

  private static final String MOCK_RESPONSE = "{\"name\": \"mock-name\"}";

  private static final String MOCK_BATCH_SUCCESS_RESPONSE = TestUtils.loadResource(
      "fcm_batch_success.txt");

  private static final String MOCK_BATCH_FAILURE_RESPONSE = TestUtils.loadResource(
      "fcm_batch_failure.txt");

  private static final ImmutableList.Builder<String> TOO_MANY_IDS = ImmutableList.builder();

  static {
    for (int i = 0; i < 1001; i++) {
      TOO_MANY_IDS.add("id" + i);
    }
  }

  private static final List<TopicMgtArgs> INVALID_TOPIC_MGT_ARGS = ImmutableList.of(
      new TopicMgtArgs(null, null),
      new TopicMgtArgs(null, "test-topic"),
      new TopicMgtArgs(ImmutableList.<String>of(), "test-topic"),
      new TopicMgtArgs(ImmutableList.of(""), "test-topic"),
      new TopicMgtArgs(TOO_MANY_IDS.build(), "test-topic"),
      new TopicMgtArgs(ImmutableList.of(""), null),
      new TopicMgtArgs(ImmutableList.of("id"), ""),
      new TopicMgtArgs(ImmutableList.of("id"), "foo*")
  );

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGetInstance() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .build();
    FirebaseApp.initializeApp(options);

    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    assertSame(messaging, FirebaseMessaging.getInstance());
  }

  @Test
  public void testGetInstanceByApp() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "custom-app");

    FirebaseMessaging messaging = FirebaseMessaging.getInstance(app);
    assertSame(messaging, FirebaseMessaging.getInstance(app));
  }

  @Test
  public void testPostDeleteApp() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "custom-app");
    app.delete();
    try {
      FirebaseMessaging.getInstance(app);
      fail("No error thrown for deleted app");
    } catch (IllegalStateException expected) {
      // expected
    }
  }

  @Test
  public void testNoProjectId() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .build();
    FirebaseApp.initializeApp(options);
    try {
      FirebaseMessaging.getInstance();
      fail("No error thrown for missing project ID");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testSendNullMessage() {
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initDefaultMessaging(interceptor);
    try {
      messaging.sendAsync(null);
      fail("No error thrown for null message");
    } catch (NullPointerException expected) {
      // expected
    }

    assertNull(interceptor.getResponse());
  }

  @Test
  public void testSend() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setContent(MOCK_RESPONSE);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    final FirebaseMessaging messaging = initMessaging(response, interceptor);
    Map<Message, Map<String, Object>> testMessages = buildTestMessages();

    List<GenericFunction<String>> functions = ImmutableList.of(
        new GenericFunction<String>() {
          @Override
          public String call(Object... args) throws Exception {
            return messaging.sendAsync((Message) args[0]).get();
          }
        },
        new GenericFunction<String>() {
          @Override
          public String call(Object... args) throws Exception {
            return messaging.send((Message) args[0]);
          }
        }
    );
    for (GenericFunction<String> fn : functions) {
      for (Map.Entry<Message, Map<String, Object>> entry : testMessages.entrySet()) {
        response.setContent(MOCK_RESPONSE);
        String resp = fn.call(entry.getKey());
        assertEquals("mock-name", resp);

        checkRequestHeader(interceptor.getLastRequest());
        checkRequest(interceptor.getLastRequest(),
            ImmutableMap.<String, Object>of("message", entry.getValue()));
      }
    }
  }

  @Test
  public void testSendDryRun() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setContent(MOCK_RESPONSE);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    final FirebaseMessaging messaging = initMessaging(response, interceptor);
    Map<Message, Map<String, Object>> testMessages = buildTestMessages();

    List<GenericFunction<String>> functions = ImmutableList.of(
        new GenericFunction<String>() {
          @Override
          public String call(Object... args) throws Exception {
            return messaging.sendAsync((Message) args[0], true).get();
          }
        },
        new GenericFunction<String>() {
          @Override
          public String call(Object... args) throws Exception {
            return messaging.send((Message) args[0], true);
          }
        }
    );

    for (GenericFunction<String> fn : functions) {
      for (Map.Entry<Message, Map<String, Object>> entry : testMessages.entrySet()) {
        response.setContent(MOCK_RESPONSE);
        String resp = fn.call(entry.getKey());
        assertEquals("mock-name", resp);

        checkRequestHeader(interceptor.getLastRequest());
        checkRequest(interceptor.getLastRequest(),
            ImmutableMap.of("message", entry.getValue(), "validate_only", true));
      }
    }
  }

  @Test
  public void testSendError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initMessaging(response, interceptor);
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent("{}");
      try {
        messaging.sendAsync(Message.builder().setTopic("test-topic").build()).get();
        fail("No error thrown for HTTP error");
      } catch (ExecutionException e) {
        assertTrue(e.getCause() instanceof FirebaseMessagingException);
        FirebaseMessagingException error = (FirebaseMessagingException) e.getCause();
        assertEquals("unknown-error", error.getErrorCode());
        assertEquals("Unexpected HTTP response with status: " + code + "; body: {}",
            error.getMessage());
        assertTrue(error.getCause() instanceof HttpResponseException);
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendErrorWithZeroContentResponse() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initMessaging(response, interceptor);
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setZeroContent();
      try {
        messaging.sendAsync(Message.builder().setTopic("test-topic").build()).get();
        fail("No error thrown for HTTP error");
      } catch (ExecutionException e) {
        assertTrue(e.getCause() instanceof FirebaseMessagingException);
        FirebaseMessagingException error = (FirebaseMessagingException) e.getCause();
        assertEquals("unknown-error", error.getErrorCode());
        assertEquals("Unexpected HTTP response with status: " + code + "; body: null",
            error.getMessage());
        assertTrue(error.getCause() instanceof HttpResponseException);
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendErrorWithDetails() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initMessaging(response, interceptor);
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\"}}");
      try {
        messaging.sendAsync(Message.builder().setTopic("test-topic").build()).get();
        fail("No error thrown for HTTP error");
      } catch (ExecutionException e) {
        assertTrue(e.getCause() instanceof FirebaseMessagingException);
        FirebaseMessagingException error = (FirebaseMessagingException) e.getCause();
        assertEquals("invalid-argument", error.getErrorCode());
        assertEquals("test error", error.getMessage());
        assertTrue(error.getCause() instanceof HttpResponseException);
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendErrorWithCanonicalCode() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initMessaging(response, interceptor);
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"NOT_FOUND\", \"message\": \"test error\"}}");
      try {
        messaging.sendAsync(Message.builder().setTopic("test-topic").build()).get();
        fail("No error thrown for HTTP error");
      } catch (ExecutionException e) {
        assertTrue(e.getCause() instanceof FirebaseMessagingException);
        FirebaseMessagingException error = (FirebaseMessagingException) e.getCause();
        assertEquals("registration-token-not-registered", error.getErrorCode());
        assertEquals("test error", error.getMessage());
        assertTrue(error.getCause() instanceof HttpResponseException);
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendErrorWithFcmError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initMessaging(response, interceptor);
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\", "
              + "\"details\":[{\"@type\": \"type.googleapis.com/google.firebase.fcm"
              + ".v1.FcmError\", \"errorCode\": \"UNREGISTERED\"}]}}");
      try {
        messaging.sendAsync(Message.builder().setTopic("test-topic").build()).get();
        fail("No error thrown for HTTP error");
      } catch (ExecutionException e) {
        assertTrue(e.getCause() instanceof FirebaseMessagingException);
        FirebaseMessagingException error = (FirebaseMessagingException) e.getCause();
        assertEquals("registration-token-not-registered", error.getErrorCode());
        assertEquals("test error", error.getMessage());
        assertTrue(error.getCause() instanceof HttpResponseException);
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendBatchWithNullBatchMessage() {
    FirebaseMessaging messaging = initDefaultMessaging();
    try {
      messaging.sendMulticastAsync(null);
      fail("No error thrown for null batch message");
    } catch (NullPointerException expected) {
      // expected
    }
  }

  @Test
  public void testSendBatchWithNullList() {
    FirebaseMessaging messaging = initDefaultMessaging();
    try {
      messaging.sendAllAsync(null);
      fail("No error thrown for null message list");
    } catch (NullPointerException expected) {
      // expected
    }
  }

  @Test
  public void testSendBatchWithEmptyList() {
    FirebaseMessaging messaging = initDefaultMessaging();
    try {
      messaging.sendAllAsync(ImmutableList.<Message>of());
      fail("No error thrown for null message list");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testSendBatchWithTooLargeList() {
    FirebaseMessaging messaging = initDefaultMessaging();
    ImmutableList.Builder<Message> listBuilder = ImmutableList.builder();
    for (int i = 0; i < 1001; i++) {
      listBuilder.add(Message.builder().setTopic("topic").build());
    }
    try {
      messaging.sendAllAsync(listBuilder.build());
      fail("No error thrown for null message list");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testSendBatchSuccessWithList() throws Exception {
    final TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = getMessagingForBatchRequest(
        MOCK_BATCH_SUCCESS_RESPONSE, interceptor);
    List<Message> messages = ImmutableList.of(
        Message.builder().setTopic("topic1").build(),
        Message.builder().setTopic("topic2").build()
    );

    BatchResponse responses = messaging.sendAll(messages);

    assertSendBatchSuccess(responses, interceptor);
  }

  @Test
  public void testSendBatchSuccessWithBatchMessage() throws Exception {
    final TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = getMessagingForBatchRequest(
        MOCK_BATCH_SUCCESS_RESPONSE, interceptor);
    MulticastMessage batch = MulticastMessage.builder()
        .addToken("token1")
        .addToken("token2")
        .build();

    BatchResponse responses = messaging.sendMulticast(batch);

    assertSendBatchSuccess(responses, interceptor);
  }

  @Test
  public void testSendBatchAsyncSuccessWithList() throws Exception {
    final TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = getMessagingForBatchRequest(
        MOCK_BATCH_SUCCESS_RESPONSE, interceptor);
    List<Message> messages = ImmutableList.of(
        Message.builder().setTopic("topic1").build(),
        Message.builder().setTopic("topic2").build()
    );

    BatchResponse responses = messaging.sendAllAsync(messages).get();

    assertSendBatchSuccess(responses, interceptor);
  }

  @Test
  public void testSendBatchAsyncSuccessWithBatchMessage() throws Exception {
    final TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = getMessagingForBatchRequest(
        MOCK_BATCH_SUCCESS_RESPONSE, interceptor);
    MulticastMessage batch = MulticastMessage.builder()
        .addToken("token1")
        .addToken("token2")
        .build();

    BatchResponse responses = messaging.sendMulticastAsync(batch).get();

    assertSendBatchSuccess(responses, interceptor);
  }

  @Test
  public void testSendBatchFailureWithList() throws Exception {
    final TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = getMessagingForBatchRequest(
        MOCK_BATCH_FAILURE_RESPONSE, interceptor);
    List<Message> messages = ImmutableList.of(
        Message.builder().setTopic("topic1").build(),
        Message.builder().setTopic("topic2").build(),
        Message.builder().setTopic("topic3").build()
    );

    BatchResponse responses = messaging.sendAll(messages);

    assertSendBatchFailure(responses, interceptor);
  }

  @Test
  public void testSendBatchFailureWithBatchMessage() throws Exception {
    final TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = getMessagingForBatchRequest(
        MOCK_BATCH_FAILURE_RESPONSE, interceptor);
    MulticastMessage batch = MulticastMessage.builder()
        .addToken("token1")
        .addToken("token2")
        .addToken("token3")
        .build();

    BatchResponse responses = messaging.sendMulticast(batch);

    assertSendBatchFailure(responses, interceptor);
  }

  @Test
  public void testSendBatchAsyncFailureWithList() throws Exception {
    final TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = getMessagingForBatchRequest(
        MOCK_BATCH_FAILURE_RESPONSE, interceptor);
    List<Message> messages = ImmutableList.of(
        Message.builder().setTopic("topic1").build(),
        Message.builder().setTopic("topic2").build(),
        Message.builder().setTopic("topic3").build()
    );

    BatchResponse responses = messaging.sendAllAsync(messages).get();

    assertSendBatchFailure(responses, interceptor);
  }

  @Test
  public void testSendBatchAsyncFailureWithBatchMessage() throws Exception {
    final TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = getMessagingForBatchRequest(
        MOCK_BATCH_FAILURE_RESPONSE, interceptor);
    MulticastMessage batch = MulticastMessage.builder()
        .addToken("token1")
        .addToken("token2")
        .addToken("token3")
        .build();

    BatchResponse responses = messaging.sendMulticastAsync(batch).get();

    assertSendBatchFailure(responses, interceptor);
  }

  @Test
  public void testSendBatchError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initMessaging(response, interceptor);
    List<Message> messages = ImmutableList.of(Message.builder()
        .setTopic("test-topic")
        .build());
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent("{}");
      try {
        messaging.sendAllAsync(messages).get();
        fail("No error thrown for HTTP error");
      } catch (ExecutionException e) {
        assertTrue(e.getCause() instanceof FirebaseMessagingException);
        FirebaseMessagingException error = (FirebaseMessagingException) e.getCause();
        assertEquals("unknown-error", error.getErrorCode());
        assertEquals("Unexpected HTTP response with status: " + code + "; body: {}",
            error.getMessage());
        assertTrue(error.getCause() instanceof HttpResponseException);
      }
      checkBatchRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendBatchTransportError() throws Exception {
    FirebaseMessaging messaging = initFaultyTransportMessaging();
    List<Message> messages = ImmutableList.of(Message.builder()
        .setTopic("test-topic")
        .build());
    try {
      messaging.sendAllAsync(messages).get();
      fail("No error thrown for HTTP error");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseMessagingException);
      FirebaseMessagingException error = (FirebaseMessagingException) e.getCause();
      assertEquals("internal-error", error.getErrorCode());
      assertEquals("Error while calling FCM backend service", error.getMessage());
      assertTrue(error.getCause() instanceof IOException);
    }
  }

  @Test
  public void testSendBatchErrorWithZeroContentResponse() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initMessaging(response, interceptor);
    List<Message> messages = ImmutableList.of(Message.builder()
        .setTopic("test-topic")
        .build());
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setZeroContent();
      try {
        messaging.sendAllAsync(messages).get();
        fail("No error thrown for HTTP error");
      } catch (ExecutionException e) {
        assertTrue(e.getCause() instanceof FirebaseMessagingException);
        FirebaseMessagingException error = (FirebaseMessagingException) e.getCause();
        assertEquals("unknown-error", error.getErrorCode());
        assertEquals("Unexpected HTTP response with status: " + code + "; body: null",
            error.getMessage());
        assertTrue(error.getCause() instanceof HttpResponseException);
      }
      checkBatchRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendBatchErrorWithDetails() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initMessaging(response, interceptor);
    List<Message> messages = ImmutableList.of(Message.builder()
        .setTopic("test-topic")
        .build());
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\"}}");
      try {
        messaging.sendAllAsync(messages).get();
        fail("No error thrown for HTTP error");
      } catch (ExecutionException e) {
        assertTrue(e.getCause() instanceof FirebaseMessagingException);
        FirebaseMessagingException error = (FirebaseMessagingException) e.getCause();
        assertEquals("invalid-argument", error.getErrorCode());
        assertEquals("test error", error.getMessage());
        assertTrue(error.getCause() instanceof HttpResponseException);
      }
      checkBatchRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendBatchErrorWithCanonicalCode() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initMessaging(response, interceptor);
    List<Message> messages = ImmutableList.of(Message.builder()
        .setTopic("test-topic")
        .build());
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"NOT_FOUND\", \"message\": \"test error\"}}");
      try {
        messaging.sendAllAsync(messages).get();
        fail("No error thrown for HTTP error");
      } catch (ExecutionException e) {
        assertTrue(e.getCause() instanceof FirebaseMessagingException);
        FirebaseMessagingException error = (FirebaseMessagingException) e.getCause();
        assertEquals("registration-token-not-registered", error.getErrorCode());
        assertEquals("test error", error.getMessage());
        assertTrue(error.getCause() instanceof HttpResponseException);
      }
      checkBatchRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendBatchErrorWithoutMessage() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initMessaging(response, interceptor);
    List<Message> messages = ImmutableList.of(Message.builder()
        .setTopic("test-topic")
        .build());
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"INVALID_ARGUMENT\", "
              + "\"details\":[{\"@type\": \"type.googleapis.com/google.firebase.fcm"
              + ".v1.FcmError\", \"errorCode\": \"UNREGISTERED\"}]}}");
      try {
        messaging.sendAllAsync(messages).get();
        fail("No error thrown for HTTP error");
      } catch (ExecutionException e) {
        assertTrue(e.getCause() instanceof FirebaseMessagingException);
        FirebaseMessagingException error = (FirebaseMessagingException) e.getCause();
        assertEquals("registration-token-not-registered", error.getErrorCode());
        assertTrue(error.getMessage().startsWith("Unexpected HTTP response"));
        assertTrue(error.getCause() instanceof HttpResponseException);
      }
      checkBatchRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testInvalidSubscribe() {
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initDefaultMessaging(interceptor);

    for (TopicMgtArgs args : INVALID_TOPIC_MGT_ARGS) {
      try {
        messaging.subscribeToTopicAsync(args.registrationTokens, args.topic);
        fail("No error thrown for invalid args");
      } catch (IllegalArgumentException expected) {
        // expected
      }
    }

    assertNull(interceptor.getResponse());
  }

  @Test
  public void testSubscribe() throws Exception {
    final String responseString = "{\"results\": [{}, {\"error\": \"error_reason\"}]}";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    final FirebaseMessaging messaging = initMessaging(response, interceptor);

    List<GenericFunction<TopicManagementResponse>> functions = ImmutableList.of(
        new GenericFunction<TopicManagementResponse>() {
          @Override
          public TopicManagementResponse call(Object... args) throws Exception {
            return messaging.subscribeToTopicAsync(ImmutableList.of("id1", "id2"),
                "test-topic").get();
          }
        },
        new GenericFunction<TopicManagementResponse>() {
          @Override
          public TopicManagementResponse call(Object... args) throws Exception {
            return messaging.subscribeToTopic(ImmutableList.of("id1", "id2"), "test-topic");
          }
        },
        new GenericFunction<TopicManagementResponse>() {
          @Override
          public TopicManagementResponse call(Object... args) throws Exception {
            return messaging.subscribeToTopic(ImmutableList.of("id1", "id2"),
                "/topics/test-topic");
          }
        }
    );

    for (GenericFunction<TopicManagementResponse> fn : functions) {
      response.setContent(responseString);
      TopicManagementResponse result = fn.call();
      checkTopicManagementRequestHeader(
          interceptor.getLastRequest(), TEST_IID_SUBSCRIBE_URL);
      checkTopicManagementRequest(interceptor.getLastRequest(), result);
    }
  }

  @Test
  public void testSubscribeError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initMessaging(response, interceptor);
    for (int statusCode : HTTP_ERRORS) {
      response.setStatusCode(statusCode).setContent("{\"error\": \"test error\"}");
      try {
        messaging.subscribeToTopicAsync(ImmutableList.of("id1", "id2"), "test-topic").get();
        fail("No error thrown for HTTP error");
      } catch (ExecutionException e) {
        assertTrue(e.getCause() instanceof FirebaseMessagingException);
        FirebaseMessagingException error = (FirebaseMessagingException) e.getCause();
        assertEquals(getTopicManagementErrorCode(statusCode), error.getErrorCode());
        assertEquals("test error", error.getMessage());
        assertTrue(error.getCause() instanceof HttpResponseException);
      }

      checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_SUBSCRIBE_URL);
    }
  }

  @Test
  public void testSubscribeUnknownError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initMessaging(response, interceptor);
    response.setStatusCode(500).setContent("{}");
    try {
      messaging.subscribeToTopicAsync(ImmutableList.of("id1", "id2"), "test-topic").get();
      fail("No error thrown for HTTP error");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseMessagingException);
      FirebaseMessagingException error = (FirebaseMessagingException) e.getCause();
      assertEquals(getTopicManagementErrorCode(500), error.getErrorCode());
      assertEquals("Unexpected HTTP response with status: 500; body: {}", error.getMessage());
      assertTrue(error.getCause() instanceof HttpResponseException);
    }

    checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_SUBSCRIBE_URL);
  }

  @Test
  public void testSubscribeTransportError() throws Exception {
    FirebaseMessaging messaging = initFaultyTransportMessaging();
    try {
      messaging.subscribeToTopicAsync(ImmutableList.of("id1", "id2"), "test-topic").get();
      fail("No error thrown for HTTP error");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseMessagingException);
      FirebaseMessagingException error = (FirebaseMessagingException) e.getCause();
      assertEquals("internal-error", error.getErrorCode());
      assertEquals("Error while calling IID backend service", error.getMessage());
      assertTrue(error.getCause() instanceof IOException);
    }
  }

  @Test
  public void testInvalidUnsubscribe() {
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initDefaultMessaging(interceptor);

    for (TopicMgtArgs args : INVALID_TOPIC_MGT_ARGS) {
      try {
        messaging.unsubscribeFromTopicAsync(args.registrationTokens, args.topic);
        fail("No error thrown for invalid args");
      } catch (IllegalArgumentException expected) {
        // expected
      }
    }

    assertNull(interceptor.getResponse());
  }

  @Test
  public void testUnsubscribe() throws Exception {
    final String responseString = "{\"results\": [{}, {\"error\": \"error_reason\"}]}";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    final FirebaseMessaging messaging = initMessaging(response, interceptor);

    List<GenericFunction<TopicManagementResponse>> functions = ImmutableList.of(
        new GenericFunction<TopicManagementResponse>() {
          @Override
          public TopicManagementResponse call(Object... args) throws Exception {
            return messaging.unsubscribeFromTopicAsync(ImmutableList.of("id1", "id2"),
                "test-topic").get();
          }
        },
        new GenericFunction<TopicManagementResponse>() {
          @Override
          public TopicManagementResponse call(Object... args) throws Exception {
            return messaging.unsubscribeFromTopic(ImmutableList.of("id1", "id2"), "test-topic");
          }
        },
        new GenericFunction<TopicManagementResponse>() {
          @Override
          public TopicManagementResponse call(Object... args) throws Exception {
            return messaging.unsubscribeFromTopic(ImmutableList.of("id1", "id2"),
                "/topics/test-topic");
          }
        }
    );

    for (GenericFunction<TopicManagementResponse> fn : functions) {
      response.setContent(responseString);
      TopicManagementResponse result = fn.call();
      checkTopicManagementRequestHeader(
          interceptor.getLastRequest(), TEST_IID_UNSUBSCRIBE_URL);
      checkTopicManagementRequest(interceptor.getLastRequest(), result);
    }
  }

  @Test
  public void testUnsubscribeError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initMessaging(response, interceptor);
    for (int statusCode : HTTP_ERRORS) {
      response.setStatusCode(statusCode).setContent("{\"error\": \"test error\"}");
      try {
        messaging.unsubscribeFromTopicAsync(ImmutableList.of("id1", "id2"), "test-topic").get();
        fail("No error thrown for HTTP error");
      } catch (ExecutionException e) {
        assertTrue(e.getCause() instanceof FirebaseMessagingException);
        FirebaseMessagingException error = (FirebaseMessagingException) e.getCause();
        assertEquals(getTopicManagementErrorCode(statusCode), error.getErrorCode());
        assertEquals("test error", error.getMessage());
        assertTrue(error.getCause() instanceof HttpResponseException);
      }

      checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_UNSUBSCRIBE_URL);
    }
  }

  @Test
  public void testUnsubscribeUnknownError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initMessaging(response, interceptor);
    response.setStatusCode(500).setContent("{}");
    try {
      messaging.unsubscribeFromTopicAsync(ImmutableList.of("id1", "id2"), "test-topic").get();
      fail("No error thrown for HTTP error");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseMessagingException);
      FirebaseMessagingException error = (FirebaseMessagingException) e.getCause();
      assertEquals(getTopicManagementErrorCode(500), error.getErrorCode());
      assertEquals("Unexpected HTTP response with status: 500; body: {}", error.getMessage());
      assertTrue(error.getCause() instanceof HttpResponseException);
    }

    checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_UNSUBSCRIBE_URL);
  }

  @Test
  public void testUnsubscribeTransportError() throws Exception {
    FirebaseMessaging messaging = initFaultyTransportMessaging();
    try {
      messaging.unsubscribeFromTopicAsync(ImmutableList.of("id1", "id2"), "test-topic").get();
      fail("No error thrown for HTTP error");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseMessagingException);
      FirebaseMessagingException error = (FirebaseMessagingException) e.getCause();
      assertEquals("internal-error", error.getErrorCode());
      assertEquals("Error while calling IID backend service", error.getMessage());
      assertTrue(error.getCause() instanceof IOException);
    }
  }

  private static FirebaseMessaging initMessaging(
      MockLowLevelHttpResponse mockResponse, HttpResponseInterceptor interceptor) {
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(mockResponse)
        .build();
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .setHttpTransport(transport)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    return new FirebaseMessaging(app, interceptor);
  }

  private static FirebaseMessaging initDefaultMessaging() {
    return initDefaultMessaging(null);
  }

  private static FirebaseMessaging initDefaultMessaging(HttpResponseInterceptor interceptor) {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);
    return new FirebaseMessaging(app, interceptor);
  }

  private static FirebaseMessaging initFaultyTransportMessaging() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .setHttpTransport(new FailingHttpTransport())
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);
    return new FirebaseMessaging(app, null);
  }

  private void checkRequest(
      HttpRequest request, Map<String, Object> expected) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    request.getContent().writeTo(out);
    JsonParser parser = Utils.getDefaultJsonFactory().createJsonParser(out.toString());
    Map<String, Object> parsed = new HashMap<>();
    parser.parseAndClose(parsed);
    assertEquals(expected, parsed);
  }

  private void checkRequestHeader(HttpRequest request) {
    assertEquals("POST", request.getRequestMethod());
    assertEquals(TEST_FCM_URL, request.getUrl().toString());
    assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
    assertEquals("2", request.getHeaders().get("X-GOOG-API-FORMAT-VERSION"));
  }

  private FirebaseMessaging getMessagingForBatchRequest(
      String responsePayload, TestResponseInterceptor interceptor) {
    MockLowLevelHttpResponse httpResponse = new MockLowLevelHttpResponse()
        .setContentType("multipart/mixed; boundary=test_boundary")
        .setContent(responsePayload);
    return initMessaging(httpResponse, interceptor);
  }

  private void assertSendBatchSuccess(
      BatchResponse batchResponse, TestResponseInterceptor interceptor) throws IOException {

    assertEquals(2, batchResponse.getSuccessCount());
    assertEquals(0, batchResponse.getFailureCount());

    List<SendResponse> responses = batchResponse.getResponses();
    assertEquals(2, responses.size());
    for (int i = 0; i < 2; i++) {
      SendResponse sendResponse = responses.get(i);
      assertTrue(sendResponse.isSuccessful());
      assertEquals("projects/test-project/messages/" + (i + 1), sendResponse.getMessageId());
      assertNull(sendResponse.getException());
    }
    checkBatchRequestHeader(interceptor.getLastRequest());
    checkBatchRequest(interceptor.getLastRequest(), 2);
  }

  private void assertSendBatchFailure(
      BatchResponse batchResponse, TestResponseInterceptor interceptor) throws IOException {

    assertEquals(1, batchResponse.getSuccessCount());
    assertEquals(2, batchResponse.getFailureCount());

    List<SendResponse> responses = batchResponse.getResponses();
    assertEquals(3, responses.size());
    SendResponse firstResponse = responses.get(0);
    assertTrue(firstResponse.isSuccessful());
    assertEquals("projects/test-project/messages/1", firstResponse.getMessageId());
    assertNull(firstResponse.getException());

    SendResponse secondResponse = responses.get(1);
    assertFalse(secondResponse.isSuccessful());
    assertNull(secondResponse.getMessageId());
    FirebaseMessagingException exception = secondResponse.getException();
    assertNotNull(exception);
    assertEquals("invalid-argument", exception.getErrorCode());

    SendResponse thirdResponse = responses.get(2);
    assertFalse(thirdResponse.isSuccessful());
    assertNull(thirdResponse.getMessageId());
    exception = thirdResponse.getException();
    assertNotNull(exception);
    assertEquals("invalid-argument", exception.getErrorCode());

    checkBatchRequestHeader(interceptor.getLastRequest());
    checkBatchRequest(interceptor.getLastRequest(), 3);
  }

  private void checkBatchRequestHeader(HttpRequest request) {
    assertEquals("POST", request.getRequestMethod());
    assertEquals("https://fcm.googleapis.com/batch", request.getUrl().toString());
    assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
  }

  private void checkBatchRequest(HttpRequest request, int expected) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    request.getContent().writeTo(out);
    String[] lines = out.toString().split("\n");
    assertEquals(expected, countLinesWithPrefix(lines, "POST " + TEST_FCM_URL));
    assertEquals(expected, countLinesWithPrefix(lines, "x-goog-api-format-version: 2"));
  }

  private int countLinesWithPrefix(String[] lines, String prefix) {
    int matchCount = 0;
    for (String line : lines) {
      if (line.trim().startsWith(prefix)) {
        matchCount++;
      }
    }
    return matchCount;
  }

  private static String getTopicManagementErrorCode(int statusCode) {
    String code = InstanceIdClient.IID_ERROR_CODES.get(statusCode);
    if (code == null) {
      code = "unknown-error";
    }
    return code;
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
    assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
  }

  private static class TopicMgtArgs {
    private final List<String> registrationTokens;
    private final String topic;

    TopicMgtArgs(List<String> registrationTokens, String topic) {
      this.registrationTokens = registrationTokens;
      this.topic = topic;
    }
  }

  private static class FailingHttpTransport extends HttpTransport {
    @Override
    protected LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
      throw new IOException("transport error");
    }
  }

  private static Map<Message, Map<String, Object>> buildTestMessages() {
    ImmutableMap.Builder<Message, Map<String, Object>> builder = ImmutableMap.builder();

    // Empty message
    builder.put(
        Message.builder().setTopic("test-topic").build(),
        ImmutableMap.<String, Object>of("topic", "test-topic"));

    // Notification message
    builder.put(
        Message.builder()
            .setNotification(new Notification("test title", "test body"))
            .setTopic("test-topic")
            .build(),
        ImmutableMap.<String, Object>of(
            "topic", "test-topic",
            "notification", ImmutableMap.of("title", "test title", "body", "test body")));

    // Data message
    builder.put(
        Message.builder()
            .putData("k1", "v1")
            .putData("k2", "v2")
            .putAllData(ImmutableMap.of("k3", "v3", "k4", "v4"))
            .setTopic("test-topic")
            .build(),
        ImmutableMap.<String, Object>of(
            "topic", "test-topic",
            "data", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3", "k4", "v4")));

    // Android message
    builder.put(
        Message.builder()
            .setAndroidConfig(AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setTtl(TimeUnit.SECONDS.toMillis(123))
                .setRestrictedPackageName("test-package")
                .setCollapseKey("test-key")
                .setNotification(AndroidNotification.builder()
                    .setClickAction("test-action")
                    .setTitle("test-title")
                    .setBody("test-body")
                    .setIcon("test-icon")
                    .setColor("#112233")
                    .setTag("test-tag")
                    .setSound("test-sound")
                    .setTitleLocalizationKey("test-title-key")
                    .setBodyLocalizationKey("test-body-key")
                    .addTitleLocalizationArg("t-arg1")
                    .addAllTitleLocalizationArgs(ImmutableList.of("t-arg2", "t-arg3"))
                    .addBodyLocalizationArg("b-arg1")
                    .addAllBodyLocalizationArgs(ImmutableList.of("b-arg2", "b-arg3"))
                    .setChannelId("channel-id")
                    .build())
                .build())
            .setTopic("test-topic")
            .build(),
        ImmutableMap.<String, Object>of(
            "topic", "test-topic",
            "android", ImmutableMap.of(
                "priority", "high",
                "collapse_key", "test-key",
                "ttl", "123s",
                "restricted_package_name", "test-package",
                "notification", ImmutableMap.builder()
                    .put("click_action", "test-action")
                    .put("title", "test-title")
                    .put("body", "test-body")
                    .put("icon", "test-icon")
                    .put("color", "#112233")
                    .put("tag", "test-tag")
                    .put("sound", "test-sound")
                    .put("title_loc_key", "test-title-key")
                    .put("title_loc_args", ImmutableList.of("t-arg1", "t-arg2", "t-arg3"))
                    .put("body_loc_key", "test-body-key")
                    .put("body_loc_args", ImmutableList.of("b-arg1", "b-arg2", "b-arg3"))
                    .put("channel_id", "channel-id")
                    .build()
            )
        ));

    // APNS message
    builder.put(
        Message.builder()
            .setApnsConfig(ApnsConfig.builder()
                .putHeader("h1", "v1")
                .putAllHeaders(ImmutableMap.of("h2", "v2", "h3", "v3"))
                .putAllCustomData(ImmutableMap.<String, Object>of("k1", "v1", "k2", true))
                .setAps(Aps.builder()
                    .setBadge(42)
                    .setAlert(ApsAlert.builder()
                        .setTitle("test-title")
                        .setSubtitle("test-subtitle")
                        .setBody("test-body")
                        .build())
                    .build())
                .build())
            .setTopic("test-topic")
            .build(),
        ImmutableMap.<String, Object>of(
            "topic", "test-topic",
            "apns", ImmutableMap.of(
                "headers", ImmutableMap.of("h1", "v1", "h2", "v2", "h3", "v3"),
                "payload", ImmutableMap.of("k1", "v1", "k2", true,
                    "aps", ImmutableMap.<String, Object>of("badge", new BigDecimal(42),
                        "alert", ImmutableMap.<String, Object>of(
                            "title", "test-title", "subtitle", "test-subtitle",
                            "body", "test-body"))))
        ));

    // Webpush message (no notification)
    builder.put(
        Message.builder()
            .setWebpushConfig(WebpushConfig.builder()
                .putHeader("h1", "v1")
                .putAllHeaders(ImmutableMap.of("h2", "v2", "h3", "v3"))
                .putData("k1", "v1")
                .putAllData(ImmutableMap.of("k2", "v2", "k3", "v3"))
                .build())
            .setTopic("test-topic")
            .build(),
        ImmutableMap.<String, Object>of(
            "topic", "test-topic",
            "webpush", ImmutableMap.of(
                "headers", ImmutableMap.of("h1", "v1", "h2", "v2", "h3", "v3"),
                "data", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"))
        ));

    // Webpush message (simple notification)
    builder.put(
        Message.builder()
            .setWebpushConfig(WebpushConfig.builder()
                .putHeader("h1", "v1")
                .putAllHeaders(ImmutableMap.of("h2", "v2", "h3", "v3"))
                .putData("k1", "v1")
                .putAllData(ImmutableMap.of("k2", "v2", "k3", "v3"))
                .setNotification(new WebpushNotification("test-title", "test-body", "test-icon"))
                .build())
            .setTopic("test-topic")
            .build(),
        ImmutableMap.<String, Object>of(
            "topic", "test-topic",
            "webpush", ImmutableMap.of(
                "headers", ImmutableMap.of("h1", "v1", "h2", "v2", "h3", "v3"),
                "data", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
                "notification", ImmutableMap.of(
                    "title", "test-title", "body", "test-body", "icon", "test-icon"))
        ));

    // Webpush message (all fields)
    builder.put(
        Message.builder()
            .setWebpushConfig(WebpushConfig.builder()
                .putHeader("h1", "v1")
                .putAllHeaders(ImmutableMap.of("h2", "v2", "h3", "v3"))
                .putData("k1", "v1")
                .putAllData(ImmutableMap.of("k2", "v2", "k3", "v3"))
                .setNotification(WebpushNotification.builder()
                    .setTitle("test-title")
                    .setBody("test-body")
                    .setIcon("test-icon")
                    .setBadge("test-badge")
                    .setImage("test-image")
                    .setLanguage("test-lang")
                    .setTag("test-tag")
                    .setData(ImmutableList.of("arbitrary", "data"))
                    .setDirection(Direction.AUTO)
                    .setRenotify(true)
                    .setRequireInteraction(false)
                    .setSilent(true)
                    .setTimestampMillis(100L)
                    .setVibrate(new int[]{200, 100, 200})
                    .addAction(new Action("action1", "title1"))
                    .addAllActions(ImmutableList.of(new Action("action2", "title2", "icon2")))
                    .putCustomData("k4", "v4")
                    .putAllCustomData(ImmutableMap.<String, Object>of("k5", "v5", "k6", "v6"))
                    .build())
                .build())
            .setTopic("test-topic")
            .build(),
        ImmutableMap.<String, Object>of(
            "topic", "test-topic",
            "webpush", ImmutableMap.of(
                "headers", ImmutableMap.of("h1", "v1", "h2", "v2", "h3", "v3"),
                "data", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
                "notification", ImmutableMap.builder()
                    .put("title", "test-title")
                    .put("body", "test-body")
                    .put("icon", "test-icon")
                    .put("badge", "test-badge")
                    .put("image", "test-image")
                    .put("lang", "test-lang")
                    .put("tag", "test-tag")
                    .put("data", ImmutableList.of("arbitrary", "data"))
                    .put("renotify", true)
                    .put("requireInteraction", false)
                    .put("silent", true)
                    .put("dir", "auto")
                    .put("timestamp", new BigDecimal(100))
                    .put("vibrate", ImmutableList.of(
                        new BigDecimal(200), new BigDecimal(100), new BigDecimal(200)))
                    .put("actions", ImmutableList.of(
                        ImmutableMap.of("action", "action1", "title", "title1"),
                        ImmutableMap.of("action", "action2", "title", "title2", "icon", "icon2")))
                    .put("k4", "v4")
                    .put("k5", "v5")
                    .put("k6", "v6")
                    .build())
        ));

    return builder.build();
  }
}
