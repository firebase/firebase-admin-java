package com.google.firebase.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.json.JsonParser;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.testing.TestResponseInterceptor;
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

  private static final ImmutableList.Builder<String> tooManyIds = ImmutableList.builder();

  static {
    for (int i = 0; i < 1001; i++) {
      tooManyIds.add("id" + i);
    }
  }

  private static final List<TopicMgtArgs> INVALID_TOPIC_MGT_ARGS = ImmutableList.of(
      new TopicMgtArgs(null, null),
      new TopicMgtArgs(null, "test-topic"),
      new TopicMgtArgs(ImmutableList.<String>of(), "test-topic"),
      new TopicMgtArgs(ImmutableList.of(""), "test-topic"),
      new TopicMgtArgs(tooManyIds.build(), "test-topic"),
      new TopicMgtArgs(ImmutableList.of(""), null),
      new TopicMgtArgs(ImmutableList.of("id"), ""),
      new TopicMgtArgs(ImmutableList.of("id"), "foo*")
  );

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
      FirebaseMessaging.getInstance();
      fail("No error thrown for missing project ID");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testNullMessage() throws Exception {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .build();
    FirebaseApp.initializeApp(options);

    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    messaging.setInterceptor(interceptor);
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
        .setContent("{\"name\": \"mock-name\"}");
    FirebaseMessaging messaging = initMessaging(response);
    Map<Message, Map<String, Object>> testMessages = buildTestMessages();

    for (Map.Entry<Message, Map<String, Object>> entry : testMessages.entrySet()) {
      response.setContent("{\"name\": \"mock-name\"}");
      TestResponseInterceptor interceptor = new TestResponseInterceptor();
      messaging.setInterceptor(interceptor);
      String resp = messaging.sendAsync(entry.getKey()).get();
      assertEquals("mock-name", resp);

      assertNotNull(interceptor.getResponse());
      HttpRequest request = interceptor.getResponse().getRequest();
      assertEquals("POST", request.getRequestMethod());
      assertEquals(TEST_FCM_URL, request.getUrl().toString());
      assertEquals("Bearer test-token", request.getHeaders().getAuthorization());

      checkRequest(request, ImmutableMap.<String, Object>of("message", entry.getValue()));
    }
  }

  @Test
  public void testSendDryRun() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setContent("{\"name\": \"mock-name\"}");
    FirebaseMessaging messaging = initMessaging(response);
    Map<Message, Map<String, Object>> testMessages = buildTestMessages();

    for (Map.Entry<Message, Map<String, Object>> entry : testMessages.entrySet()) {
      response.setContent("{\"name\": \"mock-name\"}");
      TestResponseInterceptor interceptor = new TestResponseInterceptor();
      messaging.setInterceptor(interceptor);
      String resp = messaging.sendAsync(entry.getKey(), true).get();
      assertEquals("mock-name", resp);

      assertNotNull(interceptor.getResponse());
      HttpRequest request = interceptor.getResponse().getRequest();
      assertEquals("POST", request.getRequestMethod());
      assertEquals(TEST_FCM_URL, request.getUrl().toString());
      assertEquals("Bearer test-token", request.getHeaders().getAuthorization());

      checkRequest(request, ImmutableMap.of("message", entry.getValue(), "validate_only", true));
    }
  }

  private static void checkRequest(
      HttpRequest request, Map<String, Object> expected) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    request.getContent().writeTo(out);
    JsonParser parser = Utils.getDefaultJsonFactory().createJsonParser(out.toString());
    Map<String, Object> parsed = new HashMap<>();
    parser.parseAndClose(parsed);
    assertEquals(expected, parsed);
  }

  @Test
  public void testSendError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    FirebaseMessaging messaging = initMessaging(response);
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent("{}");
      TestResponseInterceptor interceptor = new TestResponseInterceptor();
      messaging.setInterceptor(interceptor);
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

      assertNotNull(interceptor.getResponse());
      HttpRequest request = interceptor.getResponse().getRequest();
      assertEquals("POST", request.getRequestMethod());
      assertEquals(TEST_FCM_URL, request.getUrl().toString());
      assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
    }
  }

  @Test
  public void testSendErrorWithDetails() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    FirebaseMessaging messaging = initMessaging(response);
    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\"}}");
      TestResponseInterceptor interceptor = new TestResponseInterceptor();
      messaging.setInterceptor(interceptor);
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

      assertNotNull(interceptor.getResponse());
      HttpRequest request = interceptor.getResponse().getRequest();
      assertEquals("POST", request.getRequestMethod());
      assertEquals(TEST_FCM_URL, request.getUrl().toString());
      assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
    }
  }

  @Test
  public void testInvalidSubscribe() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .build();
    FirebaseApp.initializeApp(options);
    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    messaging.setInterceptor(interceptor);

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
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setContent("{\"results\": [{}, {\"error\": \"error_reason\"}]}");
    FirebaseMessaging messaging = initMessaging(response);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    messaging.setInterceptor(interceptor);

    TopicManagementResponse result = messaging.subscribeToTopicAsync(
        ImmutableList.of("id1", "id2"), "test-topic").get();
    assertEquals(1, result.getSuccessCount());
    assertEquals(1, result.getFailureCount());
    assertEquals(1, result.getErrors().size());
    assertEquals(1, result.getErrors().get(0).getIndex());
    assertEquals("unknown-error", result.getErrors().get(0).getReason());

    assertNotNull(interceptor.getResponse());
    HttpRequest request = interceptor.getResponse().getRequest();
    assertEquals("POST", request.getRequestMethod());
    assertEquals(TEST_IID_SUBSCRIBE_URL, request.getUrl().toString());
    assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
    assertEquals("true", request.getHeaders().get("access_token_auth"));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    request.getContent().writeTo(out);
    assertEquals("{\"to\":\"/topics/test-topic\",\"registration_tokens\":[\"id1\",\"id2\"]}",
        out.toString());
  }

  @Test
  public void testSubscribeError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    FirebaseMessaging messaging = initMessaging(response);
    for (int statusCode : HTTP_ERRORS) {
      response.setStatusCode(statusCode).setContent("{\"error\": \"test error\"}");
      TestResponseInterceptor interceptor = new TestResponseInterceptor();
      messaging.setInterceptor(interceptor);
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

      assertNotNull(interceptor.getResponse());
      HttpRequest request = interceptor.getResponse().getRequest();
      assertEquals("POST", request.getRequestMethod());
      assertEquals(TEST_IID_SUBSCRIBE_URL, request.getUrl().toString());
      assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
    }
  }

  @Test
  public void testInvalidUnsubscribe() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .build();
    FirebaseApp.initializeApp(options);
    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    messaging.setInterceptor(interceptor);

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
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setContent("{\"results\": [{}, {\"error\": \"error_reason\"}]}");
    FirebaseMessaging messaging = initMessaging(response);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    messaging.setInterceptor(interceptor);

    TopicManagementResponse result = messaging.unsubscribeFromTopicAsync(
        ImmutableList.of("id1", "id2"), "test-topic").get();
    assertEquals(1, result.getSuccessCount());
    assertEquals(1, result.getFailureCount());
    assertEquals(1, result.getErrors().size());
    assertEquals(1, result.getErrors().get(0).getIndex());
    assertEquals("unknown-error", result.getErrors().get(0).getReason());

    assertNotNull(interceptor.getResponse());
    HttpRequest request = interceptor.getResponse().getRequest();
    assertEquals("POST", request.getRequestMethod());
    assertEquals(TEST_IID_UNSUBSCRIBE_URL, request.getUrl().toString());
    assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
    assertEquals("true", request.getHeaders().get("access_token_auth"));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    request.getContent().writeTo(out);
    assertEquals("{\"to\":\"/topics/test-topic\",\"registration_tokens\":[\"id1\",\"id2\"]}",
        out.toString());
  }

  @Test
  public void testUnsubscribeError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    FirebaseMessaging messaging = initMessaging(response);
    for (int statusCode : HTTP_ERRORS) {
      response.setStatusCode(statusCode).setContent("{\"error\": \"test error\"}");
      TestResponseInterceptor interceptor = new TestResponseInterceptor();
      messaging.setInterceptor(interceptor);
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

      assertNotNull(interceptor.getResponse());
      HttpRequest request = interceptor.getResponse().getRequest();
      assertEquals("POST", request.getRequestMethod());
      assertEquals(TEST_IID_UNSUBSCRIBE_URL, request.getUrl().toString());
      assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
    }
  }

  private static String getTopicManagementErrorCode(int statusCode) {
    String code = FirebaseMessaging.IID_ERROR_CODES.get(statusCode);
    if (code == null) {
      code = "unknown-error";
    }
    return code;
  }

  private static FirebaseMessaging initMessaging(MockLowLevelHttpResponse mockResponse) {
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(mockResponse)
        .build();
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .setHttpTransport(transport)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    assertSame(messaging, FirebaseMessaging.getInstance(app));
    return messaging;
  }

  private static class TopicMgtArgs {
    private final List<String> registrationTokens;
    private final String topic;

    TopicMgtArgs(List<String> registrationTokens, String topic) {
      this.registrationTokens = registrationTokens;
      this.topic = topic;
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
                            "title", "test-title", "body", "test-body"))))
        ));

    // Webpush message
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

    return builder.build();
  }
}
