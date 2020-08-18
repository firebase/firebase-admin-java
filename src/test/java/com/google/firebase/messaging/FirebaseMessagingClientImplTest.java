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
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonParser;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.OutgoingHttpRequest;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.internal.SdkUtils;
import com.google.firebase.messaging.WebpushNotification.Action;
import com.google.firebase.messaging.WebpushNotification.Direction;
import com.google.firebase.testing.TestResponseInterceptor;
import com.google.firebase.testing.TestUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

public class FirebaseMessagingClientImplTest {

  private static final String TEST_FCM_URL =
      "https://fcm.googleapis.com/v1/projects/test-project/messages:send";

  private static final List<Integer> HTTP_ERRORS = ImmutableList.of(401, 404, 500);

  private static final Map<Integer, ErrorCode> HTTP_2_ERROR = ImmutableMap.of(
      401, ErrorCode.UNAUTHENTICATED,
      404, ErrorCode.NOT_FOUND,
      500, ErrorCode.INTERNAL);

  private static final String MOCK_RESPONSE = "{\"name\": \"mock-name\"}";

  private static final String MOCK_BATCH_SUCCESS_RESPONSE = TestUtils.loadResource(
      "fcm_batch_success.txt");

  private static final String MOCK_BATCH_FAILURE_RESPONSE = TestUtils.loadResource(
      "fcm_batch_failure.txt");

  private static final Message EMPTY_MESSAGE = Message.builder()
      .setTopic("test-topic")
      .build();
  private static final List<Message> MESSAGE_LIST = ImmutableList.of(EMPTY_MESSAGE, EMPTY_MESSAGE);
  
  private static final boolean DRY_RUN_ENABLED = true;
  private static final boolean DRY_RUN_DISABLED = false;

  private MockLowLevelHttpResponse response;
  private TestResponseInterceptor interceptor;
  private FirebaseMessagingClient client;

  @Before
  public void setUp() {
    response = new MockLowLevelHttpResponse();
    interceptor = new TestResponseInterceptor();
    client = initMessagingClient(response, interceptor);
  }

  @Test
  public void testSend() throws Exception {
    Map<Message, Map<String, Object>> testMessages = buildTestMessages();
    
    for (Map.Entry<Message, Map<String, Object>> entry : testMessages.entrySet()) {
      response.setContent(MOCK_RESPONSE);
      String resp = client.send(entry.getKey(), DRY_RUN_DISABLED);

      assertEquals("mock-name", resp);
      checkRequestHeader(interceptor.getLastRequest());
      checkRequest(interceptor.getLastRequest(),
          ImmutableMap.<String, Object>of("message", entry.getValue()));
    }
  }

  @Test
  public void testSendDryRun() throws Exception {
    Map<Message, Map<String, Object>> testMessages = buildTestMessages();

    for (Map.Entry<Message, Map<String, Object>> entry : testMessages.entrySet()) {
      response.setContent(MOCK_RESPONSE);
      String resp = client.send(entry.getKey(), DRY_RUN_ENABLED);

      assertEquals("mock-name", resp);
      checkRequestHeader(interceptor.getLastRequest());
      checkRequest(interceptor.getLastRequest(),
          ImmutableMap.of("message", entry.getValue(), "validate_only", true));
    }
  }

  @Test
  public void testSendHttpError() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent("{}");

      try {
        client.send(EMPTY_MESSAGE, DRY_RUN_DISABLED);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        checkExceptionFromHttpResponse(error, HTTP_2_ERROR.get(code), null,
            "Unexpected HTTP response with status: " + code + "\n{}");
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendTransportError() {
    client = initClientWithFaultyTransport();

    try {
      client.send(EMPTY_MESSAGE, DRY_RUN_DISABLED);
      fail("No error thrown for HTTP error");
    } catch (FirebaseMessagingException error) {
      assertEquals(ErrorCode.UNKNOWN, error.getErrorCode());
      assertEquals("Unknown error while making a remote service call: transport error",
          error.getMessage());
      assertTrue(error.getCause() instanceof IOException);
      assertNull(error.getHttpResponse());
      assertNull(error.getMessagingErrorCode());
    }
  }

  @Test
  public void testSendSuccessResponseWithUnexpectedPayload() {
    Map<Message, Map<String, Object>> testMessages = buildTestMessages();

    for (Map.Entry<Message, Map<String, Object>> entry : testMessages.entrySet()) {
      response.setContent("not valid json");

      try {
        client.send(entry.getKey(), DRY_RUN_DISABLED);
        fail("No error thrown for malformed response");
      } catch (FirebaseMessagingException error) {
        assertEquals(ErrorCode.UNKNOWN, error.getErrorCode());
        assertTrue(error.getMessage().startsWith("Error while parsing HTTP response: "));
        assertNotNull(error.getCause());
        assertNotNull(error.getHttpResponse());
        assertNull(error.getMessagingErrorCode());
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendErrorWithZeroContentResponse() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setZeroContent();

      try {
        client.send(EMPTY_MESSAGE, DRY_RUN_DISABLED);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        checkExceptionFromHttpResponse(error, HTTP_2_ERROR.get(code), null,
            "Unexpected HTTP response with status: " + code + "\nnull");
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendErrorWithMalformedResponse() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent("not json");

      try {
        client.send(EMPTY_MESSAGE, DRY_RUN_DISABLED);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        checkExceptionFromHttpResponse(error, HTTP_2_ERROR.get(code), null,
            "Unexpected HTTP response with status: " + code + "\nnot json");
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendErrorWithDetails() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\"}}");

      try {
        client.send(EMPTY_MESSAGE, DRY_RUN_DISABLED);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT, null);
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendErrorWithCanonicalCode() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"NOT_FOUND\", \"message\": \"test error\"}}");

      try {
        client.send(EMPTY_MESSAGE, DRY_RUN_DISABLED);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.NOT_FOUND, null);
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendErrorWithFcmError() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\", "
              + "\"details\":[{\"@type\": \"type.googleapis.com/google.firebase.fcm"
              + ".v1.FcmError\", \"errorCode\": \"UNREGISTERED\"}]}}");

      try {
        client.send(EMPTY_MESSAGE, DRY_RUN_DISABLED);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.UNREGISTERED);
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendErrorWithThirdPartyError() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\", "
              + "\"details\":[{\"@type\": \"type.googleapis.com/google.firebase.fcm"
              + ".v1.FcmError\", \"errorCode\": \"THIRD_PARTY_AUTH_ERROR\"}]}}");

      try {
        client.send(EMPTY_MESSAGE, DRY_RUN_DISABLED);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.THIRD_PARTY_AUTH_ERROR);
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendErrorWithUnknownFcmErrorCode() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\", "
              + "\"details\":[{\"@type\": \"type.googleapis.com/google.firebase.fcm"
              + ".v1.FcmError\", \"errorCode\": \"UNKNOWN_FCM_ERROR\"}]}}");

      try {
        client.send(EMPTY_MESSAGE, DRY_RUN_DISABLED);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT, null);
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendErrorWithDetailsAndNoCode() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\", "
              + "\"details\":[{\"@type\": \"type.googleapis.com/google.firebase.fcm"
              + ".v1.FcmError\"}]}}");

      try {
        client.send(EMPTY_MESSAGE, DRY_RUN_DISABLED);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT, null);
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendAll() throws Exception {
    final TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessagingClient client = initMessagingClientForBatchRequests(
        MOCK_BATCH_SUCCESS_RESPONSE, interceptor);

    BatchResponse responses = client.sendAll(MESSAGE_LIST, false);

    assertBatchResponse(responses, interceptor, 2, 0);
  }

  @Test
  public void testSendAllDryRun() throws Exception {
    final TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessagingClient client = initMessagingClientForBatchRequests(
        MOCK_BATCH_SUCCESS_RESPONSE, interceptor);

    BatchResponse responses = client.sendAll(MESSAGE_LIST, true);

    assertBatchResponse(responses, interceptor, 2, 0);
  }

  @Test
  public void testRequestInitializerAppliedToBatchRequests() throws Exception {
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(getBatchResponse(MOCK_BATCH_SUCCESS_RESPONSE))
        .build();
    HttpRequestInitializer initializer = new HttpRequestInitializer() {
      @Override
      public void initialize(HttpRequest httpRequest) {
        httpRequest.getHeaders().set("x-custom-header", "test-value");
      }
    };
    FirebaseMessagingClientImpl client = FirebaseMessagingClientImpl.builder()
        .setProjectId("test-project")
        .setJsonFactory(Utils.getDefaultJsonFactory())
        .setRequestFactory(transport.createRequestFactory(initializer))
        .setChildRequestFactory(Utils.getDefaultTransport().createRequestFactory())
        .setResponseInterceptor(interceptor)
        .build();

    try {
      client.sendAll(MESSAGE_LIST, DRY_RUN_DISABLED);
    } finally {
      HttpRequest request = interceptor.getLastRequest();
      assertEquals("test-value", request.getHeaders().get("x-custom-header"));
    }
  }

  @Test
  public void testSendAllFailure() throws Exception {
    final TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessagingClient client = initMessagingClientForBatchRequests(
        MOCK_BATCH_FAILURE_RESPONSE, interceptor);
    List<Message> messages = ImmutableList.of(EMPTY_MESSAGE, EMPTY_MESSAGE, EMPTY_MESSAGE);

    BatchResponse responses = client.sendAll(messages, DRY_RUN_DISABLED);

    assertBatchResponse(responses, interceptor, 1, 2);
  }

  @Test
  public void testSendAllHttpError() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent("{}");

      try {
        client.sendAll(MESSAGE_LIST, DRY_RUN_DISABLED);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        checkExceptionFromHttpResponse(error, HTTP_2_ERROR.get(code), null,
            "Unexpected HTTP response with status: " + code + "\n{}");
      }
      checkBatchRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendAllTransportError() {
    FirebaseMessagingClient client = initClientWithFaultyTransport();

    try {
      client.sendAll(MESSAGE_LIST, DRY_RUN_DISABLED);
      fail("No error thrown for HTTP error");
    } catch (FirebaseMessagingException error) {
      assertEquals(ErrorCode.UNKNOWN, error.getErrorCode());
      assertEquals(
          "Unknown error while making a remote service call: transport error", error.getMessage());
      assertTrue(error.getCause() instanceof IOException);
      assertNull(error.getHttpResponse());
      assertNull(error.getMessagingErrorCode());
    }
  }

  @Test
  public void testSendAllErrorWithEmptyResponse() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setZeroContent();

      try {
        client.sendAll(MESSAGE_LIST, DRY_RUN_DISABLED);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        checkExceptionFromHttpResponse(error, HTTP_2_ERROR.get(code), null,
            "Unexpected HTTP response with status: " + code + "\nnull");
      }
      checkBatchRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendAllErrorWithDetails() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\"}}");

      try {
        client.sendAll(MESSAGE_LIST, DRY_RUN_DISABLED);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT, null);
      }
      checkBatchRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendAllErrorWithCanonicalCode() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"NOT_FOUND\", \"message\": \"test error\"}}");

      try {
        client.sendAll(MESSAGE_LIST, DRY_RUN_DISABLED);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.NOT_FOUND, null);
      }
      checkBatchRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendAllErrorWithFcmError() {
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\", "
              + "\"details\":[{\"@type\": \"type.googleapis.com/google.firebase.fcm"
              + ".v1.FcmError\", \"errorCode\": \"UNREGISTERED\"}]}}");

      try {
        client.sendAll(MESSAGE_LIST, DRY_RUN_DISABLED);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.UNREGISTERED);
      }
      checkBatchRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendAllErrorWithoutMessage() {
    final String responseBody = "{\"error\": {\"status\": \"INVALID_ARGUMENT\", "
        + "\"details\":[{\"@type\": \"type.googleapis.com/google.firebase.fcm"
        + ".v1.FcmError\", \"errorCode\": \"UNREGISTERED\"}]}}";
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(responseBody);

      try {
        client.sendAll(MESSAGE_LIST, DRY_RUN_DISABLED);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        checkExceptionFromHttpResponse(error, ErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.UNREGISTERED,
            "Unexpected HTTP response with status: " + code + "\n" + responseBody);
      }
      checkBatchRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderNullProjectId() {
    fullyPopulatedBuilder().setProjectId(null).build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderEmptyProjectId() {
    fullyPopulatedBuilder().setProjectId("").build();
  }

  @Test(expected = NullPointerException.class)
  public void testBuilderNullRequestFactory() {
    fullyPopulatedBuilder().setRequestFactory(null).build();
  }

  @Test(expected = NullPointerException.class)
  public void testBuilderNullChildRequestFactory() {
    fullyPopulatedBuilder().setChildRequestFactory(null).build();
  }

  @Test
  public void testFromApp() throws IOException {
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    try {
      FirebaseMessagingClientImpl client = FirebaseMessagingClientImpl.fromApp(app);

      assertEquals(TEST_FCM_URL, client.getFcmSendUrl());
      assertSame(options.getJsonFactory(), client.getJsonFactory());

      HttpRequest request = client.getRequestFactory().buildGetRequest(
          new GenericUrl("https://example.com"));
      assertEquals("Bearer test-token", request.getHeaders().getAuthorization());

      request = client.getChildRequestFactory().buildGetRequest(
          new GenericUrl("https://example.com"));
      assertNull(request.getHeaders().getAuthorization());
    } finally {
      app.delete();
    }
  }

  private FirebaseMessagingClientImpl initMessagingClient(
      MockLowLevelHttpResponse mockResponse, HttpResponseInterceptor interceptor) {
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(mockResponse)
        .build();

    return FirebaseMessagingClientImpl.builder()
        .setProjectId("test-project")
        .setJsonFactory(Utils.getDefaultJsonFactory())
        .setRequestFactory(transport.createRequestFactory())
        .setChildRequestFactory(Utils.getDefaultTransport().createRequestFactory())
        .setResponseInterceptor(interceptor)
        .build();
  }

  private FirebaseMessagingClientImpl initMessagingClientForBatchRequests(
      String responsePayload, TestResponseInterceptor interceptor) {
    MockLowLevelHttpResponse httpResponse = getBatchResponse(responsePayload);
    return initMessagingClient(httpResponse, interceptor);
  }

  private MockLowLevelHttpResponse getBatchResponse(String responsePayload) {
    return new MockLowLevelHttpResponse()
        .setContentType("multipart/mixed; boundary=test_boundary")
        .setContent(responsePayload);
  }

  private FirebaseMessagingClientImpl initClientWithFaultyTransport() {
    HttpTransport transport = TestUtils.createFaultyHttpTransport();
    return FirebaseMessagingClientImpl.builder()
        .setProjectId("test-project")
        .setJsonFactory(Utils.getDefaultJsonFactory())
        .setRequestFactory(transport.createRequestFactory())
        .setChildRequestFactory(Utils.getDefaultTransport().createRequestFactory())
        .build();
  }

  private void checkRequestHeader(HttpRequest request) {
    assertEquals("POST", request.getRequestMethod());
    assertEquals(TEST_FCM_URL, request.getUrl().toString());
    HttpHeaders headers = request.getHeaders();
    assertEquals("2", headers.get("X-GOOG-API-FORMAT-VERSION"));
    assertEquals("fire-admin-java/" + SdkUtils.getVersion(), headers.get("X-Firebase-Client"));
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

  private void assertBatchResponse(
      BatchResponse batchResponse, TestResponseInterceptor interceptor,
      int successCount, int failureCount) throws IOException {

    assertEquals(successCount, batchResponse.getSuccessCount());
    assertEquals(failureCount, batchResponse.getFailureCount());

    List<SendResponse> responses = batchResponse.getResponses();
    assertEquals(successCount + failureCount, responses.size());
    for (int i = 0; i < successCount; i++) {
      SendResponse sendResponse = responses.get(i);
      assertTrue(sendResponse.isSuccessful());
      assertEquals("projects/test-project/messages/" + (i + 1), sendResponse.getMessageId());
      assertNull(sendResponse.getException());
    }

    for (int i = successCount; i < failureCount; i++) {
      SendResponse sendResponse = responses.get(i);
      assertFalse(sendResponse.isSuccessful());
      assertNull(sendResponse.getMessageId());

      FirebaseMessagingException exception = sendResponse.getException();
      assertNotNull(exception);
      assertEquals(ErrorCode.INVALID_ARGUMENT, exception.getErrorCode());
      assertNull(exception.getCause());
      assertNull(exception.getHttpResponse());
      assertEquals(MessagingErrorCode.INVALID_ARGUMENT, exception.getMessagingErrorCode());
    }

    checkBatchRequestHeader(interceptor.getLastRequest());
    checkBatchRequest(interceptor.getLastRequest(), successCount + failureCount);
  }

  private void checkBatchRequestHeader(HttpRequest request) {
    assertEquals("POST", request.getRequestMethod());
    assertEquals("https://fcm.googleapis.com/batch", request.getUrl().toString());
  }

  private void checkBatchRequest(HttpRequest request, int expectedParts) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    request.getContent().writeTo(out);
    String[] lines = out.toString().split("\n");
    assertEquals(expectedParts, countLinesWithPrefix(lines, "POST " + TEST_FCM_URL));
    assertEquals(expectedParts, countLinesWithPrefix(lines, "x-goog-api-format-version: 2"));
    assertEquals(expectedParts, countLinesWithPrefix(
        lines, "x-firebase-client: fire-admin-java/" + SdkUtils.getVersion()));
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

  private FirebaseMessagingClientImpl.Builder fullyPopulatedBuilder() {
    return FirebaseMessagingClientImpl.builder()
        .setProjectId("test-project")
        .setJsonFactory(Utils.getDefaultJsonFactory())
        .setRequestFactory(Utils.getDefaultTransport().createRequestFactory())
        .setChildRequestFactory(Utils.getDefaultTransport().createRequestFactory());
  }

  private void checkExceptionFromHttpResponse(
      FirebaseMessagingException error,
      ErrorCode expectedCode,
      MessagingErrorCode expectedMessagingCode) {
    checkExceptionFromHttpResponse(error, expectedCode, expectedMessagingCode, "test error");
  }

  private void checkExceptionFromHttpResponse(
      FirebaseMessagingException error,
      ErrorCode expectedCode,
      MessagingErrorCode expectedMessagingCode,
      String expectedMessage) {
    assertEquals(expectedCode, error.getErrorCode());
    assertEquals(expectedMessage, error.getMessage());
    assertTrue(error.getCause() instanceof HttpResponseException);
    assertEquals(expectedMessagingCode, error.getMessagingErrorCode());

    assertNotNull(error.getHttpResponse());
    OutgoingHttpRequest request = error.getHttpResponse().getRequest();
    assertEquals(HttpMethods.POST, request.getMethod());
    assertTrue(request.getUrl().startsWith("https://fcm.googleapis.com"));
  }

  private static Map<Message, Map<String, Object>> buildTestMessages() {
    // Create a map of FCM messages to their expected JSON serializations.
    ImmutableMap.Builder<Message, Map<String, Object>> builder = ImmutableMap.builder();

    // Empty message
    builder.put(
        EMPTY_MESSAGE,
        ImmutableMap.<String, Object>of("topic", "test-topic"));

    // Notification message
    builder.put(
        Message.builder()
            .setNotification(Notification.builder()
                .setTitle("test title")
                .setBody("test body")
                .build())
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
                .setFcmOptions(WebpushFcmOptions.withLink("https://firebase.google.com"))
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
                    .build(),
                "fcm_options", ImmutableMap.of("link", "https://firebase.google.com"))
        ));

    return builder.build();
  }
}
