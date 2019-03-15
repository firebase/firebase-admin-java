package com.google.firebase.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonParser;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.testing.GenericFunction;
import com.google.firebase.testing.TestResponseInterceptor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Test;

public class InstanceIdClientTest {

  private static final String TEST_IID_SUBSCRIBE_URL =
      "https://iid.googleapis.com/iid/v1:batchAdd";

  private static final String TEST_IID_UNSUBSCRIBE_URL =
      "https://iid.googleapis.com/iid/v1:batchRemove";

  private static final List<Integer> HTTP_ERRORS = ImmutableList.of(401, 404, 500);

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
  public void testInvalidSubscribe() {
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initMessaging(new MockLowLevelHttpResponse(), interceptor);

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
    FirebaseMessaging messaging = initMessaging(new MockLowLevelHttpResponse(), interceptor);

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

  @Test(expected = IllegalArgumentException.class)
  public void testTopicManagementResponseWithNullList() {
    new TopicManagementResponse(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testTopicManagementResponseWithEmptyList() {
    new TopicManagementResponse(ImmutableList.<GenericJson>of());
  }

  private static String getTopicManagementErrorCode(int statusCode) {
    String code = InstanceIdClient.IID_ERROR_CODES.get(statusCode);
    if (code == null) {
      code = "unknown-error";
    }
    return code;
  }

  private static FirebaseMessaging initMessaging(
      final MockLowLevelHttpResponse mockResponse,
      final HttpResponseInterceptor interceptor) {

    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(mockResponse)
        .build();
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .setHttpTransport(transport)
        .build();
    final FirebaseApp app = FirebaseApp.initializeApp(options);

    return FirebaseMessaging.builder()
        .setFirebaseApp(app)
        .setMessagingClient(Suppliers.<FirebaseMessagingClient>ofInstance(null))
        .setInstanceIdClient(new Supplier<InstanceIdClient>() {
          @Override
          public InstanceIdClient get() {
            return new InstanceIdClient(app, interceptor);
          }
        })
        .build();
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

  private static FirebaseMessaging initFaultyTransportMessaging() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .setHttpTransport(new FailingHttpTransport())
        .build();
    final FirebaseApp app = FirebaseApp.initializeApp(options);
    return FirebaseMessaging.builder()
        .setFirebaseApp(app)
        .setMessagingClient(Suppliers.<FirebaseMessagingClient>ofInstance(null))
        .setInstanceIdClient(new Supplier<InstanceIdClient>() {
          @Override
          public InstanceIdClient get() {
            return new InstanceIdClient(app, null);
          }
        })
        .build();
  }

  private static class FailingHttpTransport extends HttpTransport {
    @Override
    protected LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
      throw new IOException("transport error");
    }
  }
}
