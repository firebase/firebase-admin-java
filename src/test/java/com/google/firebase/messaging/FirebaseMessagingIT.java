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
import static org.junit.Assert.assertTrue;

import com.google.api.client.http.HttpResponseException;
import com.google.common.collect.ImmutableList;
import com.google.firebase.ErrorCode;
import com.google.firebase.testing.IntegrationTestUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.BeforeClass;
import org.junit.Test;

public class FirebaseMessagingIT {

  private static final String TEST_REGISTRATION_TOKEN =
      "fGw0qy4TGgk:APA91bGtWGjuhp4WRhHXgbabIYp1jxEKI08ofj_v1bKhWAGJQ4e3arRCWzeTfHaLz83mBnDh0a"
          + "PWB1AykXAVUUGl2h1wT4XI6XazWpvY7RBUSYfoxtqSWGIm2nvWh2BOP1YG501SsRoE";
  private static final String TEST_IMAGE_URL = "https://example.com/image.png";

  @BeforeClass
  public static void setUpClass() {
    IntegrationTestUtils.ensureDefaultApp();
  }

  @Test
  public void testSend() throws Exception {
    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    Message message = Message.builder()
        .setNotification(Notification.builder()
            .setTitle("Title")
            .setBody("Body")
            .setImage(TEST_IMAGE_URL)
            .build())
        .setAndroidConfig(AndroidConfig.builder()
            .setRestrictedPackageName("com.google.firebase.testing")
            .build())
        .setApnsConfig(ApnsConfig.builder()
            .setAps(Aps.builder()
                .setAlert(ApsAlert.builder()
                    .setTitle("Title")
                    .setBody("Body")
                    .build())
                .build())
            .build())
        .setWebpushConfig(WebpushConfig.builder()
            .putHeader("X-Custom-Val", "Foo")
            .setNotification(new WebpushNotification("Title", "Body"))
            .setFcmOptions(WebpushFcmOptions.withLink("https://firebase.google.com"))
            .build())
        .setTopic("foo-bar")
        .build();
    String id = messaging.sendAsync(message, true).get();
    assertTrue(id != null && id.matches("^projects/.*/messages/.*$"));
  }

  @Test
  public void testSendError() throws InterruptedException {
    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    Message message = Message.builder()
        .setNotification(Notification.builder()
            .setTitle("Title")
            .setBody("Body")
            .build())
        .setToken("not-a-token")
        .build();
    try {
      messaging.sendAsync(message, true).get();
    } catch (ExecutionException e) {
      FirebaseMessagingException cause = (FirebaseMessagingException) e.getCause();
      assertEquals(ErrorCode.INVALID_ARGUMENT, cause.getErrorCode());
      assertEquals(MessagingErrorCode.INVALID_ARGUMENT, cause.getMessagingErrorCode());
      assertNotNull(cause.getHttpResponse());
      assertTrue(cause.getCause() instanceof HttpResponseException);
    }
  }

  @Test
  public void testSendAll() throws Exception {
    List<Message> messages = new ArrayList<>();
    messages.add(
        Message.builder()
          .setNotification(Notification.builder()
              .setTitle("Title")
              .setBody("Body")
              .build())
          .setTopic("foo-bar")
          .build());
    messages.add(
        Message.builder()
          .setNotification(Notification.builder()
              .setTitle("Title")
              .setBody("Body")
              .build())
          .setTopic("foo-bar")
          .build());
    messages.add(
        Message.builder()
          .setNotification(Notification.builder()
              .setTitle("Title")
              .setBody("Body")
              .build())
          .setToken("not-a-token")
          .build());

    BatchResponse response = FirebaseMessaging.getInstance().sendAll(messages, true);

    assertEquals(2, response.getSuccessCount());
    assertEquals(1, response.getFailureCount());

    List<SendResponse> responses = response.getResponses();
    assertEquals(3, responses.size());
    assertTrue(responses.get(0).isSuccessful());
    String id = responses.get(0).getMessageId();
    assertTrue(id != null && id.matches("^projects/.*/messages/.*$"));

    assertTrue(responses.get(1).isSuccessful());
    id = responses.get(1).getMessageId();
    assertTrue(id != null && id.matches("^projects/.*/messages/.*$"));

    assertFalse(responses.get(2).isSuccessful());
    assertNull(responses.get(2).getMessageId());
    FirebaseMessagingException exception = responses.get(2).getException();
    assertNotNull(exception);
    assertEquals(ErrorCode.INVALID_ARGUMENT, exception.getErrorCode());
  }

  @Test
  public void testSendFiveHundred() throws Exception {
    List<Message> messages = new ArrayList<>();
    for (int i = 0; i < 500; i++) {
      messages.add(Message.builder().setTopic("foo-bar-" + (i % 10)).build());
    }

    BatchResponse response = FirebaseMessaging.getInstance().sendAll(messages, true);

    assertEquals(500, response.getResponses().size());
    assertEquals(500, response.getSuccessCount());
    assertEquals(0, response.getFailureCount());
    for (SendResponse sendResponse : response.getResponses()) {
      if (!sendResponse.isSuccessful()) {
        sendResponse.getException().printStackTrace();
      }
      assertTrue(sendResponse.isSuccessful());
      String id = sendResponse.getMessageId();
      assertTrue(id != null && id.matches("^projects/.*/messages/.*$"));
      assertNull(sendResponse.getException());
    }
  }

  @Test
  public void testSendMulticast() throws Exception {
    MulticastMessage multicastMessage = MulticastMessage.builder()
        .setNotification(Notification.builder()
            .setTitle("Title")
            .setBody("Body")
            .build())
        .addToken("not-a-token")
        .addToken("also-not-a-token")
        .build();

    BatchResponse response = FirebaseMessaging.getInstance().sendMulticast(
        multicastMessage, true);

    assertEquals(0, response.getSuccessCount());
    assertEquals(2, response.getFailureCount());
    assertEquals(2, response.getResponses().size());
    for (SendResponse sendResponse : response.getResponses()) {
      assertFalse(sendResponse.isSuccessful());
      assertNull(sendResponse.getMessageId());
      assertNotNull(sendResponse.getException());
    }
  }

  @Test
  public void testSubscribe() throws Exception {
    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    TopicManagementResponse results = messaging.subscribeToTopicAsync(
        ImmutableList.of(TEST_REGISTRATION_TOKEN), "mock-topic").get();
    assertEquals(1, results.getSuccessCount() + results.getFailureCount());
  }

  @Test
  public void testUnsubscribe() throws Exception {
    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    TopicManagementResponse results = messaging.unsubscribeFromTopicAsync(
        ImmutableList.of(TEST_REGISTRATION_TOKEN), "mock-topic").get();
    assertEquals(1, results.getSuccessCount() + results.getFailureCount());
  }
}
