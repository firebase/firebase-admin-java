/*
 * Copyright 2018 Google LLC
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
import static org.junit.Assert.fail;

import com.google.api.client.http.HttpResponseException;
import com.google.common.collect.ImmutableList;
import com.google.firebase.ErrorCode;
import com.google.firebase.testing.IntegrationTestUtils;
import java.time.Duration;
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
            .setLiveActivityToken("integration-test-live-activity-token")
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
  public void testSendAndroidV2RemoteNotification() throws Exception {
    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    String id = messaging.sendAsync(androidV2RemoteNotificationMessage(), true).get();
    assertTrue(id != null && id.matches("^projects/.*/messages/.*$"));
  }

  @Test
  public void testSendAndroidV2MinimalRemoteNotification() throws Exception {
    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    String id = messaging.sendAsync(androidV2MinimalRemoteNotificationMessage(), true).get();
    assertTrue(id != null && id.matches("^projects/.*/messages/.*$"));
  }

  @Test
  public void testSendAndroidV2BackgroundSync() throws Exception {
    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    String id = messaging.sendAsync(androidV2BackgroundSyncMessage(), true).get();
    assertTrue(id != null && id.matches("^projects/.*/messages/.*$"));
  }

  @Test
  public void testSendAndroidV2MinimalBackgroundSync() throws Exception {
    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    String id = messaging.sendAsync(androidV2MinimalBackgroundSyncMessage(), true).get();
    assertTrue(id != null && id.matches("^projects/.*/messages/.*$"));
  }

  @Test
  public void testSendEachAndroidV2() throws Exception {
    List<Message> messages = ImmutableList.of(
        androidV2RemoteNotificationMessage(),
        androidV2MinimalRemoteNotificationMessage(),
        androidV2BackgroundSyncMessage(),
        androidV2MinimalBackgroundSyncMessage());

    BatchResponse response = FirebaseMessaging.getInstance().sendEach(messages, true);

    assertEquals(4, response.getSuccessCount());
    assertEquals(0, response.getFailureCount());
    assertEquals(4, response.getResponses().size());
    for (SendResponse sendResponse : response.getResponses()) {
      assertTrue(sendResponse.isSuccessful());
      String id = sendResponse.getMessageId();
      assertTrue(id != null && id.matches("^projects/.*/messages/.*$"));
      assertNull(sendResponse.getException());
    }
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
  public void testSendFidError() throws InterruptedException {
    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    Message message = Message.builder()
        .setNotification(Notification.builder()
            .setTitle("Title")
            .setBody("Body")
            .build())
        .setFid("not-a-fid")
        .build();
    try {
      messaging.sendAsync(message, true).get();
      fail("No exception thrown for invalid FID");
    } catch (ExecutionException e) {
      FirebaseMessagingException cause = (FirebaseMessagingException) e.getCause();
      assertEquals(ErrorCode.NOT_FOUND, cause.getErrorCode());
      assertEquals(MessagingErrorCode.UNREGISTERED, cause.getMessagingErrorCode());
      assertNotNull(cause.getHttpResponse());
      assertTrue(cause.getCause() instanceof HttpResponseException);
    }
  }

  @Test
  public void testSendEach() throws Exception {
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

    BatchResponse response = FirebaseMessaging.getInstance().sendEach(messages, true);

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
  public void testSendFiveHundredWithSendEach() throws Exception {
    List<Message> messages = new ArrayList<>();
    for (int i = 0; i < 500; i++) {
      messages.add(Message.builder().setTopic("foo-bar-" + (i % 10)).build());
    }

    BatchResponse response = FirebaseMessaging.getInstance().sendEach(messages, true);

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
  public void testSendEachForMulticast() throws Exception {
    MulticastMessage multicastMessage = MulticastMessage.builder()
        .setNotification(Notification.builder()
            .setTitle("Title")
            .setBody("Body")
            .build())
        .addToken("not-a-token")
        .addToken("also-not-a-token")
        .build();

    BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(
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
  public void testSendEachForMulticastFidsError() throws Exception {
    MulticastMessage multicastMessage = MulticastMessage.builder()
        .setNotification(Notification.builder()
            .setTitle("Title")
            .setBody("Body")
            .build())
        .addFid("not-a-fid")
        .addFid("also-not-a-fid")
        .build();

    BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(
        multicastMessage, true);

    assertEquals(0, response.getSuccessCount());
    assertEquals(2, response.getFailureCount());
    assertEquals(2, response.getResponses().size());
    for (SendResponse sendResponse : response.getResponses()) {
      assertFalse(sendResponse.isSuccessful());
      assertNull(sendResponse.getMessageId());
      assertNotNull(sendResponse.getException());
      assertEquals(ErrorCode.NOT_FOUND,
          sendResponse.getException().getErrorCode());
      assertEquals(MessagingErrorCode.UNREGISTERED,
          sendResponse.getException().getMessagingErrorCode());
    }
  }

  @Test
  public void testSendEachForMulticastMixedError() throws Exception {
    MulticastMessage multicastMessage = MulticastMessage.builder()
        .setNotification(Notification.builder()
            .setTitle("Title")
            .setBody("Body")
            .build())
        .addToken("not-a-token")
        .addFid("not-a-fid")
        .build();

    BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(
        multicastMessage, true);

    assertEquals(0, response.getSuccessCount());
    assertEquals(2, response.getFailureCount());
    assertEquals(2, response.getResponses().size());

    SendResponse response1 = response.getResponses().get(0);
    assertFalse(response1.isSuccessful());
    assertNull(response1.getMessageId());
    assertNotNull(response1.getException());
    assertEquals(ErrorCode.INVALID_ARGUMENT, response1.getException().getErrorCode());
    assertEquals(
        MessagingErrorCode.INVALID_ARGUMENT,
        response1.getException().getMessagingErrorCode());

    SendResponse response2 = response.getResponses().get(1);
    assertFalse(response2.isSuccessful());
    assertNull(response2.getMessageId());
    assertNotNull(response2.getException());
    assertEquals(ErrorCode.NOT_FOUND, response2.getException().getErrorCode());
    assertEquals(
        MessagingErrorCode.UNREGISTERED,
        response2.getException().getMessagingErrorCode());
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

  private static AndroidNotificationV2 fullAndroidNotificationV2() {
    return AndroidNotificationV2.builder()
        .setTitle("test.title")
        .setBody("test.body")
        .setIcon("test.icon")
        .setColor("#AABBCC")
        .setSound("test.sound")
        .setTag("test.tag")
        .setClickAction("test.click.action")
        .setBodyLocalizationKey("test.body.loc.key")
        .addAllBodyLocalizationArgs(ImmutableList.of("body.arg1", "body.arg2"))
        .setTitleLocalizationKey("test.title.loc.key")
        .addAllTitleLocalizationArgs(ImmutableList.of("title.arg1", "title.arg2"))
        .setChannelId("test.channel.id")
        .setImage(TEST_IMAGE_URL)
        .setTicker("test.ticker")
        .setSticky(true)
        .setEventTimeInMillis(System.currentTimeMillis())
        .setLocalOnly(true)
        .setNotificationPriority(AndroidNotificationV2.NotificationPriority.HIGH)
        .setVibrateTimingsInMillis(new long[]{100L, 50L, 250L})
        .setDefaultVibrateTimings(false)
        .setDefaultSound(true)
        .setLightSettings(LightSettings.builder()
            .setColorFromString("#AABBCC")
            .setLightOnDurationInMillis(200)
            .setLightOffDurationInMillis(300)
            .build())
        .setDefaultLightSettings(false)
        .setVisibility(AndroidNotificationV2.Visibility.PRIVATE)
        .setNotificationCount(1)
        .setId(42)
        .build();
  }

  private static AndroidConfigV2.Builder fullAndroidConfigV2Base() {
    return AndroidConfigV2.builder()
        .setCollapseKey("test-key")
        .setTtl(Duration.ofSeconds(5))
        .setRestrictedPackageName("com.google.firebase.testing")
        .putData("androidFoo", "androidBar")
        .setFcmOptions(AndroidFcmOptions.withAnalyticsLabel("test-analytics"))
        .setDirectBootOk(true)
        .setBandwidthConstrainedOk(true)
        .setRestrictedSatelliteOk(true);
  }

  private static Message androidV2RemoteNotificationMessage() {
    AndroidConfigV2 config = fullAndroidConfigV2Base()
        .setRemoteNotification(AndroidRemoteNotification.builder()
            .setMutableContent(true)
            .setNotification(fullAndroidNotificationV2())
            .setUseAsV1DataMessage(true)
            .build())
        .build();
    return Message.builder()
        .setNotification(Notification.builder()
            .setTitle("Title")
            .setBody("Body")
            .build())
        .setAndroidConfigV2(config)
        .setTopic("foo-bar")
        .build();
  }

  private static Message androidV2MinimalRemoteNotificationMessage() {
    AndroidConfigV2 config = AndroidConfigV2.builder()
        .setRemoteNotification(AndroidRemoteNotification.builder()
            .setNotification(AndroidNotificationV2.builder()
                .setTitle("test.title")
                .setBody("test.body")
                .build())
            .build())
        .build();
    return Message.builder()
        .setAndroidConfigV2(config)
        .setTopic("foo-bar")
        .build();
  }

  private static Message androidV2BackgroundSyncMessage() {
    AndroidConfigV2 config = fullAndroidConfigV2Base()
        .setBackgroundSync(AndroidBackgroundSyncMessage.builder().build())
        .build();
    return Message.builder()
        .setAndroidConfigV2(config)
        .setTopic("foo-bar")
        .build();
  }

  private static Message androidV2MinimalBackgroundSyncMessage() {
    AndroidConfigV2 config = AndroidConfigV2.builder()
        .setBackgroundSync(AndroidBackgroundSyncMessage.builder().build())
        .build();
    return Message.builder()
        .setAndroidConfigV2(config)
        .setTopic("foo-bar")
        .build();
  }
}
