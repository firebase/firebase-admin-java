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
    final InstanceIdClient messaging = initMessaging(response, interceptor);
    TopicManagementResponse result = messaging.subscribeToTopic(
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
    final InstanceIdClient messaging = initMessaging(response, interceptor);
    TopicManagementResponse result = messaging.subscribeToTopic(
        "/topics/test-topic", ImmutableList.of("id1", "id2"));
    checkTopicManagementRequestHeader(
        interceptor.getLastRequest(), TEST_IID_SUBSCRIBE_URL);
    checkTopicManagementRequest(interceptor.getLastRequest(), result);
  }

  @Test
  public void testSubscribeError() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    InstanceIdClient messaging = initMessaging(response, interceptor);
    for (int statusCode : HTTP_ERRORS) {
      response.setStatusCode(statusCode).setContent("{\"error\": \"test error\"}");
      try {
        messaging.subscribeToTopic("test-topic", ImmutableList.of("id1", "id2"));
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        assertEquals(getTopicManagementErrorCode(statusCode), error.getErrorCode());
        assertEquals("test error", error.getMessage());
        assertTrue(error.getCause() instanceof HttpResponseException);
      }

      checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_SUBSCRIBE_URL);
    }
  }

  @Test
  public void testSubscribeUnknownError() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    InstanceIdClient messaging = initMessaging(response, interceptor);
    response.setStatusCode(500).setContent("{}");
    try {
      messaging.subscribeToTopic("test-topic", ImmutableList.of("id1", "id2"));
      fail("No error thrown for HTTP error");
    } catch (FirebaseMessagingException error) {
      assertEquals(getTopicManagementErrorCode(500), error.getErrorCode());
      assertEquals("Unexpected HTTP response with status: 500; body: {}", error.getMessage());
      assertTrue(error.getCause() instanceof HttpResponseException);
    }

    checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_SUBSCRIBE_URL);
  }

  @Test
  public void testSubscribeTransportError() {
    InstanceIdClient messaging = initFaultyTransportMessaging();
    try {
      messaging.subscribeToTopic("test-topic", ImmutableList.of("id1", "id2"));
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
    final InstanceIdClient messaging = initMessaging(response, interceptor);

    TopicManagementResponse result = messaging.unsubscribeFromTopic(
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
    final InstanceIdClient messaging = initMessaging(response, interceptor);

    TopicManagementResponse result = messaging.unsubscribeFromTopic(
        "/topics/test-topic", ImmutableList.of("id1", "id2"));
    checkTopicManagementRequestHeader(
        interceptor.getLastRequest(), TEST_IID_UNSUBSCRIBE_URL);
    checkTopicManagementRequest(interceptor.getLastRequest(), result);
  }

  @Test
  public void testUnsubscribeError() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    InstanceIdClient messaging = initMessaging(response, interceptor);
    for (int statusCode : HTTP_ERRORS) {
      response.setStatusCode(statusCode).setContent("{\"error\": \"test error\"}");
      try {
        messaging.unsubscribeFromTopic("test-topic", ImmutableList.of("id1", "id2"));
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException error) {
        assertEquals(getTopicManagementErrorCode(statusCode), error.getErrorCode());
        assertEquals("test error", error.getMessage());
        assertTrue(error.getCause() instanceof HttpResponseException);
      }

      checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_UNSUBSCRIBE_URL);
    }
  }

  @Test
  public void testUnsubscribeUnknownError() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    InstanceIdClient messaging = initMessaging(response, interceptor);
    response.setStatusCode(500).setContent("{}");
    try {
      messaging.unsubscribeFromTopic("test-topic", ImmutableList.of("id1", "id2"));
      fail("No error thrown for HTTP error");
    } catch (FirebaseMessagingException error) {
      assertEquals(getTopicManagementErrorCode(500), error.getErrorCode());
      assertEquals("Unexpected HTTP response with status: 500; body: {}", error.getMessage());
      assertTrue(error.getCause() instanceof HttpResponseException);
    }

    checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_UNSUBSCRIBE_URL);
  }

  @Test
  public void testUnsubscribeTransportError() {
    InstanceIdClient messaging = initFaultyTransportMessaging();
    try {
      messaging.unsubscribeFromTopic("test-topic", ImmutableList.of("id1", "id2"));
      fail("No error thrown for HTTP error");
    } catch (FirebaseMessagingException error) {
      assertEquals("internal-error", error.getErrorCode());
      assertEquals("Error while calling IID backend service", error.getMessage());
      assertTrue(error.getCause() instanceof IOException);
    }
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

  private static String getTopicManagementErrorCode(int statusCode) {
    String code = InstanceIdClientImpl.IID_ERROR_CODES.get(statusCode);
    if (code == null) {
      code = "unknown-error";
    }
    return code;
  }

  private static InstanceIdClientImpl initMessaging(
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

  private static InstanceIdClient initFaultyTransportMessaging() {
    return new InstanceIdClientImpl(
        TestUtils.faultyHttpTransport().createRequestFactory(),
        Utils.getDefaultJsonFactory());
  }
}
