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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonParser;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.messaging.AndroidConfig.Priority;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class MessageTest {

  private static final String TEST_IMAGE_URL = "https://example.com/image.png";
  private static final String TEST_IMAGE_URL_ANDROID = "https://example.com/android-image.png";
  private static final String TEST_IMAGE_URL_APNS = "https://example.com/apns-image.png";

  @Test(expected = IllegalArgumentException.class)
  public void testMessageWithoutTarget() {
    Message.builder().build();
  }

  @Test
  public void testEmptyMessage() throws IOException {
    assertJsonEquals(ImmutableMap.of("topic", "test-topic"),
        Message.builder().setTopic("test-topic").build());
    assertJsonEquals(ImmutableMap.of("condition", "'foo' in topics"),
        Message.builder().setCondition("'foo' in topics").build());
    assertJsonEquals(ImmutableMap.of("token", "test-token"),
        Message.builder().setToken("test-token").build());
  }

  @Test
  public void testDataMessage() throws IOException {
    Message message = Message.builder()
        .putData("k1", "v1")
        .putData("k2", "v2")
        .putAllData(ImmutableMap.of("k3", "v3", "k4", "v4"))
        .setTopic("test-topic")
        .build();
    Map<String, String> data = ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3", "k4", "v4");
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "data", data), message);
  }

  @Test
  public void testInvalidTopicNames() {
    List<String> invalidTopicNames = ImmutableList.of("/topics/", "/foo/bar", "foo bar");
    for (String topicName : invalidTopicNames) {
      try {
        Message.builder().setTopic(topicName).build();
      } catch (IllegalArgumentException expected) {
        // expected
      }
    }
  }

  @Test
  public void testPrefixedTopicName() throws IOException {
    Message message = Message.builder()
        .setTopic("/topics/test-topic")
        .build();
    assertJsonEquals(ImmutableMap.of("topic", "test-topic"), message);
  }

  @Test
  public void testNotificationMessageDeprecatedConstructor() throws IOException {
    Message message = Message.builder()
        .setNotification(Notification.builder()
            .setTitle("title")
            .setBody("body")
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, String> data = ImmutableMap.of("title", "title", "body", "body");
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "notification", data), message);
  }

  @Test
  public void testNotificationMessage() throws IOException {
    Message message = Message.builder()
        .setNotification(Notification.builder().setTitle("title").setBody("body").build())
        .setTopic("test-topic")
        .build();
    Map<String, String> data = ImmutableMap.of("title", "title", "body", "body");
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "notification", data), message);
  }

  @Test
  public void testEmptyAndroidMessage() throws IOException {
    Message message = Message.builder()
        .setAndroidConfig(AndroidConfig.builder().build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> data = ImmutableMap.of();
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "android", data), message);
  }

  @Test
  public void testAndroidMessageWithoutNotification() throws IOException {
    Message message = Message.builder()
        .setAndroidConfig(AndroidConfig.builder()
            .setCollapseKey("test-key")
            .setPriority(Priority.HIGH)
            .setTtl(10)
            .setRestrictedPackageName("test-pkg-name")
            .putData("k1", "v1")
            .putAllData(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> data = ImmutableMap.<String, Object>of(
        "collapse_key", "test-key",
        "priority", "high",
        "ttl", "0.010000000s", // 10 ms = 10,000,000 ns
        "restricted_package_name", "test-pkg-name",
        "data", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3")
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "android", data), message);
  }

  @Test
  public void testAndroidMessageWithNotification() throws IOException {
    Message message = Message.builder()
        .setAndroidConfig(AndroidConfig.builder()
            .setCollapseKey("test-key")
            .setPriority(Priority.HIGH)
            .setTtl(TimeUnit.DAYS.toMillis(30))
            .setRestrictedPackageName("test-pkg-name")
            .setNotification(AndroidNotification.builder()
                .setTitle("android-title")
                .setBody("android-body")
                .setIcon("android-icon")
                .setSound("android-sound")
                .setColor("#112233")
                .setTag("android-tag")
                .setClickAction("android-click")
                .setTitleLocalizationKey("title-loc")
                .addTitleLocalizationArg("title-arg1")
                .addAllTitleLocalizationArgs(ImmutableList.of("title-arg2", "title-arg3"))
                .setBodyLocalizationKey("body-loc")
                .addBodyLocalizationArg("body-arg1")
                .addAllBodyLocalizationArgs(ImmutableList.of("body-arg2", "body-arg3"))
                .setChannelId("channel-id")
                .setNotificationCount(4)
                .build())
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> notification = ImmutableMap.<String, Object>builder()
        .put("title", "android-title")
        .put("body", "android-body")
        .put("icon", "android-icon")
        .put("sound", "android-sound")
        .put("color", "#112233")
        .put("tag", "android-tag")
        .put("click_action", "android-click")
        .put("title_loc_key", "title-loc")
        .put("title_loc_args", ImmutableList.of("title-arg1", "title-arg2", "title-arg3"))
        .put("body_loc_key", "body-loc")
        .put("body_loc_args", ImmutableList.of("body-arg1", "body-arg2", "body-arg3"))
        .put("channel_id", "channel-id")
        // There is a problem with the JsonParser assignment to BigDecimal takes priority over
        // all other number types and so this integer value is interpreted as a BigDecimal 
        // rather than an Integer.
        .put("notification_count", BigDecimal.valueOf(4L))
        .build();
    Map<String, Object> data = ImmutableMap.of(
        "collapse_key", "test-key",
        "priority", "high",
        "ttl", "2592000s",
        "restricted_package_name", "test-pkg-name",
        "notification", notification
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "android", data), message);
  }

  @Test
  public void testAndroidMessageWithDirectBootOk() throws IOException {
    Message message = Message.builder()
        .setAndroidConfig(AndroidConfig.builder()
            .setDirectBootOk(true)
            .setNotification(AndroidNotification.builder()
                .setTitle("android-title")
                .setBody("android-body")
                .build())
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> notification = ImmutableMap.<String, Object>builder()
        .put("title", "android-title")
        .put("body", "android-body")
        .build();
    Map<String, Object> data = ImmutableMap.of(
        "direct_boot_ok", true,
        "notification", notification
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "android", data), message);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testAndroidNotificationWithNegativeCount() throws IllegalArgumentException {
    AndroidNotification.builder().setNotificationCount(-1).build();
  }
  
  @Test
  public void testAndroidMessageWithoutLocalization() throws IOException {
    Message message = Message.builder()
        .setAndroidConfig(AndroidConfig.builder()
            .setCollapseKey("test-key")
            .setPriority(Priority.NORMAL)
            .setTtl(TimeUnit.SECONDS.toMillis(10))
            .setRestrictedPackageName("test-pkg-name")
            .setNotification(AndroidNotification.builder()
                .setTitle("android-title")
                .setBody("android-body")
                .setIcon("android-icon")
                .setSound("android-sound")
                .setColor("#112233")
                .setTag("android-tag")
                .setClickAction("android-click")
                .build())
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> notification = ImmutableMap.<String, Object>builder()
        .put("title", "android-title")
        .put("body", "android-body")
        .put("icon", "android-icon")
        .put("sound", "android-sound")
        .put("color", "#112233")
        .put("tag", "android-tag")
        .put("click_action", "android-click")
        .build();
    Map<String, Object> data = ImmutableMap.of(
        "collapse_key", "test-key",
        "priority", "normal",
        "ttl", "10s",
        "restricted_package_name", "test-pkg-name",
        "notification", notification
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "android", data), message);
  }

  @Test
  public void testInvalidAndroidConfig() {
    try {
      AndroidConfig.builder().setTtl(-1).build();
      fail("No error thrown for invalid ttl");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    List<AndroidNotification.Builder> notificationBuilders = ImmutableList.of(
        AndroidNotification.builder().setColor(""),
        AndroidNotification.builder().setColor("foo"),
        AndroidNotification.builder().setColor("123"),
        AndroidNotification.builder().setColor("#AABBCK"),
        AndroidNotification.builder().addBodyLocalizationArg("foo"),
        AndroidNotification.builder().addTitleLocalizationArg("foo")
    );
    for (int i = 0; i < notificationBuilders.size(); i++) {
      try {
        notificationBuilders.get(i).build();
        fail("No error thrown for invalid notification: " + i);
      } catch (IllegalArgumentException expected) {
        // expected
      }
    }
  }

  @Test
  public void testEmptyWebpushMessage() throws IOException {
    Message message = Message.builder()
        .setWebpushConfig(WebpushConfig.builder().build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> data = ImmutableMap.of();
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "webpush", data), message);
  }

  @Test
  public void testWebpushMessageWithoutNotification() throws IOException {
    Message message = Message.builder()
        .setWebpushConfig(WebpushConfig.builder()
            .putHeader("k1", "v1")
            .putAllHeaders(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .putData("k1", "v1")
            .putAllData(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> data = ImmutableMap.<String, Object>of(
        "headers", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
        "data", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3")
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "webpush", data), message);
  }

  @Test
  public void testWebpushMessageWithWebpushOptions() throws IOException {
    Message message = Message.builder()
        .setWebpushConfig(WebpushConfig.builder()
            .putHeader("k1", "v1")
            .putAllHeaders(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .putData("k1", "v1")
            .putAllData(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .setFcmOptions(WebpushFcmOptions.withLink("https://my-server/page"))
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> data = ImmutableMap.<String, Object>of(
        "headers", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
        "data", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
        "fcm_options", ImmutableMap.of("link", "https://my-server/page")
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "webpush", data), message);
  }

  @Test
  public void testWebpushMessageWithNotification() throws IOException {
    Message message = Message.builder()
        .setWebpushConfig(WebpushConfig.builder()
            .putHeader("k1", "v1")
            .putAllHeaders(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .putData("k1", "v1")
            .putAllData(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .setNotification(new WebpushNotification(
                "webpush-title", "webpush-body", "webpush-icon"))
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> notification = ImmutableMap.<String, Object>builder()
        .put("title", "webpush-title")
        .put("body", "webpush-body")
        .put("icon", "webpush-icon")
        .build();
    Map<String, Object> data = ImmutableMap.<String, Object>of(
        "headers", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
        "data", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
        "notification", notification
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "webpush", data), message);

    // Test notification without icon
    message = Message.builder()
        .setWebpushConfig(WebpushConfig.builder()
            .putHeader("k1", "v1")
            .putAllHeaders(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .putData("k1", "v1")
            .putAllData(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .setNotification(new WebpushNotification("webpush-title", "webpush-body"))
            .build())
        .setTopic("test-topic")
        .build();
    notification = ImmutableMap.<String, Object>builder()
        .put("title", "webpush-title")
        .put("body", "webpush-body")
        .build();
    data = ImmutableMap.<String, Object>of(
        "headers", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
        "data", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
        "notification", notification
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "webpush", data), message);
  }

  @Test
  public void testEmptyApnsMessage() throws IOException {
    Message message = Message.builder()
        .setApnsConfig(ApnsConfig.builder().setAps(Aps.builder().build()).build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> data = ImmutableMap.<String, Object>of("payload",
        ImmutableMap.of("aps", ImmutableMap.of()));
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "apns", data), message);
  }

  @Test
  public void testApnsMessageWithPayload() throws IOException {
    Message message = Message.builder()
        .setApnsConfig(ApnsConfig.builder()
            .putHeader("k1", "v1")
            .putAllHeaders(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .putCustomData("cd1", "cd-v1")
            .putAllCustomData(ImmutableMap.<String, Object>of("cd2", "cd-v2", "cd3", true))
            .setAps(Aps.builder().build())
            .build())
        .setTopic("test-topic")
        .build();

    Map<String, Object> payload = ImmutableMap.<String, Object>builder()
        .put("cd1", "cd-v1")
        .put("cd2", "cd-v2")
        .put("cd3", true)
        .put("aps", ImmutableMap.of())
        .build();
    Map<String, Object> data = ImmutableMap.<String, Object>of(
        "headers", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
        "payload", payload
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "apns", data), message);
  }

  @Test
  public void testApnsMessageWithPayloadAndAps() throws IOException {
    Message message = Message.builder()
        .setApnsConfig(ApnsConfig.builder()
            .putCustomData("cd1", "cd-v1")
            .setAps(Aps.builder()
                .setAlert("alert string")
                .setBadge(42)
                .setCategory("test-category")
                .setContentAvailable(true)
                .setMutableContent(true)
                .setSound("test-sound")
                .setThreadId("test-thread-id")
                .build())
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "cd1", "cd-v1",
        "aps", ImmutableMap.builder()
            .put("alert", "alert string")
            .put("badge", new BigDecimal(42))
            .put("category", "test-category")
            .put("content-available", new BigDecimal(1))
            .put("mutable-content", new BigDecimal(1))
            .put("sound", "test-sound")
            .put("thread-id", "test-thread-id")
            .build());
    assertJsonEquals(
        ImmutableMap.of(
            "topic", "test-topic",
            "apns", ImmutableMap.<String, Object>of("payload", payload)),
        message);

    message = Message.builder()
        .setApnsConfig(ApnsConfig.builder()
            .putCustomData("cd1", "cd-v1")
            .setAps(Aps.builder()
                .setAlert(ApsAlert.builder()
                    .setTitle("test-title")
                    .setSubtitle("test-subtitle")
                    .setBody("test-body")
                    .setLocalizationKey("test-loc-key")
                    .setActionLocalizationKey("test-action-loc-key")
                    .setTitleLocalizationKey("test-title-loc-key")
                    .setSubtitleLocalizationKey("test-subtitle-loc-key")
                    .addLocalizationArg("arg1")
                    .addAllLocalizationArgs(ImmutableList.of("arg2", "arg3"))
                    .addTitleLocalizationArg("arg4")
                    .addAllTitleLocArgs(ImmutableList.of("arg5", "arg6"))
                    .addSubtitleLocalizationArg("arg7")
                    .addAllSubtitleLocArgs(ImmutableList.of("arg8", "arg9"))
                    .setLaunchImage("test-image")
                    .build())
                .setCategory("test-category")
                .setSound("test-sound")
                .setThreadId("test-thread-id")
                .putCustomData("ck1", "cv1")
                .putAllCustomData(ImmutableMap.<String, Object>of("ck2", "cv2", "ck3", 1))
                .build())
            .build())
        .setTopic("test-topic")
        .build();
    payload = ImmutableMap.<String, Object>of(
        "cd1", "cd-v1",
        "aps", ImmutableMap.<String, Object>builder()
            .put("alert", ImmutableMap.<String, Object>builder()
                .put("title", "test-title")
                .put("subtitle", "test-subtitle")
                .put("body", "test-body")
                .put("loc-key", "test-loc-key")
                .put("action-loc-key", "test-action-loc-key")
                .put("title-loc-key", "test-title-loc-key")
                .put("subtitle-loc-key", "test-subtitle-loc-key")
                .put("loc-args", ImmutableList.of("arg1", "arg2", "arg3"))
                .put("title-loc-args", ImmutableList.of("arg4", "arg5", "arg6"))
                .put("subtitle-loc-args", ImmutableList.of("arg7", "arg8", "arg9"))
                .put("launch-image", "test-image")
                .build())
            .put("category", "test-category")
            .put("sound", "test-sound")
            .put("thread-id", "test-thread-id")
            .put("ck1", "cv1")
            .put("ck2", "cv2")
            .put("ck3", new BigDecimal(1))
            .build());
    assertJsonEquals(
        ImmutableMap.of(
            "topic", "test-topic",
            "apns", ImmutableMap.<String, Object>of("payload", payload)),
        message);
  }

  @Test
  public void testApnsMessageWithCriticalSound() throws IOException {
    Message message = Message.builder()
        .setApnsConfig(ApnsConfig.builder()
            .setAps(Aps.builder()
                .setSound(CriticalSound.builder() // All fields
                    .setCritical(true)
                    .setName("default")
                    .setVolume(0.5)
                    .build())
                .build())
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "aps", ImmutableMap.builder()
            .put("sound", ImmutableMap.of(
                "critical", new BigDecimal(1),
                "name", "default",
                "volume", new BigDecimal(0.5)))
            .build());
    assertJsonEquals(
        ImmutableMap.of(
            "topic", "test-topic",
            "apns", ImmutableMap.<String, Object>of("payload", payload)),
        message);

    message = Message.builder()
        .setApnsConfig(ApnsConfig.builder()
            .setAps(Aps.builder()
                .setSound(CriticalSound.builder() // Name field only
                    .setName("default")
                    .build())
                .build())
            .build())
        .setTopic("test-topic")
        .build();
    payload = ImmutableMap.<String, Object>of(
        "aps", ImmutableMap.builder()
            .put("sound", ImmutableMap.of("name", "default"))
            .build());
    assertJsonEquals(
        ImmutableMap.of(
            "topic", "test-topic",
            "apns", ImmutableMap.<String, Object>of("payload", payload)),
        message);
  }

  @Test
  public void testInvalidCriticalSound() {
    List<CriticalSound.Builder> soundBuilders = ImmutableList.of(
        CriticalSound.builder(),
        CriticalSound.builder().setCritical(true).setVolume(0.5),
        CriticalSound.builder().setVolume(-0.1),
        CriticalSound.builder().setVolume(1.1)
    );
    for (int i = 0; i < soundBuilders.size(); i++) {
      try {
        soundBuilders.get(i).build();
        fail("No error thrown for invalid sound: " + i);
      } catch (IllegalArgumentException expected) {
        // expected
      }
    }
  }

  @Test
  public void testInvalidApnsConfig() {
    List<ApnsConfig.Builder> configBuilders = ImmutableList.of(
        ApnsConfig.builder(),
        ApnsConfig.builder().putCustomData("aps", "foo"),
        ApnsConfig.builder().putCustomData("aps", "foo").setAps(Aps.builder().build())
    );
    for (int i = 0; i < configBuilders.size(); i++) {
      try {
        configBuilders.get(i).build();
        fail("No error thrown for invalid config: " + i);
      } catch (IllegalArgumentException expected) {
        // expected
      }
    }

    List<Aps.Builder> apsBuilders = ImmutableList.of(
        Aps.builder().setAlert("string").setAlert(ApsAlert.builder().build()),
        Aps.builder().setSound("default").setSound(CriticalSound.builder()
            .setName("default")
            .build()),
        Aps.builder().setMutableContent(true).putCustomData("mutable-content", 1)
    );
    for (int i = 0; i < apsBuilders.size(); i++) {
      try {
        apsBuilders.get(i).build();
        fail("No error thrown for invalid aps: " + i);
      } catch (IllegalArgumentException expected) {
        // expected
      }
    }

    List<ApsAlert.Builder> notificationBuilders = ImmutableList.of(
        ApsAlert.builder().addLocalizationArg("foo"),
        ApsAlert.builder().addTitleLocalizationArg("foo"),
        ApsAlert.builder().addSubtitleLocalizationArg("foo")
    );
    for (int i = 0; i < notificationBuilders.size(); i++) {
      try {
        notificationBuilders.get(i).build();
        fail("No error thrown for invalid alert: " + i);
      } catch (IllegalArgumentException expected) {
        // expected
      }
    }
  }

  @Test
  public void testWrapForTransportDryRun() {
    Message message = Message.builder()
        .setTopic("foo")
        .build();
    Map<String, Object> wrappedMessage = message.wrapForTransport(true);
    assertEquals(2, wrappedMessage.size());
    assertSame(message, wrappedMessage.get("message"));
    assertEquals(Boolean.TRUE, wrappedMessage.get("validate_only"));
  }

  @Test
  public void testWrapForTransport() {
    Message message = Message.builder()
        .setTopic("foo")
        .build();
    Map<String, Object> wrappedMessage = message.wrapForTransport(false);
    assertEquals(1, wrappedMessage.size());
    assertSame(message, wrappedMessage.get("message"));
  }

  @Test
  public void testMessageWithAllFcmOptions() throws IOException {
    Message messageUsingShorthand = Message.builder()
        .setTopic("foo")
        .setFcmOptions(FcmOptions.withAnalyticsLabel("message-label"))
        .setAndroidConfig(AndroidConfig.builder()
            .setFcmOptions(AndroidFcmOptions.withAnalyticsLabel("android-label")).build())
        .setApnsConfig(
            ApnsConfig.builder().setAps(Aps.builder().build())
                .setFcmOptions(ApnsFcmOptions.withAnalyticsLabel("apns-label"))
                .build()).build();
    Message messageUsingBuilder = Message.builder()
        .setTopic("foo")
        .setFcmOptions(FcmOptions.builder().setAnalyticsLabel("message-label").build())
        .setAndroidConfig(AndroidConfig.builder()
            .setFcmOptions(AndroidFcmOptions.builder().setAnalyticsLabel("android-label").build())
            .build())
        .setApnsConfig(
            ApnsConfig.builder().setAps(Aps.builder().build())
                .setFcmOptions(ApnsFcmOptions.builder().setAnalyticsLabel("apns-label").build())
                .build()).build();

    ImmutableMap<String, ImmutableMap<String, String>> androidConfig =
        ImmutableMap.of("fcm_options", ImmutableMap.of("analytics_label", "android-label"));
    ImmutableMap<String, Object> apnsConfig =
        ImmutableMap.<String, Object>builder()
            .put("fcm_options", ImmutableMap.of("analytics_label", "apns-label"))
            .put("payload", ImmutableMap.of("aps", ImmutableMap.of()))
            .build();
    ImmutableMap<String, Object> expected =
        ImmutableMap.<String, Object>builder()
            .put("topic", "foo")
            .put("fcm_options", ImmutableMap.of("analytics_label", "message-label"))
            .put("android", androidConfig)
            .put("apns", apnsConfig)
            .build();
    assertJsonEquals(expected, messageUsingBuilder);
    assertJsonEquals(expected, messageUsingShorthand);
  }

  @Test
  public void createMessageWithDefaultFcmOptions() throws IOException {
    Message message = Message.builder()
        .setTopic("foo")
        .setFcmOptions(FcmOptions.builder().build())
        .setAndroidConfig(
            AndroidConfig.builder().setFcmOptions(AndroidFcmOptions.builder().build()).build())
        .setApnsConfig(
            ApnsConfig.builder()
                .setAps(Aps.builder().build())
                .setFcmOptions(ApnsFcmOptions.builder().build())
                .build())
        .build();

    ImmutableMap<String, Object> apnsConfig =
        ImmutableMap.<String, Object>builder()
            .put("fcm_options", ImmutableMap.of())
            .put("payload", ImmutableMap.of("aps", ImmutableMap.of()))
            .build();
    ImmutableMap<String, Object> expected =
        ImmutableMap.<String, Object>builder()
            .put("topic", "foo")
            .put("fcm_options", ImmutableMap.of())
            .put("android", ImmutableMap.of("fcm_options", ImmutableMap.of()))
            .put("apns", apnsConfig)
            .build();
    assertJsonEquals(expected, message);
  }

  @Test
  public void testIncorrectAnalyticsLabelFormat() {
    try {
      FcmOptions.builder().setAnalyticsLabel("!").build();
      fail("No error thrown when using bad analytics label format.");
    } catch (IllegalArgumentException expected) {
      //expected
    }

    try {
      FcmOptions.builder()
          .setAnalyticsLabel("THIS_IS_LONGER_THAN_50_CHARACTERS_WHICH_IS_NOT_ALLOWED")
          .build();
      fail("No error thrown when using bad analytics label format.");
    } catch (IllegalArgumentException expected) {
      //expected
    }

    try {
      FcmOptions.builder().setAnalyticsLabel("   ").build();
      fail("No error thrown when using bad analytics label format.");
    } catch (IllegalArgumentException expected) {
      //expected
    }
  }

  @Test
  public void testImageInNotificationDeprecatedConstructor() throws IOException {
    Message message = Message.builder()
        .setNotification(Notification.builder()
            .setTitle("title")
            .setBody("body")
            .setImage(TEST_IMAGE_URL)
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, String> data = ImmutableMap.of(
        "title", "title", "body", "body", "image", TEST_IMAGE_URL);
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "notification", data), message);
  }

  @Test
  public void testImageInNotification() throws IOException {
    Message message = Message.builder()
        .setNotification(Notification.builder()
            .setTitle("title")
            .setBody("body")
            .setImage(TEST_IMAGE_URL)
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, String> data = ImmutableMap.of(
        "title", "title", "body", "body", "image", TEST_IMAGE_URL);
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "notification", data), message);
  }

  @Test
  public void testImageInAndroidNotification() throws IOException {
    Message message = Message.builder()
        .setNotification(Notification.builder()
            .setTitle("title")
            .setBody("body")
            .setImage(TEST_IMAGE_URL)
            .build())
        .setAndroidConfig(AndroidConfig.builder()
            .setNotification(AndroidNotification.builder()
                .setTitle("android-title")
                .setBody("android-body")
                .setImage(TEST_IMAGE_URL_ANDROID)
                .build())
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> notification = ImmutableMap.<String, Object>builder()
        .put("title", "title")
        .put("body", "body")
        .put("image", TEST_IMAGE_URL)
        .build();
    Map<String, Object> androidConfig = ImmutableMap.<String, Object>builder()
        .put("notification", ImmutableMap.<String, Object>builder()
            .put("title", "android-title")
            .put("body", "android-body")
            .put("image", TEST_IMAGE_URL_ANDROID)
            .build())
        .build();
    assertJsonEquals(ImmutableMap.of(
        "topic", "test-topic", "notification", notification, "android", androidConfig), message);
  }

  @Test
  public void testImageInApnsNotification() throws IOException {
    Message message = Message.builder()
        .setTopic("test-topic")
        .setNotification(Notification.builder()
            .setTitle("title")
            .setBody("body")
            .setImage(TEST_IMAGE_URL)
            .build())
        .setApnsConfig(
            ApnsConfig.builder().setAps(Aps.builder().build())
                .setFcmOptions(ApnsFcmOptions.builder().setImage(TEST_IMAGE_URL_APNS).build())
                .build()).build();

    ImmutableMap<String, Object> notification =
        ImmutableMap.<String, Object>builder()
            .put("title", "title")
            .put("body", "body")
            .put("image", TEST_IMAGE_URL)
            .build();
    ImmutableMap<String, Object> apnsConfig =
        ImmutableMap.<String, Object>builder()
            .put("fcm_options", ImmutableMap.of("image", TEST_IMAGE_URL_APNS))
            .put("payload", ImmutableMap.of("aps", ImmutableMap.of()))
            .build();
    ImmutableMap<String, Object> expected =
        ImmutableMap.<String, Object>builder()
            .put("topic", "test-topic")
            .put("notification", notification)
            .put("apns", apnsConfig)
            .build();
    assertJsonEquals(expected, message);
  }

  @Test
  public void testInvalidColorInAndroidNotificationLightSettings() {
    try {
      LightSettings.Builder lightSettingsBuilder = LightSettings.builder()
                      .setColorFromString("#01020K")
                      .setLightOnDurationInMillis(1002L)
                      .setLightOffDurationInMillis(1003L);

      lightSettingsBuilder.build();
      fail("No error thrown for invalid notification");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testExtendedAndroidNotificationParameters() throws IOException {
    long[] vibrateTimings = {1000L, 1001L};
    Message message = Message.builder()
        .setNotification(Notification.builder()
            .setTitle("title")
            .setBody("body")
            .build())
        .setAndroidConfig(AndroidConfig.builder()
            .setNotification(AndroidNotification.builder()
                .setTitle("android-title")
                .setBody("android-body")
                .setTicker("ticker")
                .setSticky(true)
                .setEventTimeInMillis(1546304523123L)
                .setLocalOnly(true)
                .setPriority(AndroidNotification.Priority.HIGH)
                .setVibrateTimingsInMillis(vibrateTimings)
                .setDefaultVibrateTimings(false)
                .setDefaultSound(false)
                .setLightSettings(LightSettings.builder()
                    .setColorFromString("#336699")
                    .setLightOnDurationInMillis(1002L)
                    .setLightOffDurationInMillis(1003L)
                    .build())
                .setDefaultLightSettings(false)
                .setVisibility(AndroidNotification.Visibility.PUBLIC)
                .setNotificationCount(10)
                .build())
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> notification = ImmutableMap.<String, Object>builder()
        .put("title", "title")
        .put("body", "body")
        .build();
    Map<String, Object> androidConfig = ImmutableMap.<String, Object>builder()
        .put("notification", ImmutableMap.<String, Object>builder()
            .put("title", "android-title")
            .put("body", "android-body")
            .put("ticker", "ticker")
            .put("sticky", true)
            .put("event_time", "2019-01-01T01:02:03.123000000Z")
            .put("local_only", true)
            .put("notification_priority", "PRIORITY_HIGH")
            .put("vibrate_timings", ImmutableList.of("1s", "1.001000000s"))
            .put("default_vibrate_timings", false)
            .put("default_sound", false)
            .put("light_settings", ImmutableMap.<String, Object>builder()
                .put("color", ImmutableMap.<String, Object>builder()
                    .put("red", new BigDecimal(new BigInteger("2"), 1))
                    .put("green", new BigDecimal(new BigInteger("4"), 1))
                    .put("blue", new BigDecimal(new BigInteger("6"), 1))
                    .put("alpha", new BigDecimal(new BigInteger("10"), 1))
                    .build())
                .put("light_on_duration", "1.002000000s")
                .put("light_off_duration", "1.003000000s")
                .build())
            .put("default_light_settings", false)
            .put("visibility", "public")
            .put("notification_count", new BigDecimal(10))
            .build())
        .build();
    assertJsonEquals(ImmutableMap.of(
        "topic", "test-topic", "notification", notification, "android", androidConfig), message);
  }

  private static void assertJsonEquals(
      Map expected, Object actual) throws IOException {
    assertEquals(expected, toMap(actual));
  }

  private static Map<String, Object> toMap(Object object) throws IOException {
    JsonFactory jsonFactory = Utils.getDefaultJsonFactory();
    String json = jsonFactory.toString(object);
    JsonParser parser = jsonFactory.createJsonParser(json);
    Map<String, Object> map = new HashMap<>();
    parser.parse(map);
    return map;
  }
}
