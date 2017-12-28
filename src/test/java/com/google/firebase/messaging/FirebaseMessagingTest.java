package com.google.firebase.messaging;

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
import com.google.common.collect.ImmutableList;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.testing.TestResponseInterceptor;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;
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
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    messaging.setInterceptor(interceptor);
    String resp = messaging.sendAsync(Message.builder().setTopic("test-topic").build()).get();
    assertEquals("mock-name", resp);

    assertNotNull(interceptor.getResponse());
    HttpRequest request = interceptor.getResponse().getRequest();
    assertEquals("POST", request.getRequestMethod());
    assertEquals(TEST_FCM_URL, request.getUrl().toString());
    assertEquals("Bearer test-token", request.getHeaders().getAuthorization());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    request.getContent().writeTo(out);
    assertEquals("{\"message\":{\"topic\":\"test-topic\"}}", out.toString());
  }

  @Test
  public void testSendError() throws Exception {
    for (int statusCode : HTTP_ERRORS) {
      MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
          .setStatusCode(statusCode)
          .setContent("test error");
      FirebaseMessaging messaging = initMessaging(response);
      TestResponseInterceptor interceptor = new TestResponseInterceptor();
      messaging.setInterceptor(interceptor);
      try {
        messaging.sendAsync(Message.builder().setTopic("test-topic").build()).get();
        fail("No error thrown for HTTP error");
      } catch (ExecutionException e) {
        assertTrue(e.getCause() instanceof FirebaseMessagingException);
        assertTrue(e.getCause().getCause() instanceof HttpResponseException);
      }

      assertNotNull(interceptor.getResponse());
      HttpRequest request = interceptor.getResponse().getRequest();
      assertEquals("POST", request.getRequestMethod());
      assertEquals(TEST_FCM_URL, request.getUrl().toString());
      assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
      FirebaseApp.getInstance().delete();
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
    assertEquals("error_reason", result.getErrors().get(0).getReason());

    assertNotNull(interceptor.getResponse());
    HttpRequest request = interceptor.getResponse().getRequest();
    assertEquals("POST", request.getRequestMethod());
    assertEquals(TEST_IID_SUBSCRIBE_URL, request.getUrl().toString());
    assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
    assertEquals("true", request.getHeaders().get("access_token_auth"));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    request.getContent().writeTo(out);
    assertEquals("{\"to\":\"test-topic\",\"registration_tokens\":[\"id1\",\"id2\"]}",
        out.toString());
  }

  @Test
  public void testSubscribeError() throws Exception {
    for (int statusCode : HTTP_ERRORS) {
      MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
          .setStatusCode(statusCode)
          .setContent("test error");
      FirebaseMessaging messaging = initMessaging(response);
      TestResponseInterceptor interceptor = new TestResponseInterceptor();
      messaging.setInterceptor(interceptor);
      try {
        messaging.subscribeToTopicAsync(ImmutableList.of("id1", "id2"), "test-topic").get();
        fail("No error thrown for HTTP error");
      } catch (ExecutionException e) {
        assertTrue(e.getCause() instanceof FirebaseMessagingException);
        assertTrue(e.getCause().getCause() instanceof HttpResponseException);
      }

      assertNotNull(interceptor.getResponse());
      HttpRequest request = interceptor.getResponse().getRequest();
      assertEquals("POST", request.getRequestMethod());
      assertEquals(TEST_IID_SUBSCRIBE_URL, request.getUrl().toString());
      assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
      FirebaseApp.getInstance().delete();
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
    assertEquals("error_reason", result.getErrors().get(0).getReason());

    assertNotNull(interceptor.getResponse());
    HttpRequest request = interceptor.getResponse().getRequest();
    assertEquals("POST", request.getRequestMethod());
    assertEquals(TEST_IID_UNSUBSCRIBE_URL, request.getUrl().toString());
    assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
    assertEquals("true", request.getHeaders().get("access_token_auth"));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    request.getContent().writeTo(out);
    assertEquals("{\"to\":\"test-topic\",\"registration_tokens\":[\"id1\",\"id2\"]}",
        out.toString());
  }

  @Test
  public void testUnsubscribeError() throws Exception {
    for (int statusCode : HTTP_ERRORS) {
      MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
          .setStatusCode(statusCode)
          .setContent("test error");
      FirebaseMessaging messaging = initMessaging(response);
      TestResponseInterceptor interceptor = new TestResponseInterceptor();
      messaging.setInterceptor(interceptor);
      try {
        messaging.unsubscribeFromTopicAsync(ImmutableList.of("id1", "id2"), "test-topic").get();
        fail("No error thrown for HTTP error");
      } catch (ExecutionException e) {
        assertTrue(e.getCause() instanceof FirebaseMessagingException);
        assertTrue(e.getCause().getCause() instanceof HttpResponseException);
      }

      assertNotNull(interceptor.getResponse());
      HttpRequest request = interceptor.getResponse().getRequest();
      assertEquals("POST", request.getRequestMethod());
      assertEquals(TEST_IID_UNSUBSCRIBE_URL, request.getUrl().toString());
      assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
      FirebaseApp.getInstance().delete();
    }
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
}
